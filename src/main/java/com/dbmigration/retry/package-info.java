/**
 * 청크 쓰기 실패 시 재시도 정책을 판단한다.
 * <p>
 * 일시적 오류(커넥션 끊김, 데드락 등)와 영구적 오류(제약조건 위반 등)를 구분하여,
 * 전자에 한해 지수 백오프로 N회 재시도한다.
 * <p>
 * TODO: RetryPolicy 구현
 */
package com.dbmigration.retry;
