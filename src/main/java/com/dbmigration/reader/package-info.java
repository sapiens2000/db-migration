/**
 * 소스 DB에서 대용량 데이터를 스트리밍 방식으로 읽어온다.
 * <p>
 * JDBC Statement.setFetchSize()를 적용해 ResultSet 전체를 메모리에 올리지 않고
 * 커서 방식으로 순회하며, N건씩 묶어 반환한다.
 * <p>
 * TODO: StreamingReader 구현
 */
package com.dbmigration.reader;
