package com.japanese.content.entity;

import com.japanese.account.entity.UserAccount;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "curation_review_history", indexes = @Index(name = "idx_curation_history_target", columnList = "record_type,record_id,reviewed_at"))
public class CurationReviewHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @Column(name="record_type",nullable=false,length=30) private CurationRecordType recordType;
    @Column(name="record_id",nullable=false) private Long recordId;
    @Enumerated(EnumType.STRING) @Column(name="previous_status",length=20) private ReviewStatus previousStatus;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private ReviewStatus status;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="reviewer_id",nullable=false) private UserAccount reviewer;
    @Column(length=1000) private String note;
    @Column(name="reviewed_at",nullable=false) private Instant reviewedAt;
    protected CurationReviewHistory() {}
    public CurationReviewHistory(CurationRecordType type, Long recordId, ReviewStatus previous, ReviewStatus status,
                                 UserAccount reviewer, String note) {
        this.recordType=type;this.recordId=recordId;this.previousStatus=previous;this.status=status;
        this.reviewer=reviewer;this.note=note;this.reviewedAt=Instant.now();
    }
    public ReviewStatus getPreviousStatus(){return previousStatus;} public ReviewStatus getStatus(){return status;}
    public UserAccount getReviewer(){return reviewer;} public String getNote(){return note;} public Instant getReviewedAt(){return reviewedAt;}
}
