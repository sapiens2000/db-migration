/**
 * Job 전체 실행 흐름을 오케스트레이션한다 - 체크포인트 조회, 재개 지점 리더 생성,
 * 청크 반복 처리, Job 상태 갱신을 하나의 실행 단위로 묶는다.
 * <p>
 * {@code ChunkProcessor}가 청크 하나(read→write 트랜잭션)만 아는 것과 달리,
 * {@code MigrationJob}은 Job 시작부터 완료/실패까지의 전체 생명주기를 안다.
 *
 * <h2>설계 (2026-09-03 브레인스토밍 합의)</h2>
 *
 * <h3>호출 방식</h3>
 * {@code MigrationJob}은 {@code jobId} 생성/재사용에 관여하지 않는다 - 호출자가
 * 최초 실행이면 {@code JobRepository.createJob()}으로 새 UUID를 만들어 넘기고,
 * 재시작이면 실패했던 job의 UUID를 그대로 넘긴다. "새로 시작할지 재개할지"는
 * 정책 판단이라 호출자(추후 CLI/스케줄러, 지금은 통합 테스트) 책임으로 둔다.
 *
 * <pre>
 * MigrationJob(jobId, jobRepository, checkpointStore,
 *              sourceDataSource, targetDataSource,
 *              sourceTable, targetTable, keyColumn, chunkSize,
 *              retryPolicy)
 *     .run()
 * </pre>
 *
 * <h3>run() 흐름</h3>
 * <ol>
 *   <li>{@code checkpointStore.findLastProcessedKey(jobId)} 조회 → {@code Optional<Long> resumeAfterKey}</li>
 *   <li>{@code new StreamingReader(sourceDataSource, sourceTable, keyColumn,
 *       resumeAfterKey.orElse(null), chunkSize)} 로 재개 지점부터 커서 생성</li>
 *   <li>{@code ChunkProcessor}를 이 reader로 생성</li>
 *   <li>{@code hasMore}가 false가 될 때까지 반복:
 *     <ul>
 *       <li>{@code chunkProcessor.processNextChunk()} 호출</li>
 *       <li>{@code lastKey}가 있으면 {@code checkpointStore.save(jobId, lastKey)}</li>
 *     </ul>
 *   </li>
 *   <li>정상 종료 → {@code jobRepository.markCompleted(jobId)}</li>
 *   <li>재시도 소진 후 영구 실패 → {@code jobRepository.markFailed(jobId, e.getMessage())}
 *       하고 예외를 다시 던진다 (실패를 기록은 하되 호출자에게 숨기지 않는다)</li>
 * </ol>
 *
 * <h3>부수 변경 (이 설계로 인해 다른 클래스에 필요한 변경)</h3>
 * <ul>
 *   <li>{@code JobRepository}에 {@code markCompleted(UUID jobId)} 추가 - {@code markFailed}와
 *       대칭되는 메서드가 아직 없음</li>
 *   <li>{@code ChunkProcessor.processNextChunk()} 리턴 타입을 {@code boolean}에서
 *       {@code ChunkResult}(record: hasMore, lastKey)로 변경 - 체크포인트 갱신에 필요한
 *       "이번 청크 마지막 키"를 호출자에게 전달하기 위함</li>
 *   <li>{@code ChunkProcessor} 생성자에 {@code keyColumn} 파라미터 추가 - 청크의 마지막
 *       행에서 키 값을 꺼내려면 어떤 컬럼이 키인지 알아야 함</li>
 *   <li>{@code StreamingReader}의 재시작용 생성자(키컬럼/재개지점을 받는 오버로드)는
 *       이미 TODO 스텁으로 존재 - 이 작업의 일부로 구현됨</li>
 * </ul>
 *
 * <h3>테스트</h3>
 * <ul>
 *   <li>단위: 정상 완료 시 markCompleted 호출, 청크마다 체크포인트 저장, 실패 시
 *       markFailed 호출 검증</li>
 *   <li>통합: 마이그레이션 도중 강제 실패 → 같은 jobId로 재실행 → 정확히 그 지점부터
 *       이어지는지 검증 (Phase 1 TASKS.md 마지막 항목)</li>
 * </ul>
 *
 * TODO: MigrationJob 구현
 */
package com.dbmigration.migration;