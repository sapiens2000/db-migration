package com.dbmigration.checkpoint;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;

public class CheckpointStore {

    // TODO: JobRepository 컨벤션을 따른다 - 필드에 dataSource 보관, 생성자에서
    //   createTableIfNotExists() 호출, SQLException은 IllegalStateException으로 감싸서 던짐
    //   (복구 불가능한 인프라 오류로 취급, JobRepository와 동일한 이유)
    //
    // TODO: createTableIfNotExists()
    //   CREATE TABLE IF NOT EXISTS checkpoint (
    //       job_id UUID PRIMARY KEY,
    //       last_processed_key BIGINT NOT NULL,
    //       updated_at TIMESTAMP NOT NULL
    //   )
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
        // TODO: upsert - 먼저 UPDATE 시도 (executeUpdate()가 영향받은 행 수를 리턴함을 활용)
        //   영향받은 행이 0건이면(해당 job_id가 없으면) INSERT
        //   벤더별 upsert 문법(ON CONFLICT/ON DUPLICATE KEY) 대신 이 방식을 쓰는 이유:
        //   JobRepository처럼 이식성 있는 SQL을 유지하기 위함
        String update = """
                UPDATE checkpoint SET last_processed_key = :last_processed_key WHERE job_id = :job_id
                """;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            int updated = stmt.executeUpdate(update);

            if(updated == 0) {
                String insert = """
                INSERT INTO checkpoint (:job_id, :last_processed_key, :updated_at)
                """;
                stmt.execute(insert);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("checkpoint 테이블 생성 실패", e);
        }
    }

    public Optional<Long> findLastProcessedKey(UUID jobId) {
        // TODO: job_id로 SELECT, 없으면 Optional.empty(), 있으면 Optional.of(값)
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            String select = """
                    SELECT last_processed_key FROM job_status WHERE job_id = :job_id
                    """;
            ResultSet rs = stmt.executeQuery(select);
            int rs.getLong(1);
            if(){
                Optional.of();
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new IllegalStateException("checkpoint 테이블 생성 실패", e);
        }
    }
}
