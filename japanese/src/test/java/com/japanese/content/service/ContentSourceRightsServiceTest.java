package com.japanese.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.japanese.content.entity.ContentSource;
import com.japanese.content.entity.ContentSourceRightsStatus;
import com.japanese.content.repository.ContentSourceRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("sample")
@Transactional
class ContentSourceRightsServiceTest {

    @Autowired ContentSourceRightsService rights;
    @Autowired ContentSourceRepository sources;

    @Test
    void newAndCatalogSourcesDefaultToUnknownAndCannotBeReleased() {
        ContentSource source = source("new");

        assertThat(source.getRightsStatus()).isEqualTo(ContentSourceRightsStatus.UNKNOWN);
        assertThat(source.getRightsReviewedAt()).isNull();
        assertThat(rights.releaseEligibilityForSource(source).allowed()).isFalse();
        assertThat(rights.releaseEligibilityForSource(source).blockingCode()).isEqualTo("RIGHTS_NOT_ALLOWED");
        assertThat(sources.findBySourceRef("sample").orElseThrow().getRightsStatus())
                .isEqualTo(ContentSourceRightsStatus.UNKNOWN);
        assertThat(rights.releaseEligibility(null).allowed()).isFalse();
        assertThat(rights.releaseEligibility("not-registered").blockingCode())
                .isEqualTo("SOURCE_NOT_REGISTERED");
    }

    @Test
    void onlyAllowedSourceIsEligibleAndEverySupportedTransitionIsAudited() {
        ContentSource source = source("transitions");

        assertThatThrownBy(() -> rights.reviewRights(
                source.getId(), ContentSourceRightsStatus.ALLOWED, "근거", false, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UNKNOWN -> ALLOWED");

        rights.reviewRights(source.getId(), ContentSourceRightsStatus.MANUAL_REVIEW_REQUIRED,
                "재배포 조건 확인 필요", false, null);
        assertThat(rights.releaseEligibilityForSource(source).allowed()).isFalse();

        rights.reviewRights(source.getId(), ContentSourceRightsStatus.ALLOWED,
                "재배포 허용 근거 확인", false, null);
        assertThat(rights.releaseEligibilityForSource(source).allowed()).isTrue();
        assertThat(source.getRightsReviewedAt()).isNotNull();
        assertThat(source.getRightsReviewNote()).isEqualTo("재배포 허용 근거 확인");

        rights.reviewRights(source.getId(), ContentSourceRightsStatus.BLOCKED,
                "사용 조건 변경", false, null);
        assertThat(rights.releaseEligibilityForSource(source).allowed()).isFalse();

        rights.reviewRights(source.getId(), ContentSourceRightsStatus.MANUAL_REVIEW_REQUIRED,
                "재검토 시작", false, null);
        assertThat(source.getRightsStatus()).isEqualTo(ContentSourceRightsStatus.MANUAL_REVIEW_REQUIRED);
        assertThatThrownBy(() -> rights.reviewRights(
                source.getId(), ContentSourceRightsStatus.MANUAL_REVIEW_REQUIRED, "중복", false, null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> rights.reviewRights(
                source.getId(), ContentSourceRightsStatus.BLOCKED, " ", false, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiredAttributionMustBePresentBeforeAllowedTransition() {
        ContentSource source = source("attribution");
        rights.reviewRights(source.getId(), ContentSourceRightsStatus.MANUAL_REVIEW_REQUIRED,
                "권리 자료 확보", true, null);

        assertThatThrownBy(() -> rights.reviewRights(
                source.getId(), ContentSourceRightsStatus.ALLOWED,
                "허용", true, " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("attribution");
        assertThat(source.getRightsStatus()).isEqualTo(ContentSourceRightsStatus.MANUAL_REVIEW_REQUIRED);

        rights.reviewRights(source.getId(), ContentSourceRightsStatus.ALLOWED,
                "표시 조건을 포함해 허용", true, "Source authors");
        assertThat(source.isAttributionRequired()).isTrue();
        assertThat(source.getAttribution()).isEqualTo("Source authors");
        assertThat(rights.releaseEligibilityForSource(source).allowed()).isTrue();
    }

    private ContentSource source(String suffix) {
        return sources.save(new ContentSource(
                "rights-" + suffix + "-" + UUID.randomUUID(),
                "Rights test source",
                "1",
                null,
                null,
                null,
                null));
    }
}
