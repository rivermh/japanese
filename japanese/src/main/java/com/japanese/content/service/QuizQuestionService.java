package com.japanese.content.service;

import com.japanese.content.dto.QuizAnswerResult;
import com.japanese.content.dto.QuizQuestionDetails;
import com.japanese.content.dto.QuizQuestionPage;
import com.japanese.content.entity.ImportedSourceRecord;
import com.japanese.content.repository.ImportedSourceRecordRepository;
import com.japanese.learning.dto.StudyOverview;
import com.japanese.learning.service.LearningService;
import com.japanese.account.entity.UserAccount;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuizQuestionService {

    private static final String QUESTION_NOTE_TYPE = "JLPT MAX덱 어휘문제";
    private static final int MAX_PAGE_SIZE = 30;
    private static final Pattern BLANK = Pattern.compile("(?:（|\\()\\s*[　 ]*(?:）|\\))");

    private final ImportedSourceRecordRepository repository;
    private final LearningService learningService;

    public QuizQuestionService(ImportedSourceRecordRepository repository, LearningService learningService) {
        this.repository = repository;
        this.learningService = learningService;
    }

    @Transactional(readOnly = true)
    public QuizQuestionPage findPage(String level, int page, int size) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<ImportedSourceRecord> result = level == null || level.isBlank()
                ? repository.findByNoteTypeOrderBySourceNoteId(
                        QUESTION_NOTE_TYPE, PageRequest.of(normalizedPage, normalizedSize))
                : repository.findByNoteTypeAndLevelCodeOrderBySourceNoteId(
                        QUESTION_NOTE_TYPE, level.trim(), PageRequest.of(normalizedPage, normalizedSize));
        List<QuizQuestionDetails> questions = result.getContent().stream()
                .map(this::toDetails)
                .toList();
        return new QuizQuestionPage(questions, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.hasNext(), result.hasPrevious());
    }

    @Transactional(readOnly = true)
    public Optional<QuizQuestionDetails> findById(Long id) {
        return repository.findByIdAndNoteType(id, QUESTION_NOTE_TYPE).map(this::toDetails);
    }

    @Transactional
    public Optional<QuizAnswerResult> answer(Long id, String submittedAnswer, UserAccount account) {
        return repository.findByIdAndNoteType(id, QUESTION_NOTE_TYPE).map(record -> {
            QuizQuestionDetails question = toDetails(record);
            String submitted = normalize(submittedAnswer);
            boolean correct = submitted.equals(normalize(question.answerJapanese()))
                    || submitted.equals(normalize(question.answerKorean()));
            StudyOverview overview = learningService.recordImportedQuizAnswer(account, record, correct);
            return new QuizAnswerResult(correct, question.answerJapanese(), question.answerKorean(),
                    question.explanation(), overview);
        });
    }

    private QuizQuestionDetails toDetails(ImportedSourceRecord record) {
        Map<String, String> fields = fields(record);
        String promptJapanese = plain(value(fields, "PromptJP"));
        String promptKorean = plain(value(fields, "PromptKO"));
        String answerFullJapanese = plain(value(fields, "AnswerRuby"));
        String answerFullKorean = plain(value(fields, "AnswerKORuby"));
        String answerJapanese = answerFromBlank(promptJapanese, answerFullJapanese);
        String answerKorean = answerFromBlank(promptKorean, answerFullKorean);
        return new QuizQuestionDetails(
                record.getId(),
                record.getSourceNoteId(),
                value(fields, "JLPT"),
                value(fields, "QuestionType"),
                value(fields, "QuestionLabel"),
                plain(value(fields, "Instruction")),
                promptJapanese,
                promptKorean,
                choices(value(fields, "ChoicesHTML")),
                answerJapanese,
                answerKorean,
                plain(value(fields, "ExplanationRubyHTML")));
    }

    private Map<String, String> fields(ImportedSourceRecord record) {
        String[] names = split(record.getFieldNames());
        String[] values = split(record.getFieldValues());
        Map<String, String> result = new LinkedHashMap<>();
        for (int index = 0; index < names.length && index < values.length; index++) {
            result.put(names[index], values[index]);
        }
        return result;
    }

    private String[] split(String value) {
        return value == null ? new String[0] : value.split("\\u001f", -1);
    }

    private String value(Map<String, String> fields, String name) {
        String value = fields.get(name);
        return value == null || value.isBlank() || "⁣".equals(value) ? "" : value.trim();
    }

    private List<String> choices(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        List<String> listItems = Jsoup.parseBodyFragment(html).select("li").stream()
                .map(Element::text)
                .map(String::trim)
                .map(value -> value.replaceFirst("^[①②③④⑤⑥⑦⑧⑨⑩]\\s*", ""))
                .filter(value -> !value.isBlank())
                .toList();
        return listItems.isEmpty() ? List.of(plain(html)) : listItems;
    }

    private String plain(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        var body = Jsoup.parseBodyFragment(html).body();
        body.select("rt").remove();
        return body.text().trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String answerFromBlank(String prompt, String answerFull) {
        if (prompt == null || answerFull == null || prompt.isBlank() || answerFull.isBlank()) {
            return "";
        }
        var matcher = BLANK.matcher(prompt);
        if (!matcher.find()) {
            return answerFull;
        }
        String prefix = prompt.substring(0, matcher.start());
        String suffix = prompt.substring(matcher.end());
        if (!answerFull.startsWith(prefix) || !answerFull.endsWith(suffix)
                || answerFull.length() < prefix.length() + suffix.length()) {
            return "";
        }
        return answerFull.substring(prefix.length(), answerFull.length() - suffix.length()).trim();
    }
}
