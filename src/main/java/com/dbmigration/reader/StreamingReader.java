package com.dbmigration.reader;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

public class StreamingReader implements AutoCloseable {

    // application.yml의 migration.fetch-size 기본값과 맞춤. 호출자가 chunkSize를 안 정해도
    // 이 값으로 스트리밍이 동작하게 하기 위한 기본값.
    private static final int DEFAULT_CHUNK_SIZE = 1000;
    private int chunkSize;

    private Connection connection;
    private Statement statement;
    private ResultSet resultSet;

    private boolean hasNextRow;

    public StreamingReader(DataSource dataSource, String tableName, int chunkSize) {
        this(dataSource, tableName);
        try {
            statement.setFetchSize(chunkSize);
            this.chunkSize = chunkSize;
        }catch(SQLException e){
            throw new IllegalStateException(e);
        }
    }

    public StreamingReader(DataSource dataSource, String tableName) {
        // 커서를 생성자에서 미리 열어 필드에 보관해두고, hasNext()/nextChunk() 호출 사이에
        // 계속 살아있게 한다 - 커넥션 하나를 여러 메서드 호출에 걸쳐 유지해야 스트리밍이 되므로
        // try-with-resources를 쓸 수 없다.
        // SQLException은 JobRepository 컨벤션대로 IllegalStateException으로 감싸서 던진다 -
        // 커넥션/쿼리 실패는 호출자가 복구할 수 있는 상황이 아니므로 checked exception으로
        // 강제하지 않는다.
        try {
            this.connection = dataSource.getConnection();
            this.statement = connection.createStatement();
            // 확인 필요: MySQL Connector/J는 fetchSize에 양수를 주면 서버 커서 스트리밍이 아니라
            // 결과를 통째로 클라이언트로 가져온 뒤 그 크기로 나눠줄 수 있음(Integer.MIN_VALUE가
            // 필요하다는 문서가 있음). 대용량에서 실제로 메모리 문제가 없는지는 별도 확인 필요.
            this.statement.setFetchSize(DEFAULT_CHUNK_SIZE);

            // 테이블명은 내부 설정에서만 오는 값(사용자 입력이 아님)이라 SQL Injection 우려가
            // 없어 문자열 결합으로 충분하다고 판단.
            this.resultSet = statement.executeQuery("SELECT * FROM " + tableName);
            this.chunkSize = DEFAULT_CHUNK_SIZE;
        }catch(SQLException e){
            throw new IllegalStateException(e);
        }
    }


    public boolean hasNext() throws SQLException {
        // ResultSet에는 "다음 행이 있는지 미리 보는" API가 없고 rs.next() 자체가 커서를 이동시켜
        // 버린다. 그래서 next()를 부른 결과를 hasNextRow에 기억해뒀다가, 아직 그 행을 소비하지
        // 않았으면(hasNextRow == true) 커서를 또 이동시키지 않고 기억해둔 값을 그대로 돌려준다.
        // 이렇게 해야 hasNext()를 여러 번 불러도 행을 건너뛰지 않는다.
        if(hasNextRow){
            return true;
        }

        hasNextRow = resultSet.next();
        return hasNextRow;
    }

    public List<Map<String, Object>> nextChunk() throws SQLException {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        int bufferSize = 0;

        // chunkSize에서 멈추는 조건이 없으면 ResultSet이 소진될 때까지 전부 한 청크로 모아버려서
        // "N건씩 나눠 읽는다"는 목적 자체가 깨진다.
        while(hasNext() && (bufferSize++) < chunkSize) {
            ResultSetMetaData metaData = resultSet.getMetaData();
            Map<String, Object> row = new LinkedHashMap<>();
            int columnCount = metaData.getColumnCount();

            for(int i=1; i<=columnCount; i++){
                row.put(metaData.getColumnName(i), resultSet.getObject(i));
            }

            result.add(row);
            // 이 행을 이미 소비했으니, 다음 반복의 hasNext()가 진짜로 rs.next()를 호출해
            // 새 행을 확인하도록 리셋한다.
            hasNextRow = false;
        }

        return result;
    }

    @Override
    public void close() {
        // 자원 하나의 close()가 실패해도 나머지는 마저 정리해야 커넥션 누수를 막을 수 있어서,
        // 하나로 묶지 않고 각각 개별 try-catch로 감싼다.
        try {
            this.resultSet.close();
        }catch(SQLException e){
            e.printStackTrace();
        }

        try{
            this.statement.close();
        }catch(SQLException e){
            e.printStackTrace();
        }

        try {
            this.connection.close();
        }catch(SQLException e){
            e.printStackTrace();
        }
    }
}
