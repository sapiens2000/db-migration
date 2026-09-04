package com.dbmigration.job;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class JobRepository {

    private final DataSource dataSource;

    public JobRepository(DataSource dataSource) {
        this.dataSource = dataSource;
        createTableIfNotExists();
    }

    private void createTableIfNotExists() {
        String ddl = """
                CREATE TABLE IF NOT EXISTS job_status (
                    job_id UUID PRIMARY KEY,
                    status VARCHAR(20) NOT NULL,
                    started_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    error_message TEXT
                )
                """;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(ddl);
        } catch (SQLException e) {
            throw new IllegalStateException("job_status 테이블 생성 실패", e);
        }
    }

    public UUID createJob() {
        UUID jobId = UUID.randomUUID();
        Instant now = Instant.now();
        String sql = """
                INSERT INTO job_status (job_id, status, started_at, updated_at, error_message)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, jobId);
            ps.setString(2, JobStatus.RUNNING.name());
            ps.setTimestamp(3, Timestamp.from(now));
            ps.setTimestamp(4, Timestamp.from(now));
            ps.setString(5, null);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("job 생성 실패", e);
        }
        return jobId;
    }

    // 정상 종료라 실패 원인이 없으므로 error_message는 갱신하지 않는다.
    public void markCompleted(UUID jobId) {
        String sql = "UPDATE  job_status SET status = ?, updated_at = ? WHERE job_id = ?";
        try(Connection conn = dataSource.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)
        ){
            ps.setString(1, JobStatus.COMPLETED.name());
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setObject(3, jobId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }


    public void markFailed(UUID jobId, String errorMessage) {
        String sql = "UPDATE job_status SET status = ?, updated_at = ?, error_message = ? WHERE job_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, JobStatus.FAILED.name());
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setString(3, errorMessage);
            ps.setObject(4, jobId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("job 실패 처리 실패", e);
        }
    }

    public Optional<JobRecord> findById(UUID jobId) {
        String sql = "SELECT job_id, status, started_at, updated_at, error_message FROM job_status WHERE job_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, jobId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new JobRecord(
                        (UUID) rs.getObject("job_id"),
                        JobStatus.valueOf(rs.getString("status")),
                        rs.getTimestamp("started_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant(),
                        rs.getString("error_message")
                ));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("job 조회 실패", e);
        }
    }
}