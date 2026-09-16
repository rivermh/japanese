package com.japanese.content.service;

import static org.assertj.core.api.Assertions.*;

import com.japanese.account.entity.*;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.config.SampleContentDataLoader;
import com.japanese.content.dto.AdminContentReleaseBatchModels.*;
import com.japanese.content.dto.AdminContentReleaseDryRunModels.Filter;
import com.japanese.content.entity.*;
import com.japanese.content.repository.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest @ActiveProfiles("sample") @Transactional
class ContentReleaseBatchServiceTest {
    @Autowired ContentReleaseBatchService batches; @Autowired ContentReleaseDryRunService dryRun;
    @Autowired ContentItemRepository contents; @Autowired ContentSourceRepository sources;
    @Autowired ContentSourceRightsService rights; @Autowired LevelRepository levels;
    @Autowired UserAccountRepository accounts; @Autowired ContentReleaseBatchRepository batchRepository;
    @Autowired ContentReleaseBatchItemRepository itemRepository; @Autowired ContentReviewHistoryRepository histories;
    @Autowired SampleContentDataLoader sample;
    UserAccount admin; ContentSource source; String sourceRef;

    @BeforeEach void setup() throws Exception {
        sample.run(); admin=accounts.save(new UserAccount("batch-"+UUID.randomUUID(),null,"hash","Batch Admin",UserRole.ADMIN));
        sourceRef="batch-source-"+UUID.randomUUID(); source=sources.save(new ContentSource(sourceRef,"Batch source","1",null,null,null,null));
        rights.reviewRights(source.getId(),ContentSourceRightsStatus.MANUAL_REVIEW_REQUIRED,"evidence",false,null);
        rights.reviewRights(source.getId(),ContentSourceRightsStatus.ALLOWED,"allowed",false,null);
    }

    @Test void executesPendingAndApprovedTargetsAtomicallyAndIsIdempotent() {
        ContentItem pending=word("pending",true); ContentItem approved=word("approved",true); approved.approve(false); contents.flush();
        var preview=preview(); ExecuteRequest request=request(preview,ContentReleaseMode.RELEASABLE_ONLY,List.of());
        var executed=batches.execute(request,admin);
        assertThat(executed.status()).isEqualTo(ContentReleaseBatchStatus.EXECUTED);
        assertThat(executed.targetCount()).isEqualTo(2); assertThat(executed.items()).hasSize(2);
        assertThat(contents.findById(pending.getId()).orElseThrow().isPublished()).isTrue();
        assertThat(contents.findById(approved.getId()).orElseThrow().isPublished()).isTrue();
        assertThat(executed.items()).extracting(ItemView::previousReviewStatus).containsExactly(ReviewStatus.PENDING,ReviewStatus.APPROVED);
        assertThat(histories.findByContentItemIdOrderByReviewedAtDesc(pending.getId()).get(0).getNote()).contains("BATCH_RELEASE:"+executed.id());
        assertThat(batches.batch(executed.id()).items()).hasSize(2);
        assertThat(batches.execute(request,admin).id()).isEqualTo(executed.id());
        assertThat(batchRepository.count()).isEqualTo(1);
    }

    @Test void staleVersionAndBlockedTargetRejectWithoutMutation() {
        ContentItem valid=word("valid",true); var preview=preview();
        assertThatThrownBy(()->batches.execute(new ExecuteRequest(preview.targetIds(),preview.digest(),"old-gate",ContentReleaseMode.RELEASABLE_ONLY,"run",List.of()),admin))
                .isInstanceOf(ContentReleaseBatchException.class).hasMessage("GATE_VERSION_MISMATCH");
        source.reviewRights(ContentSourceRightsStatus.BLOCKED,"withdrawn",false,null);
        assertThatThrownBy(()->batches.execute(request(preview,ContentReleaseMode.RELEASABLE_ONLY,List.of()),admin))
                .isInstanceOf(ContentReleaseBatchException.class).hasMessage("STALE_PREVIEW");
        assertThat(contents.findById(valid.getId()).orElseThrow().isPublished()).isFalse();
        assertThat(batchRepository.count()).isZero();
    }

    @Test void digestAndTargetOrderingMustMatchPreviewExactly() {
        word("order-a",true); word("order-b",true); var preview=preview();
        List<Long> reversed=new java.util.ArrayList<>(preview.targetIds()); java.util.Collections.reverse(reversed);
        assertThatThrownBy(()->batches.execute(new ExecuteRequest(reversed,preview.digest(),preview.gateVersion(),ContentReleaseMode.RELEASABLE_ONLY,"run",List.of()),admin))
                .isInstanceOf(ContentReleaseBatchException.class).hasMessage("TARGET_ORDER_INVALID");
        String wrong=(preview.digest().startsWith("0")?"1":"0")+preview.digest().substring(1);
        assertThatThrownBy(()->batches.execute(new ExecuteRequest(preview.targetIds(),wrong,preview.gateVersion(),ContentReleaseMode.RELEASABLE_ONLY,"run",List.of()),admin))
                .isInstanceOf(ContentReleaseBatchException.class).hasMessage("STALE_PREVIEW");
        assertThat(batchRepository.count()).isZero();
    }

