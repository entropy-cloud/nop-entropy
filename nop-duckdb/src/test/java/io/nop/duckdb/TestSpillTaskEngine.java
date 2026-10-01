package io.nop.duckdb;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Test-only engine for the WI6 deep-spill scenario: a 128MB budget (above the contiguous
 * block-allocation floor where 64MB fails, below the ~160MB sort working set, so out-of-core
 * execution is mathematically forced) and a writable spill directory that the test can poll
 * while the task runs (DuckDB deletes temp files as soon as blocks are released, so the
 * directory is only observable during execution).
 */
public class TestSpillTaskEngine extends DuckDbEngine {
    private final Path spillDir;

    public TestSpillTaskEngine() throws IOException {
        this.spillDir = Files.createTempDirectory("duckdb-wi6-spill");
    }

    public Path getSpillDirectory() {
        return spillDir;
    }

    @Override
    protected Connection newJdbcConnection(String url) throws SQLException {
        Connection conn = super.newJdbcConnection(url);
        try (Statement st = conn.createStatement()) {
            st.execute("SET memory_limit='128MB'");
            st.execute("SET threads=2");
            st.execute("SET temp_directory='" + spillDir.toString().replace("'", "''") + "'");
        }
        return conn;
    }
}
