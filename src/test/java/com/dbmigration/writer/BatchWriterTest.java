package com.dbmigration.writer;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BatchWriterTest {

    private static final String TABLE_NAME = "batch_writer_test_table";

    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/target_db");
        config.setUsername("postgres");
        config.setPassword("password");
        config.setDriverClassName("org.postgresql.Driver");
        dataSource = new HikariDataSource(config);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS " + TABLE_NAME);
            stmt.execute("CREATE TABLE " + TABLE_NAME + " (id BIGINT PRIMARY KEY, name VARCHAR(50))");
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
    void 행_2건짜리_청크를_쓰면_테이블에_그_2건이_그대로_들어간다() throws Exception {
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("id", 1L);
        row1.put("name", "alice");

        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("id", 2L);
        row2.put("name", "bob");

        List<Map<String, Object>> chunk = List.of(row1, row2);

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            BatchWriter.writeChunk(conn, TABLE_NAME, chunk);
            conn.commit();
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT id, name FROM " + TABLE_NAME + " ORDER BY id");
             ResultSet rs = ps.executeQuery()) {

            assertThat(rs.next()).isTrue();
            assertThat(rs.getLong("id")).isEqualTo(1L);
            assertThat(rs.getString("name")).isEqualTo("alice");

            assertThat(rs.next()).isTrue();
            assertThat(rs.getLong("id")).isEqualTo(2L);
            assertThat(rs.getString("name")).isEqualTo("bob");

            assertThat(rs.next()).isFalse();
        }
    }
}