    @Test void manualIssuesRequireExactExplicitOverride() {
        ContentItem manual=word("manual",false); var preview=preview();
        assertThatThrownBy(()->batches.execute(request(preview,ContentReleaseMode.RELEASABLE_ONLY,List.of()),admin))
                .isInstanceOf(ContentReleaseBatchException.class).hasMessage("MANUAL_OVERRIDE_REQUIRED");
        ManualOverride incomplete=new ManualOverride(manual.getId(),List.of(),"checked");
        assertThatThrownBy(()->batches.execute(request(preview,ContentReleaseMode.ALLOW_EXPLICIT_MANUAL_OVERRIDES,List.of(incomplete)),admin))
                .isInstanceOf(ContentReleaseBatchException.class).hasMessage("MANUAL_OVERRIDE_INCOMPLETE");
        ManualOverride override=new ManualOverride(manual.getId(),List.of(ContentReleaseIssueCode.EXAMPLE_MISSING),"example absence accepted");
        var result=batches.execute(request(preview,ContentReleaseMode.ALLOW_EXPLICIT_MANUAL_OVERRIDES,List.of(override)),admin);
        assertThat(result.manualOverrideCount()).isEqualTo(1);
        assertThat(result.items().get(0).manualOverrideIssueCodes()).containsExactly("EXAMPLE_MISSING");
        assertThat(contents.findById(manual.getId()).orElseThrow().isPublished()).isTrue();
    }

    @Test void blockerCannotBeOverriddenAndEntireBatchRemainsUntouched() {
        ContentItem valid=word("valid-atomic",true); ContentItem blocked=word("blocked",true);
        blocked.getWord().getMeanings().clear(); blocked.getWord().addMeaning(new Meaning("en","English",0)); contents.flush();
        var preview=preview(); ManualOverride attempt=new ManualOverride(blocked.getId(),List.of(ContentReleaseIssueCode.WORD_KOREAN_MEANING_MISSING),"attempt");
        assertThatThrownBy(()->batches.execute(request(preview,ContentReleaseMode.ALLOW_EXPLICIT_MANUAL_OVERRIDES,List.of(attempt)),admin))
                .isInstanceOf(ContentReleaseBatchException.class).hasMessage("PUBLICATION_BLOCKED");
        assertThat(contents.findById(valid.getId()).orElseThrow().isPublished()).isFalse();
        assertThat(contents.findById(blocked.getId()).orElseThrow().isPublished()).isFalse();
        assertThat(batchRepository.count()).isZero();
    }

    @Test void rollbackRestoresManifestStateAndRejectsConflictsOrSecondRollback() {
        ContentItem item=word("rollback",true); var preview=preview(); var executed=batches.execute(request(preview,ContentReleaseMode.RELEASABLE_ONLY,List.of()),admin);
        var rolledBack=batches.rollback(executed.id(),"pilot cancelled",admin);
        assertThat(rolledBack.status()).isEqualTo(ContentReleaseBatchStatus.ROLLED_BACK);
        ContentItem restored=contents.findById(item.getId()).orElseThrow(); assertThat(restored.isPublished()).isFalse(); assertThat(restored.getReviewStatus()).isEqualTo(ReviewStatus.PENDING);
        assertThatThrownBy(()->batches.rollback(executed.id(),"again",admin)).isInstanceOf(ContentReleaseBatchException.class).hasMessage("BATCH_ALREADY_ROLLED_BACK");

        ContentItem conflict=word("conflict",true); var second=batches.execute(request(previewFor(List.of(conflict)),ContentReleaseMode.RELEASABLE_ONLY,List.of()),admin);
        conflict.unpublishApproved(); contents.flush();
        assertThatThrownBy(()->batches.rollback(second.id(),"conflict",admin)).isInstanceOf(ContentReleaseBatchException.class).hasMessage("BATCH_ROLLBACK_STATE_CONFLICT");
        assertThat(batchRepository.findById(second.id()).orElseThrow().getStatus()).isEqualTo(ContentReleaseBatchStatus.EXECUTED);
    }

    private com.japanese.content.dto.AdminContentReleaseDryRunModels.Result preview(){return dryRun.run(new Filter(ContentType.WORD,"N5",null,false,sourceRef,ContentSourceRightsStatus.ALLOWED));}
    private com.japanese.content.dto.AdminContentReleaseDryRunModels.Result previewFor(List<ContentItem> ignored){return preview();}
    private ExecuteRequest request(com.japanese.content.dto.AdminContentReleaseDryRunModels.Result p,ContentReleaseMode mode,List<ManualOverride> overrides){return new ExecuteRequest(p.targetIds(),p.digest(),p.gateVersion(),mode,"synthetic test batch",overrides);}
    private ContentItem word(String name,boolean example){ContentItem item=new ContentItem("batch-"+name+"-"+UUID.randomUUID(),ContentType.WORD,sourceRef,false);Word word=new Word(name,"reading-"+name,"noun",null);word.addMeaning(new Meaning("ko","뜻",0));item.attachWord(word);item.addLevel(levels.findBySystemAndCode("JLPT","N5").orElseThrow());if(example)item.addExample(new Example("Example",null,"예문",0));return contents.saveAndFlush(item);}
}
