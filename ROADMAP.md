# db-migration

## 프로젝트 개요

특정 DB에서 다른 DB로 대용량 데이터를 안정적으로 이관하는 기능을, 최소 기능(MVP)부터 시작해 점진적으로 확장해나가는 포트폴리오 프로젝트.

- 핵심 어필 포인트: **대용량 데이터 처리 성능**(스트리밍/배치, 메모리 관리) + **안정성**(트랜잭션, 재시도, 실패 복구)

## 범위 결정

| 항목 | 결정 |
|---|---|
| 소스/타겟 | MySQL → PostgreSQL 한 쌍부터 시작. 다중 DB 조합 지원, CSV 확장은 Phase 2 이후 |
| 기술 스택 | Spring Boot 3.3.4, Java 17, Gradle |
| 청크/재시도/재시작 로직 | Spring Batch를 바로 쓰지 않고 **직접 구현** → 이후 Spring Batch로 전환하며 비교. 원리 이해가 우선 |

## MVP 범위 (Phase 1)

**목표**: MySQL → PostgreSQL 단방향 마이그레이션을, 대용량에서도 메모리 문제 없이 안정적으로 끝낼 수 있는 최소 기능

핵심 기능 4가지:

1. **스트리밍 읽기** — JDBC `fetchSize` 설정으로 전체 결과를 메모리에 올리지 않고 커서 방식으로 순회
2. **청크 단위 쓰기** — N건씩 모아서 batch insert, 청크 단위로 커밋 (전체를 하나의 트랜잭션으로 묶지 않음)
3. **재시도** — 청크 쓰기 실패 시 일시적 오류(커넥션 끊김, 데드락 등)와 영구적 오류(제약조건 위반 등)를 구분해서, 전자만 지수 백오프로 N회 재시도
4. **체크포인트 & 재시작** — 마지막으로 성공한 청크 위치를 별도 테이블에 기록해두고, Job이 중간에 죽어도 처음부터가 아니라 그 지점부터 재개

## 패키지 구조

```
config      - 소스/타겟 DataSource 빈 설정 (인프라, 기 구현됨)
job         - Job 생명주기/상태 관리 (RUNNING, FAILED, COMPLETED)
reader      - 소스 DB 스트리밍 리더
writer      - 타겟 DB 배치 라이터
chunk       - 읽기→쓰기 오케스트레이션 (청크 단위로 묶어서 처리)
retry       - 재시도 정책 (일시적/영구적 오류 판별, 백오프)
checkpoint  - 진행 상황 저장 및 재시작 지점 조회
common      - 공통 예외, 유틸
```

특정 스키마에 종속되지 않는 범용 이관 로직이 핵심이라, DB 컬럼 매핑 같은 도메인 로직 없이 "행 단위 복사"에만 집중하는 게 MVP를 깔끔하게 만드는 포인트.

## Phase 1 TODO 체크리스트

- [x] `JobRepository` — job_status 테이블 설계 및 상태 전이 (CRUD)
- [x] `StreamingReader` — JDBC `Statement.setFetchSize()` 적용, ResultSet 순회하며 N건씩 List로 반환
  - 미해결: MySQL 드라이버가 양수 fetchSize로 실제 서버 커서 스트리밍을 하는지 확인 필요
- [x] `BatchWriter` — JDBC batch insert (`addBatch`/`executeBatch`)
- [ ] `ChunkProcessor` — reader → writer 오케스트레이션, 청크 단위 트랜잭션 경계 설정
- [ ] `RetryPolicy` — 예외 타입별 재시도 가능 여부 판단 + 지수 백오프
- [ ] `CheckpointStore` — 마지막 성공 오프셋 저장/조회, 재시작 시 그 지점부터 재개
- [ ] 통합 테스트: 중간에 강제로 실패시켰을 때 재시작이 정확히 그 지점부터 이어지는지 검증

## Phase 2 이후 (MVP 완료 후 재논의)

- 다중 DB 조합을 유연하게 지원 (드라이버 추상화)
- CSV → DB 이관 확장
- 직접 구현한 청크/재시도/재시작 로직을 Spring Batch로 전환해보며 비교
- 한 테이블 내에서 청크 단위 병렬 처리 (멀티스레딩) 지원

## 이미 만들어둔 것

- Gradle 프로젝트 골격 (`build.gradle`, `settings.gradle`)
- `application.yml` — 소스(MySQL)/타겟(PostgreSQL) 이중 DataSource 및 마이그레이션 설정 프로퍼티
- `DbMigrationApplication` — 메인 클래스
- `DataSourceConfig` — 소스/타겟 DataSource 빈 (인프라 보일러플레이트라 미리 구현)
- 패키지별 `package-info.java` — 각 패키지의 책임과 TODO를 Javadoc으로 명시
