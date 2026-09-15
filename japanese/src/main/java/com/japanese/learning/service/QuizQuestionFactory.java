package com.japanese.learning.service;

import tools.jackson.databind.ObjectMapper;
import com.japanese.account.entity.UserAccount;
import com.japanese.content.entity.*;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.content.repository.GrammarConfirmationQuestionRepository;
import com.japanese.learning.entity.QuizQuestionType;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuizQuestionFactory {
    private static final int CANDIDATE_LIMIT = 500;
    private static final List<QuizQuestionType> TYPE_ORDER = List.of(
            QuizQuestionType.WORD_JAPANESE_TO_MEANING,
            QuizQuestionType.WORD_MEANING_TO_JAPANESE,
            QuizQuestionType.WORD_READING_CHOICE,
            QuizQuestionType.WORD_READING_INPUT,
            QuizQuestionType.GRAMMAR_PATTERN_TO_MEANING,
            QuizQuestionType.GRAMMAR_MEANING_TO_PATTERN,
            QuizQuestionType.GRAMMAR_CONTEXT_CHOICE);

    private final ContentItemRepository contents;
    private final GrammarConfirmationQuestionRepository confirmations;
    private final LearningService learning;
    private final QuizAnswerEvaluator evaluator;
    private final ObjectMapper objectMapper;

    public QuizQuestionFactory(ContentItemRepository contents,
            GrammarConfirmationQuestionRepository confirmations, LearningService learning,
            QuizAnswerEvaluator evaluator, ObjectMapper objectMapper) {
        this.contents = contents;
        this.confirmations = confirmations;
        this.learning = learning;
        this.evaluator = evaluator;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<QuestionSpec> create(UserAccount account, String seed, int requestedCount) {
        var scope = learning.learningScope(account);
        List<Long> candidateIds = contents.findPublishedQuizCandidateIds(
                !scope.allLevels(), queryValues(scope.levelCodes()),
                !scope.allCategories(), queryValues(scope.categorySlugs()),
                PageRequest.of(0, CANDIDATE_LIMIT));
        if (candidateIds.isEmpty()) return List.of();
        Map<Long, ContentItem> hydrated = contents.findPublishedForSummaryByIdIn(candidateIds).stream()
                .collect(java.util.stream.Collectors.toMap(ContentItem::getId, item -> item));
        // The hydrate query uses an IN clause, so restore the deterministic id
        // order returned by the candidate query before building choice pools.
        List<ContentItem> candidates = candidateIds.stream()
                .map(hydrated::get)
                .filter(Objects::nonNull)
                .toList();
        List<String> meanings = distinct(candidates.stream().filter(item -> item.getWord() != null)
                .map(this::primaryMeaning).filter(Objects::nonNull).toList(), false);
        List<String> expressions = distinct(candidates.stream().filter(item -> item.getWord() != null)
                .map(item -> item.getWord().getExpression()).toList(), false);
        List<String> readings = distinct(candidates.stream().filter(item -> item.getWord() != null)
                .map(item -> item.getWord().getReading()).filter(this::hasText).toList(), true);
        List<String> patterns = distinct(candidates.stream().filter(item -> item.getGrammar() != null)
                .map(item -> item.getGrammar().getPattern()).toList(), false);
        List<String> grammarMeanings = distinct(candidates.stream().filter(item -> item.getGrammar() != null)
                .map(item -> item.getGrammar().getExplanation()).filter(this::hasText).toList(), false);

        Map<QuizQuestionType, List<QuestionSpec>> byType = new EnumMap<>(QuizQuestionType.class);
        TYPE_ORDER.forEach(type -> byType.put(type, new ArrayList<>()));
        for (ContentItem item : candidates) {
            if (item.getWord() != null) addWord(byType, item, seed, meanings, expressions, readings);
            if (item.getGrammar() != null) addGrammar(byType, item, seed, patterns, grammarMeanings);
        }
        addConfirmations(byType, candidates, seed);
        byType.values().forEach(list -> list.sort(stable(seed)));

        List<QuestionSpec> result = new ArrayList<>();
        int round = 0;
        int limit = Math.min(Math.max(requestedCount, 1), 20);
        while (result.size() < limit) {
            boolean added = false;
            for (QuizQuestionType type : TYPE_ORDER) {
                List<QuestionSpec> values = byType.get(type);
                if (round < values.size() && result.size() < limit) {
                    result.add(values.get(round));
                    added = true;
                }
            }
            if (!added) break;
            round++;
        }
        return result;
    }

    private void addWord(Map<QuizQuestionType, List<QuestionSpec>> target, ContentItem item, String seed,
            List<String> meanings, List<String> expressions, List<String> readings) {
        Word word = item.getWord();
        String meaning = primaryMeaning(item);
        String explanation = word.getExpression() + " · " + word.getReading() + (meaning == null ? "" : " · " + meaning);
        addChoice(target, item, QuizQuestionType.WORD_JAPANESE_TO_MEANING, "일본어에 맞는 뜻을 고르세요.",
                word.getExpression(), meaning, meanings, explanation, seed);
        addChoice(target, item, QuizQuestionType.WORD_MEANING_TO_JAPANESE, "뜻에 맞는 일본어를 고르세요.",
                meaning, word.getExpression(), expressions, explanation, seed);
        addChoice(target, item, QuizQuestionType.WORD_READING_CHOICE, "올바른 읽기를 고르세요.",
                word.getExpression(), word.getReading(), readings, explanation, seed);
        if (hasText(word.getReading())) {
            target.get(QuizQuestionType.WORD_READING_INPUT).add(new QuestionSpec(item,
                    QuizQuestionType.WORD_READING_INPUT, "읽기를 히라가나 또는 가타카나로 입력하세요.",
                    word.getExpression(), List.of(), word.getReading(), explanation));
        }
    }

    private void addGrammar(Map<QuizQuestionType, List<QuestionSpec>> target, ContentItem item, String seed,
            List<String> patterns, List<String> meanings) {
        Grammar grammar = item.getGrammar();
        String explanation = grammar.getExplanation();
        addChoice(target, item, QuizQuestionType.GRAMMAR_PATTERN_TO_MEANING, "문법 패턴에 맞는 설명을 고르세요.",
                grammar.getPattern(), explanation, meanings, explanation, seed);
        addChoice(target, item, QuizQuestionType.GRAMMAR_MEANING_TO_PATTERN, "설명에 맞는 문법 패턴을 고르세요.",
                explanation, grammar.getPattern(), patterns, explanation, seed);
    }

    private void addConfirmations(Map<QuizQuestionType, List<QuestionSpec>> target,
            List<ContentItem> candidates, String seed) {
        List<Long> grammarIds = candidates.stream().filter(item -> item.getGrammar() != null)
                .map(ContentItem::getId).toList();
        if (grammarIds.isEmpty()) return;
        for (GrammarConfirmationQuestion question : confirmations.findApprovedForQuiz(grammarIds, ReviewStatus.APPROVED)) {
            if (!question.isPubliclyVisible()) continue;
            List<String> choices = distinct(question.getChoices().stream()
                    .map(GrammarConfirmationChoice::getChoiceText).toList(), false);
            String answer = question.correctChoice().getChoiceText();
            if (choices.size() < 2 || choices.stream().noneMatch(value -> evaluator.normalizeText(value).equals(evaluator.normalizeText(answer)))) continue;
            List<String> ordered = stableChoices(seed, "confirmation-" + question.getId(), answer, choices);
            String prompt = hasText(question.getContext()) ? question.getContext() + "\n" + question.getPrompt() : question.getPrompt();
            target.get(QuizQuestionType.GRAMMAR_CONTEXT_CHOICE).add(new QuestionSpec(
                    question.getGrammar().getContentItem(), QuizQuestionType.GRAMMAR_CONTEXT_CHOICE,
                    "문맥에 맞는 답을 고르세요.", prompt, ordered, answer, question.getExplanation()));
        }
    }

    private void addChoice(Map<QuizQuestionType, List<QuestionSpec>> target, ContentItem item,
            QuizQuestionType type, String instruction, String prompt, String answer,
            List<String> pool, String explanation, String seed) {
        if (!hasText(prompt) || !hasText(answer)) return;
        List<String> choices = stableChoices(seed, item.getId() + "-" + type, answer, pool);
        if (choices.size() < 2) return;
        target.get(type).add(new QuestionSpec(item, type, instruction, prompt, choices, answer, explanation));
    }

    private List<String> stableChoices(String seed, String key, String answer, List<String> pool) {
        String normalizedAnswer = evaluator.normalizeText(answer);
        List<String> distractors = pool.stream()
                .filter(this::hasText)
                .filter(value -> !evaluator.normalizeText(value).equals(normalizedAnswer))
                .sorted(Comparator.comparingInt(value -> stableHash(seed + "|" + key + "|" + value)))
                .limit(3).toList();
        List<String> choices = new ArrayList<>(distractors);
        choices.add(answer);
        choices.sort(Comparator.comparingInt(value -> stableHash(seed + "|position|" + key + "|" + value)));
        return choices;
    }

    private List<String> distinct(List<String> values, boolean reading) {
        Map<String, String> unique = new LinkedHashMap<>();
        for (String value : values) {
            String key = reading ? evaluator.normalizeReading(value) : evaluator.normalizeText(value);
            if (!key.isBlank()) unique.putIfAbsent(key, value.trim());
        }
        return List.copyOf(unique.values());
    }

    private Comparator<QuestionSpec> stable(String seed) {
        return Comparator.comparingInt(spec -> stableHash(seed + "|" + spec.content().getId() + "|" + spec.type()));
    }

    private int stableHash(String value) { return value.hashCode() & Integer.MAX_VALUE; }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private String primaryMeaning(ContentItem item) {
        return item.getWord().getMeanings().stream().filter(meaning -> "ko".equalsIgnoreCase(meaning.getLanguageTag()))
                .map(Meaning::getText).filter(this::hasText).findFirst().orElse(null);
    }
    private List<String> queryValues(List<String> values) {
        return values == null || values.isEmpty() ? List.of("__NO_SCOPE_VALUE__") : values;
    }

    public String choicesJson(List<String> choices) {
        try { return objectMapper.writeValueAsString(choices); }
        catch (Exception exception) { throw new IllegalStateException("퀴즈 선택지를 저장할 수 없습니다.", exception); }
    }

    public record QuestionSpec(ContentItem content, QuizQuestionType type, String instruction,
            String prompt, List<String> choices, String correctAnswer, String explanation) { }
}
