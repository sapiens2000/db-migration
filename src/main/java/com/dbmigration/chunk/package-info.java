/**
 * reader와 writer를 오케스트레이션하는 청크 처리 로직.
 * <p>
 * 청크 단위로 트랜잭션 경계를 설정하고, 전체 데이터를 하나의 트랜잭션으로 묶지 않는다.
 * <p>
 * TODO: ChunkProcessor 구현
 */
package com.dbmigration.chunk;
