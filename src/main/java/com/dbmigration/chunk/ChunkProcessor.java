package com.dbmigration.chunk;

import com.dbmigration.reader.StreamingReader;
import com.dbmigration.writer.BatchWriter;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransientException;
import java.util.List;
import java.util.Map;

public class ChunkProcessor {

    private final StreamingReader streamingReader;
    private final String targetTableName;
    private final DataSource targetDataSource;
    private final int maxRetries;

    public ChunkProcessor(StreamingReader reader, DataSource targetDataSource, String targetTableName, int maxRetries) {
        this.streamingReader = reader;
        this.targetDataSource = targetDataSource;
        this.targetTableName = targetTableName;
        this.maxRetries = maxRetries;
    }

    public boolean processNextChunk() throws SQLException {
        if(!streamingReader.hasNext()) return false;

        List<Map<String, Object>> chunk = streamingReader.nextChunk();

        // 시도마다 새 커넥션을 여는 이유: 커넥션 끊김도 재시도 대상인 일시적 오류라서,
        // 같은(죽은) 커넥션을 재사용하면 재시도해도 계속 실패한다.
        // SQLException을 감싸지 않고 그대로 던지는 이유: RetryPolicy가 나중에 예외 종류를
        // 보고 재시도 여부를 판단해야 하므로 원본 예외 타입이 유지돼야 한다.
        int retries = 0;
        while(retries < maxRetries) {
            // 매번 새 connection 얻음
            Connection conn = targetDataSource.getConnection();
            conn.setAutoCommit(false);
            try{
                BatchWriter.writeChunk(conn, targetTableName, chunk);
                conn.commit();
                conn.close();
                return true;
            }catch(SQLTransientException e){
                retries++;
                conn.rollback();
                conn.close();
                if(retries == maxRetries) throw e;
                e.printStackTrace();
            }catch(SQLException e){
                conn.rollback();
                conn.close();
                throw e;
            }
        }

        return false;
    }
}