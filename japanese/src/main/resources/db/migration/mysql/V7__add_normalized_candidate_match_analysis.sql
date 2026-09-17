create table normalized_candidate_match_pairs (
    id bigint not null auto_increment primary key,
    left_candidate_id bigint not null,
    right_candidate_id bigint not null,
    assessment varchar(20) not null,
    generated_at datetime(6) not null,
    constraint uk_normalized_candidate_match_pair unique (left_candidate_id, right_candidate_id),
    constraint fk_normalized_candidate_match_pair_left foreign key (left_candidate_id) references normalized_content_candidates(id),
    constraint fk_normalized_candidate_match_pair_right foreign key (right_candidate_id) references normalized_content_candidates(id),
    index ix_normalized_candidate_match_pair_right (right_candidate_id),
    index ix_normalized_candidate_match_pair_assessment (assessment)
);

create table normalized_candidate_match_evidence (
    id bigint not null auto_increment primary key,
    pair_id bigint not null,
    position integer not null,
    evidence_code varchar(40) not null,
    field_name varchar(40) null,
    detail varchar(2000) null,
    constraint uk_normalized_candidate_match_evidence_position unique (pair_id, position),
    constraint fk_normalized_candidate_match_evidence_pair foreign key (pair_id) references normalized_candidate_match_pairs(id)
);
