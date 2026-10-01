package io.nop.duckdb;

import java.sql.Connection;

/**
 * In-process DuckDB connection/session manager for the analysis execution layer.
 *
 * <p>Contract (WI0 adjudication, ai-dev/analysis/2026-10/):
 * <ul>
 * <li>Connections are plain JDBC connections with engine config (memory_limit / threads /
 * temp_directory) applied via {@code SET} on every opened connection.</li>
 * <li>All connections to the same {@code .duckdb} file within one JVM must use an identical
 * configuration - DuckDB rejects mismatched configurations and a second writer process is
 * rejected by file lock. This engine fails fast with {@code nop.err.duckdb.config-conflict}
 * when a conflicting open is detected.</li>
 * <li>{@link #close()} releases every connection opened by this engine instance.</li>
 * </ul>
 */
public interface IDuckDbEngine extends AutoCloseable {

    /**
     * Open an in-memory DuckDB connection with engine config applied.
     */
    Connection openMemory();

    /**
     * Open a connection to a persistent {@code .duckdb} file with engine config applied.
     * The file acts as a task-level working set: committed data survives close and reopen.
     */
    Connection openFile(String filePath);

    /**
     * Close all connections opened by this engine. The engine must not be used afterwards.
     */
    @Override
    void close();
}
