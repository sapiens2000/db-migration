package com.dbmigration.reader;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StreamingReaderTest {

    private static final String TABLE_NAME = "streaming_reader_test_table";

    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://localhost:3306/source_db");
        config.setUsername("root");
        config.setPassword("password");
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource = new HikariDataSource(config);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS " + TABLE_NAME);
            stmt.execute("CREATE TABLE " + TABLE_NAME + " (id BIGINT PRIMARY KEY, name VARCHAR(50))");
            stmt.execute("INSERT INTO " + TABLE_NAME + " (id, name) VALUES (1, 'alice')");
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS " + TABLE_NAME);
        }
        ((HikariDataSource) dataSource).close();
    }

    @Test
    void 행이_1건인_테이블을_읽으면_첫_청크에서_그_1건이_반환되고_이후_hasNext는_false다() throws SQLException {
        try (StreamingReader reader = new StreamingReader(dataSource, TABLE_NAME, 10)) {
            assertThat(reader.hasNext()).isTrue();

            List<Map<String, Object>> chunk = reader.nextChunk();

            assertThat(chunk).hasSize(1);
            assertThat(chunk.get(0)).containsEntry("id", 1L);
            assertThat(chunk.get(0)).containsEntry("name", "alice");
            assertThat(reader.hasNext()).isFalse();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void 행이_3건이고_청크크기가_2일때_첫_청크는_2건_두번째_청크는_1건_반환된다() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO " + TABLE_NAME + " (id, name) VALUES (2, 'bob'), (3, 'carol')");
        }

        try (StreamingReader reader = new StreamingReader(dataSource, TABLE_NAME, 2)) {
            List<Map<String, Object>> firstChunk = reader.nextChunk();
            assertThat(firstChunk).hasSize(2);
            assertThat(reader.hasNext()).isTrue();

            List<Map<String, Object>> secondChunk = reader.nextChunk();
            assertThat(secondChunk).hasSize(1);
            assertThat(reader.hasNext()).isFalse();
        }
    }

    @Test
    void 빈_테이블을_읽으면_hasNext는_false이고_nextChunk는_빈_리스트를_반환한다() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM " + TABLE_NAME);
        }

        try (StreamingReader reader = new StreamingReader(dataSource, TABLE_NAME, 10)) {
            assertThat(reader.hasNext()).isFalse();
            assertThat(reader.nextChunk()).isEmpty();
        }
    }
}