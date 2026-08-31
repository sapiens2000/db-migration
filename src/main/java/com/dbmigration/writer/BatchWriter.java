package com.dbmigration.writer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
public class BatchWriter {

    public static void writeChunk(Connection connection, String tableName, List<Map<String, Object>> chunk) throws SQLException {
        // 컬럼 목록을 첫 번째 행의 key 집합에서 뽑는다 - StreamingReader가 LinkedHashMap으로
        // 채워주므로 순서가 컬럼 순서와 일치한다는 걸 전제로 한다.
        // 커밋을 호출하지 않는 이유: 청크 하나의 트랜잭션 경계는 ChunkProcessor가 정하므로,
        // 여기서 임의로 커밋해버리면 재시도/체크포인트 시점과 어긋난다.
        // SQLException을 감싸지 않고 그대로 던지는 이유: RetryPolicy가 예외 종류(일시적/영구적)를
        // 보고 재시도 여부를 판단해야 하므로, 여기서 다른 예외로 바꿔버리면 그 정보가 사라진다.
        if(chunk.isEmpty()) return;
        List<String> columnNames = new ArrayList<>(chunk.get(0).keySet());

        StringBuilder sb = new StringBuilder("INSERT INTO " + tableName + " (");
        sb.append(String.join(", ", columnNames));
        sb.append(") VALUES (");
        sb.append(String.join(", ", Collections.nCopies(columnNames.size(), "?")));
        sb.append(')');

        try(PreparedStatement pstmt = connection.prepareStatement(sb.toString())) {
            for (Map<String, Object> row : chunk) {
                for(int i=0;i<columnNames.size();i++) {
                    pstmt.setObject(i+1, row.get(columnNames.get(i)));
                }

                pstmt.addBatch();
            }

            pstmt.executeBatch();
        }
    }
}