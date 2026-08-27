/**
 * 마이그레이션 진행 상황을 저장하고 재시작 지점을 조회한다.
 * <p>
 * 마지막으로 성공한 청크의 오프셋을 기록해, Job이 중간에 실패해도
 * 처음부터가 아니라 그 지점부터 재개할 수 있게 한다.
 * <p>
 * TODO: CheckpointStore 구현
 */
package com.dbmigration.checkpoint;
