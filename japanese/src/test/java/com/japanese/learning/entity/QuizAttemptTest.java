package com.japanese.learning.entity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.japanese.content.entity.ImportedSourceRecord;
import com.japanese.account.entity.UserAccount;
import com.japanese.account.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class QuizAttemptTest {

    @Test
    void rejectsMixedOrMissingProvenanceReferences() {
        LearnerProfile learner = new LearnerProfile(
                new UserAccount("attempt-test", null, "hash", "Attempt", UserRole.USER), 10, "haru");
        QuizAttempt attempt = QuizAttempt.forLegacyUnknown(
                learner, 1L, StudyResult.CORRECT, 10, true);
        ReflectionTestUtils.setField(attempt, "originType", QuizAttemptOriginType.IMPORTED_SOURCE);

        assertThatThrownBy(attempt::validateProvenance)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("provenance");

        ReflectionTestUtils.setField(attempt, "importedSourceRecord",
                new ImportedSourceRecord("source", "quiz", 1L, "", "Prompt", "Answer"));
        ReflectionTestUtils.setField(attempt, "quizSessionItem", new QuizSessionItem());
        assertThatThrownBy(attempt::validateProvenance)
                .isInstanceOf(IllegalStateException.class);
    }
}
