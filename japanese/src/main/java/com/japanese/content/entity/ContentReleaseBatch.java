package com.japanese.content.entity;

import com.japanese.account.entity.UserAccount;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "content_release_batches", uniqueConstraints =
        @UniqueConstraint(name = "uk_content_release_batch_preview", columnNames = {"gate_version", "preview_digest"}))
public class ContentReleaseBatch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "reviewer_id", nullable = false) private UserAccount reviewer;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private ContentReleaseBatchStatus status;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 48) private ContentReleaseMode mode;
    @Column(name = "gate_version", nullable = false, length = 40) private String gateVersion;
    @Column(name = "preview_digest", nullable = false, length = 64) private String previewDigest;
    @Column(name = "execution_note", nullable = false, length = 1000) private String executionNote;
    @Column(name = "target_count", nullable = false) private int targetCount;
    @Column(name = "success_count", nullable = false) private int successCount;
    @Column(name = "releasable_count", nullable = false) private int releasableCount;
    @Column(name = "manual_override_count", nullable = false) private int manualOverrideCount;
    @Column(name = "blocked_count", nullable = false) private int blockedCount;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "executed_at", nullable = false) private Instant executedAt;
    @Column(name = "rolled_back_at") private Instant rolledBackAt;
    @Column(name = "rollback_reason", length = 1000) private String rollbackReason;
    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("position asc") private List<ContentReleaseBatchItem> items = new ArrayList<>();

    protected ContentReleaseBatch() {}

    public ContentReleaseBatch(UserAccount reviewer, ContentReleaseMode mode, String gateVersion,
                               String previewDigest, String executionNote, int targetCount,
                               int releasableCount, int manualOverrideCount, int blockedCount) {
        this.reviewer = reviewer; this.mode = mode; this.gateVersion = gateVersion;
        this.previewDigest = previewDigest; this.executionNote = executionNote;
        this.targetCount = targetCount; this.releasableCount = releasableCount;
        this.manualOverrideCount = manualOverrideCount; this.blockedCount = blockedCount;
        this.successCount = targetCount; this.status = ContentReleaseBatchStatus.EXECUTED;
        this.createdAt = Instant.now(); this.executedAt = this.createdAt;
    }

    public void addItem(ContentReleaseBatchItem item) { items.add(item); item.attach(this); }
    public void markRolledBack(String reason) {
        if (status != ContentReleaseBatchStatus.EXECUTED) throw new IllegalStateException("BATCH_ALREADY_ROLLED_BACK");
        status = ContentReleaseBatchStatus.ROLLED_BACK; rollbackReason = reason; rolledBackAt = Instant.now();
    }
    public Long getId(){return id;} public UserAccount getReviewer(){return reviewer;} public ContentReleaseBatchStatus getStatus(){return status;}
    public ContentReleaseMode getMode(){return mode;} public String getGateVersion(){return gateVersion;} public String getPreviewDigest(){return previewDigest;}
    public String getExecutionNote(){return executionNote;} public int getTargetCount(){return targetCount;} public int getSuccessCount(){return successCount;}
    public int getReleasableCount(){return releasableCount;} public int getManualOverrideCount(){return manualOverrideCount;} public int getBlockedCount(){return blockedCount;}
    public Instant getCreatedAt(){return createdAt;} public Instant getExecutedAt(){return executedAt;} public Instant getRolledBackAt(){return rolledBackAt;}
    public String getRollbackReason(){return rollbackReason;} public List<ContentReleaseBatchItem> getItems(){return Collections.unmodifiableList(items);}
}
