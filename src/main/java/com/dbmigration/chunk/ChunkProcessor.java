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

    // TODO: MigrationJob이 체크포인트를 갱신하려면 "이번 청크의 마지막 키 값"이 필요하다.
    //   ChunkResult record(boolean hasMore, Object lastKey) 추가하고 processNextChunk()의
    //   리턴 타입을 boolean -> ChunkResult로 변경.

    private final StreamingReader streamingReader;
    private final String targetTableName;
    private final DataSource targetDataSource;
    private final RetryPolicy retryPolicy;

    // TODO: 생성자에 keyColumn 파라미터 추가 - 청크의 마지막 행(Map<String, Object>)에서
    //   keyColumn으로 값을 꺼내 ChunkResult.lastKey로 리턴하기 위함.
    public ChunkProcessor(StreamingReader reader, DataSource targetDataSource, String targetTableName, RetryPolicy retryPolicy) {
        this.streamingReader = reader;
        this.targetDataSource = targetDataSource;
        this.targetTableName = targetTableName;
        this.retryPolicy = retryPolicy;
    }

    // TODO: 리턴 타입을 ChunkResult로 변경.
    //   - streamingReader.hasNext()가 false면 ChunkResult(false, null) 리턴 (지금의 `return false`)
    //   - 청크 처리 성공 후, chunk의 마지막 Map에서 keyColumn 값을 꺼내
    //     ChunkResult(true, lastKey) 리턴 (지금의 `return true`)
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