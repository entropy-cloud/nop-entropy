package io.nop.duckdb;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Test-only engine with a tight memory budget and an unusable spill location (a regular
 * file in place of the temp directory, so any spill write fails with ENOTDIR on every
 * platform). The limits are applied per connection inside the driver-touch boundary:
 * field injection would overwrite constructor-set values with the empty @cfg defaults.
 */
public class TestSpillEngine extends DuckDbEngine {
    private final String unusableSpillPath;

    public TestSpillEngine() throws IOException {
        this.unusableSpillPath = Files.createTempFile("duckdb-wi4-notdir", ".txt").toString();
    }

    @Override
    protected Connection newJdbcConnection(String url) throws SQLException {
        Connection conn = super.newJdbcConnection(url);
        try (Statement st = conn.createStatement()) {
            st.execute("SET memory_limit='64MB'");
            st.execute("SET temp_directory='" + unusableSpillPath + "'");
        }
        return conn;
    }
}
