package com.dbmigration.writer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BatchWriter {

    public static void writeChunk(Connection connection, String tableName, List<Map<String, Object>> chunk) throws SQLException {
        // TODO: chunk가 비어있으면 그냥 반환 (할 일 없음)
        // TODO: 컬럼명은 chunk의 첫 번째 행(Map)의 key 집합에서 가져오기
        //       - 순서가 뒤섞이면 안 되므로 Map의 key 순서가 보장되는 걸 가정 (LinkedHashMap 등)
        // TODO: "INSERT INTO {tableName} (col1, col2, ...) VALUES (?, ?, ...)" 형태의 SQL 문자열 조립
        // TODO: PreparedStatement 생성 후, chunk의 각 행마다:
        //       - 컬럼 순서대로 row.get(columnName) 값을 setObject(index, value)로 채우고
        //       - addBatch() 호출
        // TODO: 반복이 끝나면 executeBatch() 한 번 호출
        // TODO: 커밋은 여기서 하지 않는다 - 호출자(ChunkProcessor)가 트랜잭션 경계를 제어
        // TODO: SQLException은 감싸지 않고 그대로 던진다 (RetryPolicy가 원인 판단에 사용)

        if(chunk.isEmpty()) return;
        List<String> columnNames = new ArrayList<>(chunk.get(0).keySet());

        StringBuilder sb = new StringBuilder("INSERT INTO " + tableName + " (");

        for(int i=0;i<columnNames.size();i++) {

        }

        for(Object columnName : columnNames) {
            sb.append(columnName).append(",");
        }
        sb.append(") VALUES (");

        for(int i = 0; i < columnNames.size()-1; ++i) {
            sb.append("?, ");
        }
        sb.append("?)");

        try(PreparedStatement pstmt = connection.prepareStatement(sb.toString())) {
            for (Map<String, Object> row : chunk) {

                pstmt.addBatch();
            }

            pstmt.executeBatch();
        }
    }
}