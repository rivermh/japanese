create table normalized_candidate_pair_reviews (
    id bigint not null auto_increment primary key,
    left_candidate_id bigint not null,
    right_candidate_id bigint not null,
    decision varchar(20) not null,
    reviewer_id bigint not null,
    note varchar(2000) null,
    reviewed_at datetime(6) not null,
    left_normalized_at_snapshot datetime(6) not null,
    right_normalized_at_snapshot datetime(6) not null,
    assessment_snapshot varchar(20) not null,
    version bigint not null,
    constraint uk_normalized_candidate_pair_review unique (left_candidate_id, right_candidate_id),
    constraint fk_normalized_candidate_pair_review_left foreign key (left_candidate_id) references normalized_content_candidates(id),
    constraint fk_normalized_candidate_pair_review_right foreign key (right_candidate_id) references normalized_content_candidates(id),
    constraint fk_normalized_candidate_pair_review_reviewer foreign key (reviewer_id) references user_accounts(id)
);

create table normalized_candidate_pair_review_history (
    id bigint not null auto_increment primary key,
    left_candidate_id bigint not null,
    right_candidate_id bigint not null,
    previous_decision varchar(20) null,
    new_decision varchar(20) not null,
    reviewer_id bigint not null,
    note varchar(2000) null,
    reviewed_at datetime(6) not null,
    left_normalized_at_snapshot datetime(6) not null,
    right_normalized_at_snapshot datetime(6) not null,
    assessment_snapshot varchar(20) not null,
    constraint fk_normalized_candidate_pair_review_history_left foreign key (left_candidate_id) references normalized_content_candidates(id),
    constraint fk_normalized_candidate_pair_review_history_right foreign key (right_candidate_id) references normalized_content_candidates(id),
    constraint fk_normalized_candidate_pair_review_history_reviewer foreign key (reviewer_id) references user_accounts(id),
    index ix_normalized_candidate_pair_review_history_pair (left_candidate_id, right_candidate_id)
);
