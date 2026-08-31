package com.dbmigration.retry;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLTransientException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryPolicyTest {

    @Test
    void 일시적_오류가_maxRetries_안에서_해소되면_결국_성공하고_액션은_성공할때까지_반복_호출된다() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);
        RetryPolicy retryPolicy = new RetryPolicy(3, 1L, 2);

        retryPolicy.execute(() -> {
            int attempt = callCount.incrementAndGet();
            if (attempt < 3) {
                throw new SQLTransientException("일시적 오류");
            }
        });

        assertThat(callCount.get()).isEqualTo(3);
    }

    @Test
    void 일시적_오류가_maxRetries를_넘게_계속되면_재시도를_멈추고_마지막_예외를_던지며_액션은_정확히_maxRetries번_호출된다() {
        AtomicInteger callCount = new AtomicInteger(0);
        RetryPolicy retryPolicy = new RetryPolicy(3, 1L, 2);

        assertThatThrownBy(() -> retryPolicy.execute(() -> {
            callCount.incrementAndGet();
            throw new SQLTransientException("계속되는 일시적 오류");
        })).isInstanceOf(SQLTransientException.class);

        assertThat(callCount.get()).isEqualTo(3);
    }

    @Test
    void 영구적_오류는_재시도하지_않고_액션이_한번만_호출된채_바로_예외가_던져진다() {
        AtomicInteger callCount = new AtomicInteger(0);
        RetryPolicy retryPolicy = new RetryPolicy(3, 1L, 2);

        assertThatThrownBy(() -> retryPolicy.execute(() -> {
            callCount.incrementAndGet();
            throw new SQLIntegrityConstraintViolationException("제약조건 위반");
        })).isInstanceOf(SQLIntegrityConstraintViolationException.class);

        assertThat(callCount.get()).isEqualTo(1);
    }
}