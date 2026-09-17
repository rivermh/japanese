create table normalized_candidate_canonical_groups (
    id bigint not null auto_increment primary key,
    canonical_candidate_id bigint not null,
    decided_by bigint not null,
    decided_at datetime(6) not null,
    note varchar(2000) null,
    status varchar(20) not null,
    dissolved_by bigint null,
    dissolved_at datetime(6) null,
    dissolution_note varchar(2000) null,
    version bigint not null,
    constraint fk_canonical_group_canonical_candidate foreign key (canonical_candidate_id)
        references normalized_content_candidates(id),
    constraint fk_canonical_group_decided_by foreign key (decided_by) references user_accounts(id),
    constraint fk_canonical_group_dissolved_by foreign key (dissolved_by) references user_accounts(id)
);

create table normalized_candidate_canonical_group_members (
    id bigint not null auto_increment primary key,
    group_id bigint not null,
    member_candidate_id bigint not null,
    member_normalized_at_snapshot datetime(6) not null,
    constraint uk_canonical_group_member_candidate unique (member_candidate_id),
    constraint fk_canonical_group_member_group foreign key (group_id)
        references normalized_candidate_canonical_groups(id),
    constraint fk_canonical_group_member_candidate foreign key (member_candidate_id)
        references normalized_content_candidates(id),
    index ix_canonical_group_member_group (group_id)
);

create table normalized_candidate_canonical_group_edges (
    id bigint not null auto_increment primary key,
    group_id bigint not null,
    left_candidate_id bigint not null,
    right_candidate_id bigint not null,
    pair_review_id bigint not null,
    pair_review_version_snapshot bigint not null,
    pair_reviewed_at_snapshot datetime(6) not null,
    reviewer_id_snapshot bigint not null,
    review_decision_snapshot varchar(20) not null,
    assessment_snapshot varchar(20) not null,
    left_normalized_at_snapshot datetime(6) not null,
    right_normalized_at_snapshot datetime(6) not null,
    constraint uk_canonical_group_edge unique (group_id, left_candidate_id, right_candidate_id),
    constraint fk_canonical_group_edge_group foreign key (group_id)
        references normalized_candidate_canonical_groups(id),
    constraint fk_canonical_group_edge_left foreign key (left_candidate_id)
        references normalized_content_candidates(id),
    constraint fk_canonical_group_edge_right foreign key (right_candidate_id)
        references normalized_content_candidates(id),
    constraint fk_canonical_group_edge_review foreign key (pair_review_id)
        references normalized_candidate_pair_reviews(id),
    constraint fk_canonical_group_edge_reviewer foreign key (reviewer_id_snapshot)
        references user_accounts(id),
    index ix_canonical_group_edge_review (pair_review_id)
);
