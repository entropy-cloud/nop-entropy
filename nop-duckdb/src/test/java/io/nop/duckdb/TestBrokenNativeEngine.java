package io.nop.duckdb;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Test-only engine whose driver-touch boundary simulates the DuckDBNative static-init
 * failure (surfaces as ExceptionInInitializerError, an Error) - {@code openConnection}
 * must translate it into the explicit native-load-failed error, never a bare Error.
 */
public class TestBrokenNativeEngine extends DuckDbEngine {
    @Override
    protected Connection newJdbcConnection(String url) throws SQLException {
        throw new ExceptionInInitializerError(new RuntimeException("simulated missing duckdb native"));
    }
}
