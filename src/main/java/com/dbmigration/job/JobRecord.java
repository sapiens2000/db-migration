package com.dbmigration.job;

import java.time.Instant;
import java.util.UUID;

public record JobRecord(
        UUID jobId,
        JobStatus status,
        Instant startedAt,
        Instant updatedAt,
        String errorMessage
) {
}