package com.japanese.learning.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.japanese.account.entity.UserAccount;
import com.japanese.content.entity.ContentItem;
import com.japanese.learning.dto.*;
import com.japanese.learning.entity.*;
import com.japanese.learning.repository.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuizSessionService {
    private final QuizSessionRepository sessions;
    private final QuizSessionItemRepository items;
    private final LearnerProfileRepository profiles;
    private final QuizQuestionFactory questions;
    private final QuizAnswerEvaluator evaluator;
    private final LearningService learning;
    private final ObjectMapper objectMapper;

    public QuizSessionService(QuizSessionRepository sessions, QuizSessionItemRepository items,
            LearnerProfileRepository profiles, QuizQuestionFactory questions, QuizAnswerEvaluator evaluator,
            LearningService learning, ObjectMapper objectMapper) {
        this.sessions = sessions;
        this.items = items;
        this.profiles = profiles;
        this.questions = questions;
        this.evaluator = evaluator;
        this.learning = learning;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public QuizSessionView startOrResume(UserAccount account, QuizMode mode, int count) {
        LearnerProfile profile = profile(account);
        var active = sessions.findFirstByLearnerProfileIdAndModeAndStateOrderByIdDesc(
                profile.getId(), mode, QuizSessionState.ACTIVE);
        if (active.isPresent()) return view(active.get());
        return create(account, profile, mode, count);
    }

    @Transactional
    public QuizSessionView restart(UserAccount account, String publicId) {
        LearnerProfile profile = profile(account);
        QuizSession previous = sessions.findOwned(profile.getId(), publicId)
                .orElseThrow(() -> new QuizSessionNotFoundException("Quiz session not found"));
        if (previous.getState() != QuizSessionState.COMPLETED) return view(previous);
        return create(account, profile, previous.getMode(), previous.getTotalQuestions());
    }

    @Transactional(readOnly = true)
    public QuizSessionView get(UserAccount account, String publicId) {
        return view(owned(account, publicId));
    }

    @Transactional
    public QuizAnswerSubmission answer(UserAccount account, String publicId, Long itemId, String submittedAnswer) {
        LearnerProfile profile = profile(account);
        QuizSession session = sessions.findOwnedForUpdate(profile.getId(), publicId)
                .orElseThrow(() -> new QuizSessionNotFoundException("Quiz session not found"));
        QuizSessionItem item = items.findByIdAndSessionId(itemId, session.getId())
                .orElseThrow(() -> new QuizSessionNotFoundException("Quiz question not found"));
        if (item.isAnswered()) return new QuizAnswerSubmission(view(session), feedback(item));
        if (session.getState() != QuizSessionState.ACTIVE) throw new QuizRequestException("완료된 퀴즈입니다.");
        QuizSessionItem current = items.findBySessionIdOrderByPosition(session.getId()).stream()
                .filter(value -> !value.isAnswered()).findFirst().orElseThrow();
        if (!current.getId().equals(item.getId())) throw new QuizRequestException("현재 문제부터 풀어주세요.");
        String answer = submittedAnswer == null ? "" : submittedAnswer.trim();
        if (answer.isBlank()) throw new QuizRequestException("답을 입력하거나 선택하세요.");
        List<String> choices = choices(item);
        if (!item.getQuestionType().isInput() && choices.stream().noneMatch(
                value -> evaluator.normalizeText(value).equals(evaluator.normalizeText(answer)))) {
            throw new QuizRequestException("제공된 선택지 중 하나를 골라주세요.");
        }
        boolean correct = evaluator.matches(item.getQuestionType(), answer, item.getCorrectAnswer());
        QuizAward award = learning.recordQuizSessionAnswer(account, item, correct);
        item.answer(answer, correct, award.earnedExperience());
        session.recordAnswer(correct, award.earnedExperience());
        return new QuizAnswerSubmission(view(session), feedback(item));
    }

    @Transactional(readOnly = true)
    public QuizSessionResult result(UserAccount account, String publicId) {
        QuizSession session = owned(account, publicId);
        if (session.getState() != QuizSessionState.COMPLETED) throw new QuizRequestException("아직 진행 중인 퀴즈입니다.");
        List<QuizIncorrectItem> incorrect = items.findBySessionIdOrderByPosition(session.getId()).stream()
                .filter(item -> Boolean.FALSE.equals(item.getCorrect()))
                .map(item -> new QuizIncorrectItem(item.getContentItem().getSlug(), title(item.getContentItem()),
                        item.getSubmittedAnswer(), item.getCorrectAnswer(), item.getExplanation())).toList();
        int incorrectCount = session.getTotalQuestions() - session.getCorrectCount();
        int accuracy = session.getTotalQuestions() == 0 ? 0 : session.getCorrectCount() * 100 / session.getTotalQuestions();
        return new QuizSessionResult(session.getPublicId(), session.getTotalQuestions(), session.getCorrectCount(),
                incorrectCount, accuracy, session.getEarnedExperience(), incorrect);
    }

    private QuizSessionView create(UserAccount account, LearnerProfile profile, QuizMode mode, int count) {
        String publicId = UUID.randomUUID().toString();
        String sessionKey = "quiz-" + profile.getId() + "-" + publicId;
        List<QuizQuestionFactory.QuestionSpec> specs = questions.create(account, sessionKey, count);
        if (specs.isEmpty()) throw new QuizRequestException("현재 범위에서 만들 수 있는 퀴즈가 없습니다.");
        QuizSession session = sessions.save(new QuizSession(profile, publicId, sessionKey, mode, specs.size()));
        int position = 0;
        for (var spec : specs) {
            items.save(new QuizSessionItem(session, spec.content(), position++, spec.type(), spec.instruction(),
                    spec.prompt(), questions.choicesJson(spec.choices()), spec.correctAnswer(), spec.explanation()));
        }
        return view(session);
    }

    private QuizSession owned(UserAccount account, String publicId) {
        LearnerProfile profile = profile(account);
        return sessions.findOwned(profile.getId(), publicId)
                .orElseThrow(() -> new QuizSessionNotFoundException("Quiz session not found"));
    }

    private QuizSessionView view(QuizSession session) {
        QuizSessionItem current = items.findBySessionIdOrderByPosition(session.getId()).stream()
                .filter(item -> !item.isAnswered()).findFirst().orElse(null);
        QuizQuestionView question = current == null ? null : new QuizQuestionView(current.getId(),
                current.getPosition() + 1, current.getQuestionType(), current.getContentItem().getType(),
                current.getInstruction(), current.getPrompt(), choices(current), current.getQuestionType().isInput());
        return new QuizSessionView(session.getPublicId(), session.getMode(), session.getState(),
                session.getTotalQuestions(), session.getAnsweredCount(), session.getCorrectCount(),
                session.getEarnedExperience(), question);
    }

    private QuizFeedback feedback(QuizSessionItem item) {
        return new QuizFeedback(item.getId(), Boolean.TRUE.equals(item.getCorrect()), item.getSubmittedAnswer(),
                item.getCorrectAnswer(), item.getExplanation(), item.getContentItem().getSlug(),
                title(item.getContentItem()), item.getEarnedExperience());
    }

    private List<String> choices(QuizSessionItem item) {
        try { return objectMapper.readValue(item.getChoicesJson(), new TypeReference<>() { }); }
        catch (Exception exception) { throw new IllegalStateException("저장된 퀴즈 선택지를 읽을 수 없습니다.", exception); }
    }

    private String title(ContentItem item) {
        return item.getWord() != null ? item.getWord().getExpression() : item.getGrammar().getPattern();
    }

    private LearnerProfile profile(UserAccount account) {
        return profiles.findByUserAccountLoginId(account.getLoginId())
                .orElseThrow(() -> new NoSuchElementException("Learner profile not found"));
    }
}
