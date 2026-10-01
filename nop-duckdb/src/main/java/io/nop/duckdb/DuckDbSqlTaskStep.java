package io.nop.duckdb;

import io.nop.api.core.exceptions.NopException;
import io.nop.task.step.AbstractTaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SQL execution task step for nop-task orchestration, wired through the official
 * {@code <step type="simple" bean="...">} extension point (no changes to nop-task-core/xdef).
 *
 * <p>Inputs (read from the step scope, see TaskStepExecution.initInputs):
 * <ul>
 * <li>{@code sql} (required) - the SQL to execute. Values are bound via PreparedStatement
 * parameters only; user values are never interpolated into the SQL text.</li>
 * <li>{@code params} (optional List) - positional PreparedStatement parameters.</li>
 * <li>{@code ingestCsvPath} / {@code ingestParquetPath} + {@code ingestTable} (optional) -
 * ingest a data file into a table before executing the SQL. All stages of one execution share
 * a single connection (in-memory tables do not survive across connections, see WI2 contract).</li>
 * <li>{@code exportPath} + {@code exportFormat} (optional, csv|parquet) - export the SQL
 * result instead of counting it (the SQL runs exactly once either way).</li>
 * <li>{@code resultTable} (optional) - materialize the SQL result into a table with this
 * name; the summary output {@code outputTable} carries the table name so downstream steps
 * can reference it via scope variables (only names cross steps, never data).</li>
 * <li>{@code dbPath} (optional) - use a persistent .duckdb file instead of a private
 * in-memory database. Multi-step pipelines must pass the same dbPath to share tables.</li>
 * </ul>
 *
 * <p>Outputs (declare in task.xml as <output> to expose to downstream steps):
 * {@code rowCount} (SQL result rows / update count), {@code rowsIngested},
 * {@code rowsExported}, {@code outputPath}. Only paths and table names cross step
 * boundaries, never data.
 *
 * <p>Statelessness: this bean is a shared singleton - execute() must not keep
 * connection/statement state on the instance.
 */
public class DuckDbSqlTaskStep extends AbstractTaskStep {
    public static final String STEP_TYPE_DUCKDB_SQL = "duckdb-sql";

    private IDuckDbEngine engine;

    @Inject
    public void setEngine(IDuckDbEngine engine) {
        this.engine = engine;
    }

    @Nonnull
    @Override
    public TaskStepReturn execute(@Nonnull ITaskStepRuntime stepRt) {
        InputResolver inputs = new InputResolver(stepRt);
        String sql = inputs.requireText("sql");
        List<Object> params = inputs.optionalList("params");
        String ingestCsvPath = inputs.optionalText("ingestCsvPath");
        String ingestParquetPath = inputs.optionalText("ingestParquetPath");
        String ingestTable = inputs.optionalText("ingestTable");
        String exportPath = inputs.optionalText("exportPath");
        String exportFormat = inputs.optionalText("exportFormat", "csv");
        String dbPath = inputs.optionalText("dbPath");
        String resultTable = inputs.optionalText("resultTable");
        if (resultTable != null && exportPath != null) {
            throw invalidInput("resultTable and exportPath are mutually exclusive");
        }

        boolean hasIngest = ingestCsvPath != null || ingestParquetPath != null;
        if (hasIngest && ingestTable == null) {
            throw invalidInput("ingestTable is required when ingestCsvPath/ingestParquetPath is set");
        }
        if (!"csv".equals(exportFormat) && !"parquet".equals(exportFormat)) {
            throw invalidInput("exportFormat must be csv or parquet, got: " + exportFormat);
        }
        boolean exportParquet = "parquet".equals(exportFormat);

        Map<String, Object> outputs = new HashMap<>();
        try (Connection conn = openConnection(dbPath)) {
            long rowsIngested = 0;
            if (ingestCsvPath != null) {
                rowsIngested += DuckDbFiles.readCsv(conn, ingestCsvPath, ingestTable);
            }
            if (ingestParquetPath != null) {
                rowsIngested += DuckDbFiles.readParquet(conn, ingestParquetPath, ingestTable);
            }

            long rowCount;
            long rowsExported = 0;
            if (exportPath != null) {
                if (exportParquet) {
                    rowCount = DuckDbFiles.writeParquet(conn, sql, params, exportPath);
                } else {
                    rowCount = DuckDbFiles.writeCsv(conn, sql, params, exportPath);
                }
                rowsExported = rowCount;
            } else if (resultTable != null) {
                rowCount = materialize(conn, sql, params, resultTable);
                outputs.put("outputTable", resultTable);
            } else {
                rowCount = executeSql(conn, sql, params);
            }

            outputs.put("rowCount", rowCount);
            outputs.put("rowsIngested", rowsIngested);
            outputs.put("rowsExported", rowsExported);
            outputs.put("outputPath", exportPath);
        } catch (SQLException e) {
            throw new NopDuckDbException(NopDuckDbErrors.ERR_DUCKDB_IO_FAILED, e)
                    .param(NopDuckDbErrors.ARG_REASON, "task step execution failed: " + e.getMessage());
        }
        return stepRt.RETURN(outputs);
    }

