alter table quiz_attempts add column origin_type enum ('IMPORTED_SOURCE','QUIZ_SESSION_ITEM','LEGACY_UNKNOWN');
alter table quiz_attempts add column imported_source_record_id bigint;
alter table quiz_attempts add column quiz_session_item_id bigint;

update quiz_attempts attempt
set origin_type = 'LEGACY_UNKNOWN'
where origin_type is null
  and exists (
      select 1 from imported_source_records source_record
      where source_record.id = attempt.question_source_record_id
  )
  and exists (
      select 1
      from quiz_session_items session_item
      join quiz_sessions session on session.id = session_item.session_id
      where session_item.id = attempt.question_source_record_id
        and session.learner_profile_id = attempt.learner_profile_id
  );

update quiz_attempts attempt
set origin_type = 'IMPORTED_SOURCE',
    imported_source_record_id = question_source_record_id
where origin_type is null
  and (streak_eligible is null or streak_eligible = true)
  and exists (
      select 1 from imported_source_records source_record
      where source_record.id = attempt.question_source_record_id
  );

update quiz_attempts attempt
set origin_type = 'QUIZ_SESSION_ITEM',
    quiz_session_item_id = question_source_record_id
where origin_type is null
  and streak_eligible = false
  and exists (
      select 1
      from quiz_session_items session_item
      join quiz_sessions session on session.id = session_item.session_id
      where session_item.id = attempt.question_source_record_id
        and session.learner_profile_id = attempt.learner_profile_id
  );

update quiz_attempts set origin_type = 'LEGACY_UNKNOWN' where origin_type is null;

alter table quiz_attempts alter column origin_type set not null;
create index idx_quiz_attempt_imported_source on quiz_attempts (imported_source_record_id);
create index idx_quiz_attempt_session_item on quiz_attempts (quiz_session_item_id);
alter table quiz_attempts add constraint fk_quiz_attempt_imported_source
    foreign key (imported_source_record_id) references imported_source_records (id);
alter table quiz_attempts add constraint fk_quiz_attempt_session_item
    foreign key (quiz_session_item_id) references quiz_session_items (id);
alter table quiz_attempts add constraint chk_quiz_attempt_provenance check (
    (origin_type = 'IMPORTED_SOURCE' and imported_source_record_id is not null and quiz_session_item_id is null)
    or (origin_type = 'QUIZ_SESSION_ITEM' and imported_source_record_id is null and quiz_session_item_id is not null)
    or (origin_type = 'LEGACY_UNKNOWN' and imported_source_record_id is null and quiz_session_item_id is null)
);
