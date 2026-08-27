/**
 * 타겟 DB에 청크 단위로 데이터를 batch insert한다.
 * <p>
 * JDBC addBatch/executeBatch를 사용해 N건씩 모아서 쓴다.
 * <p>
 * TODO: BatchWriter 구현
 */
package com.dbmigration.writer;
