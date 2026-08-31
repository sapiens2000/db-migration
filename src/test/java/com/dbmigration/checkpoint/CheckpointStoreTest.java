package com.dbmigration.checkpoint;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CheckpointStoreTest {

    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/target_db");
        config.setUsername("postgres");
        config.setPassword("password");
        config.setDriverClassName("org.postgresql.Driver");
        dataSource = new HikariDataSource(config);
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS checkpoint");
        }
        ((HikariDataSource) dataSource).close();
    }

    @Test
    void 존재하지_않는_job의_체크포인트를_조회하면_빈값을_반환한다() {
        CheckpointStore checkpointStore = new CheckpointStore(dataSource);

        Optional<Long> result = checkpointStore.findLastProcessedKey(UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    @Test
    void 체크포인트를_저장하면_그대로_조회된다() {
        CheckpointStore checkpointStore = new CheckpointStore(dataSource);
        UUID jobId = UUID.randomUUID();

        checkpointStore.save(jobId, 42L);

        assertThat(checkpointStore.findLastProcessedKey(jobId)).contains(42L);
    }

    @Test
    void 같은_job에_다시_저장하면_기존_값이_갱신된다() {
        CheckpointStore checkpointStore = new CheckpointStore(dataSource);
        UUID jobId = UUID.randomUUID();

        checkpointStore.save(jobId, 10L);
        checkpointStore.save(jobId, 20L);

        assertThat(checkpointStore.findLastProcessedKey(jobId)).contains(20L);
    }
}
