package io.nop.duckdb;

import io.nop.tablesaw.xlsx.XlsxReadOptions;
import io.nop.tablesaw.xlsx.XlsxReader;
import tech.tablesaw.api.Table;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * File data plane for the DuckDB analysis layer: ingest CSV/Parquet/XLSX into DuckDB tables
 * and export SQL results back to CSV/Parquet (open archival formats; .duckdb files are
 * task-level working sets only).
 *
 * <p>Contract (verified empirically on duckdb 1.5.6, see plan 2284):
 * <ul>
 * <li>Created tables live and die with the connection: a connection from
 * {@code engine.openMemory()} is an isolated private in-memory database (tables invisible to
 * other connections and lost on close); a connection from {@code engine.openFile(path)}
 * persists tables into the {@code .duckdb} file.</li>
 * <li>Returned row count: read side counts via {@code SELECT count(*)} (CREATE TABLE AS does
 * not report a count); write side returns the COPY update count.</li>
 * <li>Read side refuses to overwrite an existing table ({@code table-exists}); write side
 * COPY silently overwrites an existing file - that is the documented archival semantics.</li>
 * <li>Single quotes in paths/identifiers are escaped by this class; callers pass raw paths.</li>
 * </ul>
 */
public final class DuckDbFiles {
    private static final String TABLE_EXISTS_TABLE = "information_schema.tables";

    private DuckDbFiles() {
    }

    /**
     * Ingest a CSV file into a persistent DuckDB table (read_csv_auto, header row expected).
     *
     * @return ingested row count
     */
    public static long readCsv(Connection conn, String csvPath, String tableName) {
        return readExternal(conn, csvPath, tableName, "read_csv_auto");
    }

    /**
     * Ingest a Parquet file into a persistent DuckDB table.
     *
     * @return ingested row count
     */
    public static long readParquet(Connection conn, String parquetPath, String tableName) {
        return readExternal(conn, parquetPath, tableName, "read_parquet");
    }

    /**
     * Ingest an XLSX file via the existing nop-tablesaw XlsxReader (first non-empty sheet),
     * through a temporary CSV file. Column types are re-inferred by read_csv_auto from the
     * serialized CSV, so string columns with numeric content may be typed as numbers - this is
     * the documented bridge behavior.
     *
     * @return ingested row count
     */
    public static long readXlsx(Connection conn, String xlsxPath, String tableName) {
        requireFile(xlsxPath);
        File tempCsv;
        try {
            tempCsv = Files.createTempFile("duckdb-xlsx-", ".csv").toFile();
            tempCsv.deleteOnExit();
            Table table = new XlsxReader().read(XlsxReadOptions.builder(xlsxPath).build());
            table.write().csv(tempCsv.getAbsolutePath());
        } catch (IOException | RuntimeException e) {
            throw new NopDuckDbException(NopDuckDbErrors.ERR_DUCKDB_IO_FAILED, e)
                    .param(NopDuckDbErrors.ARG_FILE_PATH, xlsxPath)
                    .param(NopDuckDbErrors.ARG_REASON, "xlsx bridge conversion failed: " + e.getMessage());
        }
        try {
            return readCsv(conn, tempCsv.getAbsolutePath(), tableName);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tempCsv.delete();
        }
    }

    /**
     * Export a SELECT statement to a CSV file with a header line. Overwrites an existing file.
     *
     * @return exported row count
     */
    public static long writeCsv(Connection conn, String sql, String csvPath) {
        return copyTo(conn, sql, csvPath, "FORMAT CSV, HEADER");
    }

    /**
     * Export a SELECT statement with positional parameters to a CSV file. Overwrites an existing file.
     *
     * @return exported row count
     */
    public static long writeCsv(Connection conn, String sql, List<Object> params, String csvPath) {
        return copyTo(conn, sql, params, csvPath, "FORMAT CSV, HEADER");
    }

