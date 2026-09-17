package com.japanese.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class FlywayMigrationTest {

    @Test
    void emptyDatabaseMigrationIsRepeatable() throws Exception {
        String url = databaseUrl("empty");
        Flyway flyway = flyway(url, false);

        MigrateResult first = flyway.migrate();
        MigrateResult second = flyway.migrate();

        assertThat(first.migrationsExecuted).isEqualTo(7);
        assertThat(second.migrationsExecuted).isZero();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("7");
        assertThat(tableExists(url, "private_apkg_notes")).isTrue();
        assertThat(tableExists(url, "content_items")).isTrue();
        assertThat(tableExists(url, "today_study_sessions")).isTrue();
        assertThat(columnExists(url, "quiz_attempts", "origin_type")).isTrue();
        assertThat(columnExists(url, "content_sources", "rights_status")).isTrue();
        assertThat(tableExists(url, "content_release_batches")).isTrue();
        assertThat(tableExists(url, "content_release_batch_items")).isTrue();
        assertThat(tableExists(url, "normalized_content_candidates")).isTrue();
        assertThat(tableExists(url, "normalized_candidate_warnings")).isTrue();
        assertThat(tableExists(url, "normalized_candidate_extra_fields")).isTrue();
        assertThat(tableExists(url, "normalized_vocabulary_candidates")).isTrue();
        assertThat(tableExists(url, "normalized_vocabulary_candidate_meanings")).isTrue();
        assertThat(tableExists(url, "normalized_vocabulary_candidate_examples")).isTrue();
        assertThat(tableExists(url, "normalized_grammar_candidates")).isTrue();
        assertThat(tableExists(url, "normalized_grammar_candidate_confusable_patterns")).isTrue();
        assertThat(tableExists(url, "normalized_candidate_match_pairs")).isTrue();
        assertThat(tableExists(url, "normalized_candidate_match_evidence")).isTrue();
    }

    @Test
    void v2BackfillsOnlyVerifiedReferencesAndPreservesEveryAttempt() throws Exception {
        String url = databaseUrl("quiz_attempt_backfill");
        Flyway v1 = Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration/h2")
                .target("1")
                .cleanDisabled(true)
                .load();
        v1.migrate();

        try (Connection connection = connection(url); Statement sql = connection.createStatement()) {
            sql.executeUpdate("insert into user_accounts (id, joined_at, display_name, login_id, password_hash, role) values (1, current_timestamp, 'Learner', 'migration-user', 'hash', 'USER')");
            sql.executeUpdate("insert into user_accounts (id, joined_at, display_name, login_id, password_hash, role) values (2, current_timestamp, 'Other Learner', 'migration-user-2', 'hash', 'USER')");
            sql.executeUpdate("insert into learner_profiles (id, experience, created_at, updated_at, user_account_id, character_key, display_name, learner_key) values (1, 0, current_timestamp, current_timestamp, 1, 'haru', 'Learner', 'migration-learner')");
            sql.executeUpdate("insert into learner_profiles (id, experience, created_at, updated_at, user_account_id, character_key, display_name, learner_key) values (2, 0, current_timestamp, current_timestamp, 2, 'haru', 'Other Learner', 'migration-learner-2')");
            sql.executeUpdate("insert into imported_source_records (id, source_note_id, note_type, source_ref, tags, field_names, field_values) values (10, 10, 'quiz', 'source', '', 'Prompt', 'Answer')");
            sql.executeUpdate("insert into imported_source_records (id, source_note_id, note_type, source_ref, tags, field_names, field_values) values (100, 100, 'quiz', 'source', '', 'Prompt', 'Answer')");
            sql.executeUpdate("insert into imported_source_records (id, source_note_id, note_type, source_ref, tags, field_names, field_values) values (101, 101, 'quiz', 'source', '', 'Prompt', 'Answer')");
            sql.executeUpdate("insert into imported_source_records (id, source_note_id, note_type, source_ref, tags, field_names, field_values) values (102, 102, 'quiz', 'source', '', 'Prompt', 'Answer')");
            sql.executeUpdate("insert into imported_source_records (id, source_note_id, note_type, source_ref, tags, field_names, field_values) values (104, 104, 'quiz', 'source', '', 'Prompt', 'Answer')");
            sql.executeUpdate("insert into content_items (id, published, slug, source_ref, type, review_status) values (1, true, 'migration-content', 'source', 'WORD', 'APPROVED')");
            sql.executeUpdate("insert into quiz_sessions (id, answered_count, correct_count, earned_experience, total_questions, learner_profile_id, started_at, public_id, session_key, mode, state) values (1, 1, 1, 10, 1, 1, current_timestamp, '00000000-0000-0000-0000-000000000001', 'migration-session', 'QUICK', 'COMPLETED')");
            sql.executeUpdate("insert into quiz_sessions (id, answered_count, correct_count, earned_experience, total_questions, learner_profile_id, started_at, public_id, session_key, mode, state) values (2, 1, 1, 10, 1, 2, current_timestamp, '00000000-0000-0000-0000-000000000002', 'migration-session-2', 'QUICK', 'COMPLETED')");
            sql.executeUpdate("insert into quiz_session_items (id, answered, correct, earned_experience, position, answered_at, content_item_id, session_id, instruction, correct_answer, choices_json, prompt, question_type) values (20, true, true, 10, 0, current_timestamp, 1, 1, 'instruction', 'answer', '[]', 'prompt', 'WORD_READING_INPUT')");
            sql.executeUpdate("insert into quiz_session_items (id, answered, correct, earned_experience, position, answered_at, content_item_id, session_id, instruction, correct_answer, choices_json, prompt, question_type) values (100, true, true, 10, 1, current_timestamp, 1, 1, 'instruction', 'answer', '[]', 'prompt', 'WORD_READING_INPUT')");
            sql.executeUpdate("insert into quiz_session_items (id, answered, correct, earned_experience, position, answered_at, content_item_id, session_id, instruction, correct_answer, choices_json, prompt, question_type) values (101, true, true, 10, 2, current_timestamp, 1, 1, 'instruction', 'answer', '[]', 'prompt', 'WORD_READING_INPUT')");
            sql.executeUpdate("insert into quiz_session_items (id, answered, correct, earned_experience, position, answered_at, content_item_id, session_id, instruction, correct_answer, choices_json, prompt, question_type) values (103, true, true, 10, 3, current_timestamp, 1, 1, 'instruction', 'answer', '[]', 'prompt', 'WORD_READING_INPUT')");
            sql.executeUpdate("insert into quiz_session_items (id, answered, correct, earned_experience, position, answered_at, content_item_id, session_id, instruction, correct_answer, choices_json, prompt, question_type) values (104, true, true, 10, 0, current_timestamp, 1, 2, 'instruction', 'answer', '[]', 'prompt', 'WORD_READING_INPUT')");
            sql.executeUpdate("insert into quiz_attempts (id, earned_experience, streak_eligible, answered_at, learner_profile_id, question_source_record_id, result) values (1, 10, true, current_timestamp, 1, 10, 'CORRECT')");
            sql.executeUpdate("insert into quiz_attempts (id, earned_experience, streak_eligible, answered_at, learner_profile_id, question_source_record_id, result) values (2, 2, false, current_timestamp, 1, 20, 'INCORRECT')");
            sql.executeUpdate("insert into quiz_attempts (id, earned_experience, streak_eligible, answered_at, learner_profile_id, question_source_record_id, result) values (3, 2, false, current_timestamp, 1, 10, 'INCORRECT')");
            sql.executeUpdate("insert into quiz_attempts (id, earned_experience, streak_eligible, answered_at, learner_profile_id, question_source_record_id, result) values (4, 3, true, current_timestamp, 1, 100, 'CORRECT')");
            sql.executeUpdate("insert into quiz_attempts (id, earned_experience, streak_eligible, answered_at, learner_profile_id, question_source_record_id, result) values (5, 4, null, current_timestamp, 1, 101, 'INCORRECT')");
            sql.executeUpdate("insert into quiz_attempts (id, earned_experience, streak_eligible, answered_at, learner_profile_id, question_source_record_id, result) values (6, 5, true, current_timestamp, 1, 102, 'CORRECT')");
            sql.executeUpdate("insert into quiz_attempts (id, earned_experience, streak_eligible, answered_at, learner_profile_id, question_source_record_id, result) values (7, 6, false, current_timestamp, 1, 103, 'INCORRECT')");
            sql.executeUpdate("insert into quiz_attempts (id, earned_experience, streak_eligible, answered_at, learner_profile_id, question_source_record_id, result) values (8, 7, true, current_timestamp, 1, 104, 'CORRECT')");
        }

        Flyway upgraded = Flyway.configure().dataSource(url, "sa", "")
                .locations("classpath:db/migration/h2").cleanDisabled(true).load();
        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(6);
        assertThat(upgraded.migrate().migrationsExecuted).isZero();

        try (Connection connection = connection(url); Statement sql = connection.createStatement()) {
            assertThat(singleInt(sql, "select count(*) from quiz_attempts")).isEqualTo(8);
            assertThat(singleInt(sql, "select sum(earned_experience) from quiz_attempts")).isEqualTo(39);
            assertThat(singleInt(sql, "select count(*) from quiz_attempts where result='CORRECT'")).isEqualTo(4);
            assertThat(singleText(sql, "select origin_type from quiz_attempts where id=1")).isEqualTo("IMPORTED_SOURCE");
            assertThat(singleLong(sql, "select imported_source_record_id from quiz_attempts where id=1")).isEqualTo(10L);
            assertThat(singleText(sql, "select origin_type from quiz_attempts where id=2")).isEqualTo("QUIZ_SESSION_ITEM");
            assertThat(singleLong(sql, "select quiz_session_item_id from quiz_attempts where id=2")).isEqualTo(20L);
            assertThat(singleText(sql, "select origin_type from quiz_attempts where id=3")).isEqualTo("LEGACY_UNKNOWN");
            assertThat(singleText(sql, "select origin_type from quiz_attempts where id=4")).isEqualTo("LEGACY_UNKNOWN");
            assertThat(singleText(sql, "select origin_type from quiz_attempts where id=5")).isEqualTo("LEGACY_UNKNOWN");
            assertThat(singleInt(sql, "select count(*) from quiz_attempts where id in (3,4,5) and imported_source_record_id is null and quiz_session_item_id is null")).isEqualTo(3);
            assertThat(singleText(sql, "select origin_type from quiz_attempts where id=6")).isEqualTo("IMPORTED_SOURCE");
            assertThat(singleLong(sql, "select imported_source_record_id from quiz_attempts where id=6")).isEqualTo(102L);
            assertThat(singleText(sql, "select origin_type from quiz_attempts where id=7")).isEqualTo("QUIZ_SESSION_ITEM");
            assertThat(singleLong(sql, "select quiz_session_item_id from quiz_attempts where id=7")).isEqualTo(103L);
            assertThat(singleText(sql, "select origin_type from quiz_attempts where id=8")).isEqualTo("IMPORTED_SOURCE");
            assertThat(singleLong(sql, "select imported_source_record_id from quiz_attempts where id=8")).isEqualTo(104L);
            assertThatThrownBy(() -> sql.executeUpdate(
                    "update quiz_attempts set origin_type='IMPORTED_SOURCE', imported_source_record_id=10, quiz_session_item_id=20 where id=3"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }

    @Test
    void v3BackfillsExistingSourcesAsUnknownAndPreservesPublishedContent() throws Exception {
        String url = databaseUrl("source_rights_backfill");
        Flyway v2 = Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration/h2")
                .target("2")
                .cleanDisabled(true)
                .load();
        v2.migrate();

        try (Connection connection = connection(url); Statement sql = connection.createStatement()) {
            sql.executeUpdate("insert into content_sources (id, source_ref, display_name) values (1, 'existing-source', 'Existing Source')");
            sql.executeUpdate("insert into content_items (id, published, slug, source_ref, type, review_status) values (1, true, 'existing-published', 'existing-source', 'WORD', 'APPROVED')");
        }

        Flyway upgraded = flyway(url, false);
        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(5);
        assertThat(upgraded.migrate().migrationsExecuted).isZero();

        try (Connection connection = connection(url); Statement sql = connection.createStatement()) {
            assertThat(singleText(sql, "select rights_status from content_sources where id=1"))
                    .isEqualTo("UNKNOWN");
            assertThat(singleInt(sql, "select attribution_required from content_sources where id=1"))
                    .isZero();
            assertThat(singleInt(sql, "select count(*) from content_items where id=1 and published=true"))
                    .isEqualTo(1);
            sql.executeUpdate("insert into content_sources (id, source_ref, display_name) values (2, 'new-source', 'New Source')");
            assertThat(singleText(sql, "select rights_status from content_sources where id=2"))
                    .isEqualTo("UNKNOWN");
        }
    }

    @Test
    void mysqlV3MigrationIsAdditiveAndUsesConservativeDefaults() throws Exception {
        String migration = new ClassPathResource("db/migration/mysql/V3__add_content_source_rights.sql")
                .getContentAsString(StandardCharsets.UTF_8)
                .toLowerCase();

        assertThat(migration).contains("alter table content_sources")
                .contains("rights_status varchar(32) not null default 'unknown'")
                .contains("rights_reviewed_at datetime(6) null")
                .contains("rights_review_note varchar(2000) null")
                .contains("attribution_required bit not null default b'0'")
                .doesNotContain("drop ")
                .doesNotContain("delete ")
                .doesNotContain("update content_sources")
                .doesNotContain("allowed'");
    }

    @Test
    void v4AddsImmutableBatchManifestWithoutChangingExistingContent() throws Exception {
        String url = databaseUrl("release_batch_history");
        Flyway v3 = Flyway.configure().dataSource(url, "sa", "")
                .locations("classpath:db/migration/h2").target("3").cleanDisabled(true).load();
        v3.migrate();
        try (Connection connection = connection(url); Statement sql = connection.createStatement()) {
            sql.executeUpdate("insert into user_accounts (id, joined_at, display_name, login_id, password_hash, role) values (1, current_timestamp, 'Admin', 'batch-admin', 'hash', 'ADMIN')");
            sql.executeUpdate("insert into user_accounts (id, joined_at, display_name, login_id, password_hash, role) values (2, current_timestamp, 'Learner', 'batch-learner', 'hash', 'USER')");
            sql.executeUpdate("insert into content_items (id, published, slug, source_ref, type, review_status) values (1, false, 'batch-content', 'source', 'WORD', 'PENDING')");
            sql.executeUpdate("insert into learner_profiles (id, experience, created_at, updated_at, user_account_id, character_key, display_name, learner_key) values (1, 0, current_timestamp, current_timestamp, 2, 'haru', 'Learner', 'batch-learner-key')");
            sql.executeUpdate("insert into learning_progress (id, consecutive_correct, lapse_count, review_count, content_item_id, learner_profile_id, last_studied_at, next_review_at, last_result, learning_state) values (1, 0, 0, 1, 1, 1, current_timestamp, current_timestamp, 'CORRECT', 'REVIEW')");
        }
        Flyway upgraded = flyway(url, false);
        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(4);
        assertThat(upgraded.migrate().migrationsExecuted).isZero();
        try (Connection connection = connection(url); Statement sql = connection.createStatement()) {
            assertThat(singleInt(sql, "select count(*) from content_items where id=1 and published=false and review_status='PENDING'")).isEqualTo(1);
            assertThat(singleInt(sql, "select count(*) from learning_progress where id=1")).isEqualTo(1);
            assertThat(tableExists(url, "content_release_batches")).isTrue();
            assertThat(tableExists(url, "content_release_batch_items")).isTrue();
        }
    }

    @Test
    void mysqlV4MigrationIsAdditive() throws Exception {
        String migration = new ClassPathResource("db/migration/mysql/V4__add_content_release_batch_history.sql")
                .getContentAsString(StandardCharsets.UTF_8).toLowerCase();
        assertThat(migration).contains("create table content_release_batches")
                .contains("create table content_release_batch_items")
                .contains("uk_content_release_batch_preview")
                .doesNotContain("drop ").doesNotContain("delete ").doesNotContain("update content_items");
    }

    @Test
    void v5UpgradesV4WithoutTouchingContentOrRightsAndIsRepeatable() throws Exception {
        String url = databaseUrl("private_staging_upgrade");
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration/h2")
                .target("4").cleanDisabled(true).load().migrate();
        try (Connection connection = connection(url); Statement sql = connection.createStatement()) {
            sql.executeUpdate("insert into content_sources (id,source_ref,display_name,rights_status,attribution_required) "
                    + "values (1,'private-source','Private','UNKNOWN',false)");
            sql.executeUpdate("insert into content_items (id,published,slug,source_ref,type,review_status) "
                    + "values (1,false,'existing-private','private-source','WORD','PENDING')");
        }
        Flyway upgrade = flyway(url, false);
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(3);
        assertThat(upgrade.migrate().migrationsExecuted).isZero();
        try (Connection connection = connection(url); Statement sql = connection.createStatement()) {
            assertThat(singleInt(sql, "select count(*) from content_items where id=1 and published=false and review_status='PENDING'"))
                    .isEqualTo(1);
            assertThat(singleText(sql, "select rights_status from content_sources where id=1")).isEqualTo("UNKNOWN");
            assertThat(tableExists(url, "private_apkg_notes")).isTrue();
        }
    }

    @Test
    void mysqlV5IsPrivateAndAdditive() throws Exception {
        String migration = new ClassPathResource("db/migration/mysql/V5__add_private_apkg_staging.sql")
                .getContentAsString(StandardCharsets.UTF_8).toLowerCase();
        assertThat(migration).contains("create table private_apkg_notes", "uk_private_apkg_note")
                .doesNotContain("drop ").doesNotContain("delete ").doesNotContain("alter table content_items")
                .doesNotContain("update content_sources");
    }

    @Test
    void v6UpgradesV5WithoutTouchingExistingContentOrStagingAndIsRepeatable() throws Exception {
        String url = databaseUrl("normalized_candidate_upgrade");
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration/h2")
                .target("5").cleanDisabled(true).load().migrate();
        try (Connection connection = connection(url); Statement sql = connection.createStatement()) {
            sql.executeUpdate("insert into content_sources (id,source_ref,display_name,rights_status,attribution_required) "
                    + "values (1,'private-source','Private','UNKNOWN',false)");
            sql.executeUpdate("insert into content_items (id,published,slug,source_ref,type,review_status) "
                    + "values (1,false,'existing-private','private-source','WORD','PENDING')");
            sql.executeUpdate("insert into private_apkg_notes (id,source_ref,source_file,source_version,source_note_id,"
                    + "model_id,note_type,category,anki_guid,deck_paths,card_metadata,tags,field_names,field_values,"
                    + "normalized_values,audio_reference_count,extracted_at) values (1,'private-source','deck.apkg','2.1.1',"
                    + "1,1,'note','VOCABULARY','guid-1','[]','[]','','[]','[]','{}',0,current_timestamp)");
        }
        Flyway upgrade = flyway(url, false);
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(2);
        assertThat(upgrade.migrate().migrationsExecuted).isZero();
        try (Connection connection = connection(url); Statement sql = connection.createStatement()) {
            assertThat(singleInt(sql, "select count(*) from content_items where id=1 and published=false and review_status='PENDING'"))
                    .isEqualTo(1);
            assertThat(singleInt(sql, "select count(*) from private_apkg_notes where id=1")).isEqualTo(1);
            assertThat(tableExists(url, "normalized_content_candidates")).isTrue();
            sql.executeUpdate("insert into normalized_content_candidates (id,candidate_type,source_ref,source_note_id,"
                    + "source_identity_key,quality_state,normalized_at) values (1,'VOCABULARY','private-source',1,"
                    + "'E-1','CLEAN',current_timestamp)");
            assertThat(singleInt(sql, "select count(*) from normalized_content_candidates where id=1")).isEqualTo(1);
        }
    }

    @Test
    void mysqlV6IsAdditiveAndDoesNotTouchProductionOrStaging() throws Exception {
        String migration = new ClassPathResource("db/migration/mysql/V6__add_normalized_content_candidates.sql")
                .getContentAsString(StandardCharsets.UTF_8).toLowerCase();
        assertThat(migration).contains("create table normalized_content_candidates", "uk_normalized_candidate_snapshot",
                        "create table normalized_candidate_warnings", "create table normalized_candidate_extra_fields",
                        "create table normalized_vocabulary_candidates", "create table normalized_vocabulary_candidate_meanings",
                        "create table normalized_vocabulary_candidate_examples", "create table normalized_grammar_candidates",
                        "create table normalized_grammar_candidate_confusable_patterns")
                .doesNotContain("drop ").doesNotContain("delete ")
                .doesNotContain("alter table content_items").doesNotContain("alter table private_apkg_notes")
                .doesNotContain("update content_sources").doesNotContain("update content_items");
    }

    @Test
    void v7UpgradesV6WithoutTouchingExistingCandidatesAndIsRepeatable() throws Exception {
        String url = databaseUrl("normalized_candidate_match_upgrade");
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration/h2")
                .target("6").cleanDisabled(true).load().migrate();
        try (Connection connection = connection(url); Statement sql = connection.createStatement()) {
            sql.executeUpdate("insert into content_sources (id,source_ref,display_name,rights_status,attribution_required) "
                    + "values (1,'private-source','Private','UNKNOWN',false)");
            sql.executeUpdate("insert into content_items (id,published,slug,source_ref,type,review_status) "
                    + "values (1,false,'existing-private','private-source','WORD','PENDING')");
            sql.executeUpdate("insert into normalized_content_candidates (id,candidate_type,source_ref,source_note_id,"
                    + "source_identity_key,quality_state,normalized_at) values "
                    + "(1,'VOCABULARY','private-source',1,'E-1','CLEAN',current_timestamp),"
                    + "(2,'VOCABULARY','private-source',2,'E-2','CLEAN',current_timestamp)");
        }
        Flyway upgrade = flyway(url, false);
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(upgrade.migrate().migrationsExecuted).isZero();
        try (Connection connection = connection(url); Statement sql = connection.createStatement()) {
            assertThat(singleInt(sql, "select count(*) from content_items where id=1 and published=false and review_status='PENDING'"))
                    .isEqualTo(1);
            assertThat(singleInt(sql, "select count(*) from normalized_content_candidates")).isEqualTo(2);
            assertThat(tableExists(url, "normalized_candidate_match_pairs")).isTrue();
            assertThat(tableExists(url, "normalized_candidate_match_evidence")).isTrue();
            sql.executeUpdate("insert into normalized_candidate_match_pairs (id,left_candidate_id,right_candidate_id,"
                    + "assessment,generated_at) values (1,1,2,'POSSIBLE_DUPLICATE',current_timestamp)");
            sql.executeUpdate("insert into normalized_candidate_match_evidence (id,pair_id,position,evidence_code,"
                    + "field_name,detail) values (1,1,1,'SAME_EXPRESSION','expression','だぶる')");
            assertThat(singleInt(sql, "select count(*) from normalized_candidate_match_pairs where id=1")).isEqualTo(1);
            assertThat(singleInt(sql, "select count(*) from normalized_candidate_match_evidence where pair_id=1")).isEqualTo(1);
            assertThatThrownBy(() -> sql.executeUpdate(
                    "insert into normalized_candidate_match_pairs (id,left_candidate_id,right_candidate_id,"
                            + "assessment,generated_at) values (2,1,2,'CONFLICT',current_timestamp)"))
                    .as("duplicate (left,right) pair must be rejected by the unique constraint")
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }

    @Test
    void mysqlV7IsAdditiveAndDoesNotTouchProductionOrCandidates() throws Exception {
        String migration = new ClassPathResource("db/migration/mysql/V7__add_normalized_candidate_match_analysis.sql")
                .getContentAsString(StandardCharsets.UTF_8).toLowerCase();
        assertThat(migration).contains("create table normalized_candidate_match_pairs", "uk_normalized_candidate_match_pair",
                        "create table normalized_candidate_match_evidence", "uk_normalized_candidate_match_evidence_position")
                .doesNotContain("drop ").doesNotContain("delete ")
                .doesNotContain("alter table content_items").doesNotContain("alter table private_apkg_notes")
                .doesNotContain("alter table normalized_content_candidates")
                .doesNotContain("update content_sources").doesNotContain("update content_items")
                .doesNotContain("update normalized_content_candidates");
    }

    private int singleInt(Statement sql, String query) throws Exception {
        try (ResultSet result = sql.executeQuery(query)) { result.next(); return result.getInt(1); }
    }

    private long singleLong(Statement sql, String query) throws Exception {
        try (ResultSet result = sql.executeQuery(query)) { result.next(); return result.getLong(1); }
    }

    private String singleText(Statement sql, String query) throws Exception {
        try (ResultSet result = sql.executeQuery(query)) { result.next(); return result.getString(1); }
    }

    private Flyway flyway(String url, boolean baselineOnMigrate) {
        return Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration/h2")
                .baselineOnMigrate(baselineOnMigrate)
                .baselineVersion("1")
                .cleanDisabled(true)
                .load();
    }

    private String databaseUrl(String scenario) {
        return "jdbc:h2:mem:flyway_" + scenario + "_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    }

    private Connection connection(String url) throws Exception {
        return DriverManager.getConnection(url, "sa", "");
    }

    private boolean tableExists(String url, String tableName) throws Exception {
        try (Connection connection = connection(url);
                ResultSet tables = connection.getMetaData().getTables(null, "public", tableName, null)) {
            return tables.next();
        }
    }

    private boolean columnExists(String url, String tableName, String columnName) throws Exception {
        try (Connection connection = connection(url);
                ResultSet columns = connection.getMetaData().getColumns(null, "public", tableName, columnName)) {
            return columns.next();
        }
    }

}
