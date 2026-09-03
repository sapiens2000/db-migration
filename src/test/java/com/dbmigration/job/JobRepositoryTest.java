package com.dbmigration.job;

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

class JobRepositoryTest {

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
            stmt.execute("DROP TABLE IF EXISTS job_status");
        }
        ((HikariDataSource) dataSource).close();
    }

    @Test
    void markCompleted을_호출하면_상태가_COMPLETED로_바뀐다() {
        JobRepository jobRepository = new JobRepository(dataSource);
        UUID jobId = jobRepository.createJob();

        jobRepository.markCompleted(jobId);

        Optional<JobRecord> record = jobRepository.findById(jobId);
        assertThat(record).isPresent();
        assertThat(record.get().status()).isEqualTo(JobStatus.COMPLETED);
    }
}
