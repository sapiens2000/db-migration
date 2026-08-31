package com.dbmigration.retry;

import java.sql.SQLException;

@FunctionalInterface
public interface RetryableAction {
    void run() throws SQLException;
}
