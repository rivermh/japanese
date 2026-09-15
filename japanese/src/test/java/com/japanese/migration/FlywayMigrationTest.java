package com.japanese.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;

class FlywayMigrationTest {

    @Test
    void emptyDatabaseMigrationIsRepeatable() throws Exception {
        String url = databaseUrl("empty");
        Flyway flyway = flyway(url, false);

        MigrateResult first = flyway.migrate();
        MigrateResult second = flyway.migrate();

        assertThat(first.migrationsExecuted).isEqualTo(2);
        assertThat(second.migrationsExecuted).isZero();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");
        assertThat(tableExists(url, "content_items")).isTrue();
        assertThat(tableExists(url, "today_study_sessions")).isTrue();
        assertThat(columnExists(url, "quiz_attempts", "origin_type")).isTrue();
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
        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(1);
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
