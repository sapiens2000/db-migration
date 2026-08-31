package com.dbmigration.retry;

import java.sql.SQLException;
import java.sql.SQLTransientException;

public class RetryPolicy {

    private final int maxRetries;
    private final long initialBackoffOfMilliseconds;
    private final long multiplier;

    public RetryPolicy(int maxRetries, long initialBackoffMillis, long multiplier) {
        this.maxRetries = maxRetries;
        this.initialBackoffOfMilliseconds = initialBackoffMillis;
        this.multiplier = multiplier;
    }

    public void execute(RetryableAction action) throws SQLException, InterruptedException {
        // SQLTransientException만 재시도하고 그 외 SQLException은 즉시 던지는 이유:
        // 제약조건 위반 같은 영구적 오류는 몇 번을 다시 시도해도 결과가 달라지지 않으므로
        // 헛되이 시간을 쓰지 않고 바로 실패를 알린다.
        // 예외를 다른 타입으로 감싸지 않는 이유: 호출자(ChunkProcessor)가 원본 예외 타입을
        // 그대로 받아야 재시도 소진 원인을 정확히 알 수 있다.
        int attempt = 1;
        while (attempt <= maxRetries) {
            try {
                action.run();
                return;
            }catch (SQLTransientException e) {
                if(attempt == maxRetries) throw e;
                long backoff = (long) (initialBackoffOfMilliseconds * Math.pow(multiplier, attempt++ - 1));
                Thread.sleep(backoff);
            }catch(SQLException e){
                throw e;
            }
        }
    }
}