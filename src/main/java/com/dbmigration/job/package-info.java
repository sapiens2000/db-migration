/**
 * Job의 생명주기와 상태를 관리한다.
 * <p>
 * 마이그레이션 작업 단위(Job)의 상태(RUNNING, FAILED, COMPLETED)를 추적하고,
 * job_status 테이블을 통해 영속화한다.
 * <p>
 * TODO: JobRepository - job_status 테이블 설계 및 상태 전이(CRUD) 구현
 */
package com.dbmigration.job;
