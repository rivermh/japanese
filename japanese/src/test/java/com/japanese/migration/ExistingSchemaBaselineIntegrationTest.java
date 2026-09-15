package com.japanese.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class ExistingSchemaBaselineIntegrationTest {

    private static final String URL = "jdbc:h2:mem:flyway_existing_" + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    static {
        prepareExistingSchemaWithoutHistory();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> URL);
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
    }

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void baselinePreservesExistingDataAndHibernateValidatesSchema() {
        Integer sentinelCount = jdbcClient.sql(
                "select count(*) from categories where slug = 'migration-sentinel'")
                .query(Integer.class)
                .single();
        Integer baselineCount = jdbcClient.sql(
                "select count(*) from flyway_schema_history where type = 'BASELINE' and success = true")
                .query(Integer.class)
                .single();

        assertThat(sentinelCount).isEqualTo(1);
        assertThat(baselineCount).isEqualTo(1);
    }

    private static void prepareExistingSchemaWithoutHistory() {
        try {
            Flyway.configure()
                    .dataSource(URL, "sa", "")
                    .locations("classpath:db/migration/h2")
                    .target("1")
                    .cleanDisabled(true)
                    .load()
                    .migrate();

            try (Connection connection = DriverManager.getConnection(URL, "sa", "");
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "insert into categories (name, slug) values ('보존 확인', 'migration-sentinel')");
                statement.executeUpdate("drop table flyway_schema_history");
            }
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
