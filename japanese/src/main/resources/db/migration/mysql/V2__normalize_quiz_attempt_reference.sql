alter table quiz_attempts
    add column origin_type enum ('IMPORTED_SOURCE','QUIZ_SESSION_ITEM','LEGACY_UNKNOWN') null,
    add column imported_source_record_id bigint null,
    add column quiz_session_item_id bigint null;

-- A numeric id can exist in both legacy and QuizSession namespaces. When the
-- session belongs to the same learner, provenance is ambiguous regardless of
-- streak eligibility, so preserve the row as unknown before other backfills.
update quiz_attempts attempt
join imported_source_records source_record
  on source_record.id = attempt.question_source_record_id
join quiz_session_items session_item
  on session_item.id = attempt.question_source_record_id
join quiz_sessions session
  on session.id = session_item.session_id
 and session.learner_profile_id = attempt.learner_profile_id
set attempt.origin_type = 'LEGACY_UNKNOWN'
where attempt.origin_type is null;

-- Legacy quiz writes were streak eligible and used ImportedSourceRecord ids.
-- Ambiguous owner-matching rows were marked above and are excluded here.
update quiz_attempts attempt
join imported_source_records source_record
  on source_record.id = attempt.question_source_record_id
set attempt.origin_type = 'IMPORTED_SOURCE',
    attempt.imported_source_record_id = source_record.id
where attempt.origin_type is null
  and (attempt.streak_eligible is null or attempt.streak_eligible = 1);

-- Quiz 2.0 writes are not streak eligible. Ownership must also match the
-- session owner before the reference is considered trustworthy. Ambiguous
-- rows were marked above and therefore cannot be classified here.
update quiz_attempts attempt
join quiz_session_items session_item
  on session_item.id = attempt.question_source_record_id
join quiz_sessions session
  on session.id = session_item.session_id
 and session.learner_profile_id = attempt.learner_profile_id
set attempt.origin_type = 'QUIZ_SESSION_ITEM',
    attempt.quiz_session_item_id = session_item.id
where attempt.origin_type is null
  and attempt.streak_eligible = 0;

update quiz_attempts
set origin_type = 'LEGACY_UNKNOWN'
where origin_type is null;

alter table quiz_attempts modify origin_type enum ('IMPORTED_SOURCE','QUIZ_SESSION_ITEM','LEGACY_UNKNOWN') not null;
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
