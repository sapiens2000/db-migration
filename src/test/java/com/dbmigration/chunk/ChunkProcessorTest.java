package com.dbmigration.chunk;

import com.dbmigration.reader.StreamingReader;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class ChunkProcessorTest {

    private static final String TABLE_NAME = "chunk_processor_test_table";

    private DataSource sourceDataSource;
    private DataSource targetDataSource;

    @BeforeEach
    void setUp() throws Exception {
        HikariConfig sourceConfig = new HikariConfig();
        sourceConfig.setJdbcUrl("jdbc:mysql://localhost:3306/source_db");
        sourceConfig.setUsername("root");
        sourceConfig.setPassword("password");
        sourceConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
        sourceDataSource = new HikariDataSource(sourceConfig);

        HikariConfig targetConfig = new HikariConfig();
        targetConfig.setJdbcUrl("jdbc:postgresql://localhost:5432/target_db");
        targetConfig.setUsername("postgres");
        targetConfig.setPassword("password");
        targetConfig.setDriverClassName("org.postgresql.Driver");
        targetDataSource = new HikariDataSource(targetConfig);

        try (Connection conn = sourceDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS " + TABLE_NAME);
            stmt.execute("CREATE TABLE " + TABLE_NAME + " (id BIGINT PRIMARY KEY, name VARCHAR(50))");
            stmt.execute("INSERT INTO " + TABLE_NAME + " (id, name) VALUES (1, 'alice')");
        }

        try (Connection conn = targetDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS " + TABLE_NAME);
            stmt.execute("CREATE TABLE " + TABLE_NAME + " (id BIGINT PRIMARY KEY, name VARCHAR(50))");
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = sourceDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS " + TABLE_NAME);
        }
        try (Connection conn = targetDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS " + TABLE_NAME);
        }
        ((HikariDataSource) sourceDataSource).close();
        ((HikariDataSource) targetDataSource).close();
    }

    @Test
    void 행이_1건인_청크를_처리하면_target에_그_행이_들어가고_true를_반환하며_다음_호출은_false를_반환한다() throws Exception {
        try (StreamingReader reader = new StreamingReader(sourceDataSource, TABLE_NAME, 10)) {
            ChunkProcessor chunkProcessor = new ChunkProcessor(reader, targetDataSource, TABLE_NAME, 3);

            assertThat(chunkProcessor.processNextChunk()).isTrue();

            try (Connection conn = targetDataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT id, name FROM " + TABLE_NAME);
                 ResultSet rs = ps.executeQuery()) {

                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong("id")).isEqualTo(1L);
                assertThat(rs.getString("name")).isEqualTo("alice");
                assertThat(rs.next()).isFalse();
            }

            assertThat(chunkProcessor.processNextChunk()).isFalse();
        }
    }

    @Test
    void PK_중복처럼_영구적_오류가_나면_재시도하지_않고_즉시_예외를_던진다() throws Exception {
        try (Connection conn = targetDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO " + TABLE_NAME + " (id, name) VALUES (1, 'existing')");
        }

        try (StreamingReader reader = new StreamingReader(sourceDataSource, TABLE_NAME, 10)) {
            ChunkProcessor chunkProcessor = new ChunkProcessor(reader, targetDataSource, TABLE_NAME, 3);

            assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                    assertThatThrownBy(chunkProcessor::processNextChunk).isInstanceOf(SQLException.class));
        }
    }
}