package com.dbmigration.migration;

import com.dbmigration.checkpoint.CheckpointStore;
import com.dbmigration.chunk.ChunkProcessor;
import com.dbmigration.job.JobRepository;
import com.dbmigration.reader.StreamingReader;
import com.dbmigration.retry.RetryPolicy;

import javax.sql.DataSource;
import java.util.UUID;

public class MigrationJob {

    // TODO: 설계는 package-info.java 참고.
    //   필드: jobId, jobRepository, checkpointStore, sourceDataSource, targetDataSource,
    //   sourceTable, targetTable, keyColumn, chunkSize, retryPolicy
    //   생성자는 이 필드들을 그대로 받아서 저장 (jobId 생성/재사용은 호출자 책임 - 여기서
    //   JobRepository.createJob()을 부르지 않는다)

    // TODO: run()
    //   1. checkpointStore.findLastProcessedKey(jobId)로 resumeAfterKey 조회
    //   2. new StreamingReader(sourceDataSource, sourceTable, keyColumn,
    //      resumeAfterKey.orElse(null), chunkSize)로 재개 지점부터 커서 생성
    //   3. 그 reader로 ChunkProcessor 생성 (keyColumn도 함께 전달)
    //   4. ChunkResult.hasMore()가 false가 될 때까지 반복:
    //      - chunkProcessor.processNextChunk() 호출
    //      - result.lastKey()가 있으면 checkpointStore.save(jobId, lastKey)로 갱신
    //   5. 정상 종료 시 jobRepository.markCompleted(jobId)
    //   6. 재시도 소진 후 예외가 올라오면 jobRepository.markFailed(jobId, e.getMessage())
    //      호출 후 예외를 다시 던진다 (호출자에게 실패를 숨기지 않는다)
}