    /**
     * Export a SELECT statement to a Parquet file. Overwrites an existing file.
     *
     * @return exported row count
     */
    public static long writeParquet(Connection conn, String sql, String parquetPath) {
        return copyTo(conn, sql, parquetPath, "FORMAT PARQUET");
    }

    /**
     * Export a SELECT statement with positional parameters to a Parquet file. Overwrites an existing file.
     *
     * @return exported row count
     */
    public static long writeParquet(Connection conn, String sql, List<Object> params, String parquetPath) {
        return copyTo(conn, sql, params, parquetPath, "FORMAT PARQUET");
    }

    private static long readExternal(Connection conn, String filePath, String tableName, String readerFn) {
        requireFile(filePath);
        if (tableExists(conn, tableName)) {
            throw new NopDuckDbException(NopDuckDbErrors.ERR_DUCKDB_TABLE_EXISTS)
                    .param(NopDuckDbErrors.ARG_TABLE_NAME, tableName);
        }
        // single quotes are escaped by doubling; identifier quotes likewise.
        // header=true is a read_csv_auto-only parameter (read_parquet rejects it)
        String reader = "read_parquet".equals(readerFn)
                ? readerFn + "('" + escapePath(filePath) + "')"
                : readerFn + "('" + escapePath(filePath) + "', header=true)";
        String sql = "CREATE TABLE " + quoteIdentifier(tableName) + " AS SELECT * FROM " + reader;
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new NopDuckDbException(NopDuckDbErrors.ERR_DUCKDB_IO_FAILED, e)
                    .param(NopDuckDbErrors.ARG_FILE_PATH, filePath)
                    .param(NopDuckDbErrors.ARG_REASON, "ingest failed: " + e.getMessage());
        }
        return countRows(conn, tableName, filePath);
    }

    private static long copyTo(Connection conn, String sql, String filePath, String format) {
        return copyTo(conn, sql, null, filePath, format);
    }

    private static long copyTo(Connection conn, String sql, List<Object> params, String filePath, String format) {
        String copy = "COPY (" + sql + ") TO '" + escapePath(filePath) + "' (" + format + ")";
        try (PreparedStatement ps = conn.prepareStatement(copy)) {
            if (params != null) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
            }
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new NopDuckDbException(NopDuckDbErrors.ERR_DUCKDB_IO_FAILED, e)
                    .param(NopDuckDbErrors.ARG_FILE_PATH, filePath)
                    .param(NopDuckDbErrors.ARG_REASON, "COPY failed: " + e.getMessage());
        }
    }

    private static long countRows(Connection conn, String tableName, String filePath) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT count(*) FROM " + quoteIdentifier(tableName))) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new NopDuckDbException(NopDuckDbErrors.ERR_DUCKDB_IO_FAILED, e)
                    .param(NopDuckDbErrors.ARG_FILE_PATH, filePath)
                    .param(NopDuckDbErrors.ARG_REASON, "row count failed: " + e.getMessage());
        }
    }

    private static boolean tableExists(Connection conn, String tableName) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM " + TABLE_EXISTS_TABLE
                     + " WHERE table_name = '" + escapePath(tableName) + "'")) {
            rs.next();
            return rs.getLong(1) > 0;
        } catch (SQLException e) {
            throw new NopDuckDbException(NopDuckDbErrors.ERR_DUCKDB_IO_FAILED, e)
                    .param(NopDuckDbErrors.ARG_REASON, "table existence check failed: " + e.getMessage());
        }
    }

    private static void requireFile(String filePath) {
        if (filePath == null || !new File(filePath).isFile()) {
            throw new NopDuckDbException(NopDuckDbErrors.ERR_DUCKDB_FILE_NOT_FOUND)
                    .param(NopDuckDbErrors.ARG_FILE_PATH, String.valueOf(filePath));
        }
    }

    static String escapePath(String path) {
        return path.replace("'", "''");
    }

    static String quoteIdentifier(String tableName) {
        return '"' + tableName.replace("\"", "\"\"") + '"';
    }
}
