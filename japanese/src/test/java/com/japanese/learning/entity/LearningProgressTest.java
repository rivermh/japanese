package com.japanese.learning.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.japanese.content.entity.ContentItem;
import com.japanese.content.entity.ContentType;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LearningProgressTest {

    @Test
    void lapseRestartsLearningIntervalInsteadOfUsingOldReviewCount() {
        var profile = new LearnerProfile("srs-test", "SRS", "haru");
        var item = new ContentItem("srs-item", ContentType.WORD, "test", true);
        var progress = new LearningProgress(profile, item, StudyResult.CORRECT);
        for (int i = 0; i < 4; i++) {
            progress.record(StudyResult.CORRECT);
        }

        progress.record(StudyResult.INCORRECT);

        assertThat(progress.getLearningState()).isEqualTo(LearningState.LEARNING);
        assertThat(progress.getConsecutiveCorrect()).isZero();
        assertThat(progress.getLapseCount()).isEqualTo(1);
        assertThat(Duration.between(Instant.now(), progress.getNextReviewAt()).toMinutes())
                .isBetween(9L, 10L);

        progress.record(StudyResult.CORRECT);
        assertThat(progress.getLearningState()).isEqualTo(LearningState.REVIEW);
        assertThat(Duration.between(Instant.now(), progress.getNextReviewAt()).toHours())
                .isBetween(23L, 24L);
    }

    @Test
    void marksAStableCardAsMasteredAfterFiveConsecutiveCorrectAnswers() {
        var profile = new LearnerProfile("mastery-test", "Mastery", "haru");
        var item = new ContentItem("mastery-item", ContentType.WORD, "test", true);
        var progress = new LearningProgress(profile, item, StudyResult.CORRECT);
        for (int i = 0; i < 4; i++) {
            progress.record(StudyResult.CORRECT);
        }

        assertThat(progress.getLearningState()).isEqualTo(LearningState.MASTERED);
        assertThat(progress.getConsecutiveCorrect()).isEqualTo(5);
    }
}
