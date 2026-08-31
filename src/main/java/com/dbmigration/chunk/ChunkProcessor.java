package com.dbmigration.chunk;

import com.dbmigration.reader.StreamingReader;
import com.dbmigration.retry.RetryPolicy;
import com.dbmigration.writer.BatchWriter;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class ChunkProcessor {

    private final StreamingReader streamingReader;
    private final String targetTableName;
    private final DataSource targetDataSource;
    private final RetryPolicy retryPolicy;

    public ChunkProcessor(StreamingReader reader, DataSource targetDataSource, String targetTableName, RetryPolicy retryPolicy) {
        this.streamingReader = reader;
        this.targetDataSource = targetDataSource;
        this.targetTableName = targetTableName;
        this.retryPolicy = retryPolicy;
    }

    public boolean processNextChunk() throws SQLException, InterruptedException {
        if(!streamingReader.hasNext()) return false;

        List<Map<String, Object>> chunk = streamingReader.nextChunk();

        // 재시도 여부/횟수/백오프 판단은 RetryPolicy에 위임한다 - ChunkProcessor는
        // "커넥션 열고 쓰고 커밋/롤백하고 닫는" 한 번의 시도만 알면 된다.
        retryPolicy.execute(() -> {
            Connection conn =  targetDataSource.getConnection();
            try {
                conn.setAutoCommit(false);
                BatchWriter.writeChunk(conn, targetTableName, chunk);
                conn.commit();
            }catch(SQLException e) {
                conn.rollback();
                throw e;
            }finally{
                conn.close();
            }
        });

        return true;
    }
}