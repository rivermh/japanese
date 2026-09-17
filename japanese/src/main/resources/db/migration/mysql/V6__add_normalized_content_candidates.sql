create table normalized_content_candidates (
    id bigint not null auto_increment primary key,
    candidate_type varchar(20) not null,
    source_ref varchar(160) not null,
    source_note_id bigint not null,
    source_identity_key varchar(160) null,
    quality_state varchar(20) not null,
    normalized_at datetime(6) not null,
    constraint uk_normalized_candidate_snapshot unique (source_ref, source_note_id, candidate_type),
    index ix_normalized_candidate_identity (candidate_type, source_identity_key)
);

create table normalized_candidate_warnings (
    id bigint not null auto_increment primary key,
    candidate_id bigint not null,
    position integer not null,
    issue_code varchar(80) not null,
    severity varchar(20) not null,
    message varchar(2000) not null,
    constraint uk_normalized_candidate_warning_position unique (candidate_id, position),
    constraint fk_normalized_candidate_warning_candidate foreign key (candidate_id) references normalized_content_candidates(id)
);

create table normalized_candidate_extra_fields (
    id bigint not null auto_increment primary key,
    candidate_id bigint not null,
    field_name varchar(160) not null,
    field_value longtext not null,
    constraint uk_normalized_candidate_extra_field unique (candidate_id, field_name),
    constraint fk_normalized_candidate_extra_field_candidate foreign key (candidate_id) references normalized_content_candidates(id)
);

create table normalized_vocabulary_candidates (
    id bigint not null auto_increment primary key,
    candidate_id bigint not null,
    entry_id varchar(160) null,
    expression varchar(500) null,
    reading varchar(500) null,
    part_of_speech varchar(500) null,
    pitch_accent_terminal_states varchar(500) null,
    pitch_accent_mora varchar(500) null,
    level_code varchar(20) null,
    level_raw_value varchar(160) null,
    level_source_field varchar(40) null,
    normalized_search_expression varchar(500) null,
    normalized_search_reading varchar(500) null,
    constraint uk_normalized_vocabulary_candidate unique (candidate_id),
    constraint fk_normalized_vocabulary_candidate_candidate foreign key (candidate_id) references normalized_content_candidates(id)
);

create table normalized_vocabulary_candidate_meanings (
    id bigint not null auto_increment primary key,
    candidate_id bigint not null,
    sense_order integer not null,
    meaning_text longtext not null,
    constraint uk_normalized_vocab_candidate_meaning_order unique (candidate_id, sense_order),
    constraint fk_normalized_vocab_candidate_meaning_candidate foreign key (candidate_id) references normalized_content_candidates(id)
);

create table normalized_vocabulary_candidate_examples (
    id bigint not null auto_increment primary key,
    candidate_id bigint not null,
    display_order integer not null,
    meaning_label varchar(500) null,
    japanese_text longtext not null,
    reading longtext null,
    translation longtext null,
    constraint uk_normalized_vocab_candidate_example_order unique (candidate_id, display_order),
    constraint fk_normalized_vocab_candidate_example_candidate foreign key (candidate_id) references normalized_content_candidates(id)
);

create table normalized_grammar_candidates (
    id bigint not null auto_increment primary key,
    candidate_id bigint not null,
    unit_id varchar(160) null,
    pattern varchar(500) null,
    front_example_display_order integer null,
    front_example_japanese_text longtext null,
    front_example_reading longtext null,
    front_example_translation longtext null,
    meaning_gloss varchar(2000) null,
    nuance longtext null,
    connection_form varchar(2000) null,
    level_code varchar(20) null,
    level_raw_value varchar(160) null,
    level_source_field varchar(40) null,
    raw_kind varchar(500) null,
    constraint uk_normalized_grammar_candidate unique (candidate_id),
    constraint fk_normalized_grammar_candidate_candidate foreign key (candidate_id) references normalized_content_candidates(id)
);

create table normalized_grammar_candidate_confusable_patterns (
    id bigint not null auto_increment primary key,
    candidate_id bigint not null,
    display_order integer not null,
    pattern varchar(500) not null,
    explanation longtext null,
    constraint uk_normalized_grammar_candidate_confusable_order unique (candidate_id, display_order),
    constraint fk_normalized_grammar_candidate_confusable_candidate foreign key (candidate_id) references normalized_content_candidates(id)
);
