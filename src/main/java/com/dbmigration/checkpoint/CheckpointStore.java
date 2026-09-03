package com.dbmigration.checkpoint;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class CheckpointStore {

    // SQLException을 IllegalStateException으로 감싸는 이유: 커넥션/스키마 문제는
    // 호출부에서 복구 가능한 상황이 아니라 인프라 장애이므로, 체크 예외로 처리를
    // 강제하기보다 즉시 애플리케이션을 중단시키는 편이 낫다 (JobRepository와 동일 판단).
    private final DataSource dataSource;

    public CheckpointStore(DataSource dataSource) {
        this.dataSource = dataSource;
        createTableIfNotExists();
    }

    private void createTableIfNotExists() {
        String ddl = """
               CREATE TABLE IF NOT EXISTS checkpoint (
                   job_id UUID PRIMARY KEY,
                   last_processed_key BIGINT NOT NULL,
                   updated_at TIMESTAMP NOT NULL
               )
                """;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(ddl);
        } catch (SQLException e) {
            throw new IllegalStateException("checkpoint 테이블 생성 실패", e);
        }
    }

    public void save(UUID jobId, long lastProcessedKey) {
        // ON CONFLICT/ON DUPLICATE KEY 같은 벤더별 upsert 문법 대신 UPDATE 후 영향받은
        // 행이 0건이면 INSERT하는 방식을 쓴다 - MySQL/PostgreSQL 어느 쪽으로도 이식 가능한
        // SQL을 유지하기 위함 (JobRepository와 동일 방침).
        String update = """
                UPDATE checkpoint SET last_processed_key = ?, updated_at = ? WHERE job_id = ?
                """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(update)) {
            pstmt.setLong(1, lastProcessedKey);
            pstmt.setTimestamp(2, Timestamp.from(Instant.now()));
            pstmt.setObject(3, jobId);
            int updated = pstmt.executeUpdate();

            if(updated == 0) {
                String insert = """
                    INSERT INTO checkpoint (job_id, last_processed_key, updated_at) VALUES (?, ?, ?)
                    """;
                try(PreparedStatement pstmt2 = conn.prepareStatement(insert)) {
                    pstmt2.setObject(1, jobId);
                    pstmt2.setLong(2, lastProcessedKey);
                    pstmt2.setTimestamp(3, Timestamp.from(Instant.now()));
                    pstmt2.execute();
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("checkpoint 테이블 저장 실패", e);
        }
    }

    public Optional<Long> findLastProcessedKey(UUID jobId) {
        String select = """
                    SELECT last_processed_key FROM checkpoint WHERE job_id = ?
                    """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(select)) {
            pstmt.setObject(1, jobId);

            try(ResultSet rs = pstmt.executeQuery()) {
                if(!rs.next()){
                    return Optional.empty();
                }
                return Optional.of(rs.getLong(1));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("checkpoint 테이블 조회 실패", e);
        }
    }
}