    /**
     * Materialize the SQL result into a table; the created row count is reported exactly.
     */
    private long materialize(Connection conn, String sql, List<Object> params, String resultTable)
            throws SQLException {
        String ctas = "CREATE TABLE " + DuckDbFiles.quoteIdentifier(resultTable) + " AS (" + sql + ")";
        try (PreparedStatement ps = conn.prepareStatement(ctas)) {
            bindParams(ps, params);
            ps.executeUpdate();
        }
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM "
                     + DuckDbFiles.quoteIdentifier(resultTable))) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private Connection openConnection(String dbPath) {
        return dbPath == null ? engine.openMemory() : engine.openFile(dbPath);
    }

    private long executeSql(Connection conn, String sql, List<Object> params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, params);
            if (ps.execute()) {
                // query: count result rows (running a separate count(*) would execute the SQL twice)
                long n = 0;
                try (ResultSet rs = ps.getResultSet()) {
                    while (rs.next()) {
                        n++;
                    }
                }
                return n;
            }
            return ps.getUpdateCount();
        }
    }

    private static void bindParams(PreparedStatement ps, List<Object> params) throws SQLException {
        if (params == null) {
            return;
        }
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    /**
     * Step inputs must be read from the step-local scope ({@code getLocalValue}): task-level
     * inputs live in the parent/task scope, and the fall-through {@code getValue} would leak
     * them into every step (e.g. an ingest step seeing an export step's exportPath).
     * TaskStepExecution.initInputs sets declared inputs as step-local values.
     */
    private class InputResolver {
        private final ITaskStepRuntime stepRt;

        InputResolver(ITaskStepRuntime stepRt) {
            this.stepRt = stepRt;
        }

        String requireText(String name) {
            String v = optionalText(name);
            if (v == null || v.isEmpty()) {
                throw invalidInput(name + " is required");
            }
            return v;
        }

        String optionalText(String name) {
            return optionalText(name, null);
        }

        String optionalText(String name, String defaultValue) {
            Object v = stepRt.getLocalValue(name);
            return v == null ? defaultValue : v.toString();
        }

        List<Object> optionalList(String name) {
            Object v = stepRt.getLocalValue(name);
            if (v == null) {
                return null;
            }
            if (v instanceof List) {
                //noinspection unchecked
                return (List<Object>) v;
            }
            throw invalidInput(name + " must be a list of positional parameters");
        }
    }

    private static NopException invalidInput(String reason) {
        // permanent: the task DSL config is wrong, retrying cannot fix it
        return new NopDuckDbException(NopDuckDbErrors.ERR_DUCKDB_INVALID_STEP_INPUT)
                .param(NopDuckDbErrors.ARG_REASON, reason)
                .bizFatal(true);
    }

    @Override
    public String getStepType() {
        // informational only: the simple-step wrapper overrides the observable step type
        return STEP_TYPE_DUCKDB_SQL;
    }
}
