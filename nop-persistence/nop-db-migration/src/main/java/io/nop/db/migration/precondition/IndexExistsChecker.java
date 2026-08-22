package io.nop.db.migration.precondition;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.sql.SQL;
import io.nop.core.resource.component.AbstractComponentModel;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.db.migration.PreconditionExpect;
import io.nop.db.migration.core.MigrationContext;
import io.nop.db.migration.model.IndexExistsPrecondition;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

import static io.nop.db.migration.DbMigrationErrors.ARG_TABLE_NAME;
import static io.nop.db.migration.DbMigrationErrors.ERR_DB_MIGRATION_HISTORY_QUERY_FAILED;

public class IndexExistsChecker implements IPreconditionChecker {

    public static final String PRECONDITION_TYPE = "indexExists";

    @Override
    public String getPreconditionType() {
        return PRECONDITION_TYPE;
    }

    @Override
    public boolean check(AbstractComponentModel precondition, MigrationContext context) {
        if (!(precondition instanceof IndexExistsPrecondition)) {
            return false;
        }

        IndexExistsPrecondition iep = (IndexExistsPrecondition) precondition;
        String tableName = iep.getTableName();
        String indexName = iep.getIndexName();
        String schemaName = iep.getSchemaName();
        PreconditionExpect expect = iep.getExpect();
        if (expect == null) {
            expect = PreconditionExpect.EXISTS;
        }

        boolean exists = checkIndexExists(context.getJdbcTemplate(), context.getQuerySpace(), tableName, indexName, schemaName);

        return expect == PreconditionExpect.EXISTS ? exists : !exists;
    }

    private boolean checkIndexExists(IJdbcTemplate jdbcTemplate, String querySpace, String tableName, String indexName, String schemaName) {
        // INFORMATION_SCHEMA.INDEXES only exists on H2 (MySQL uses STATISTICS,
        // PostgreSQL pg_indexes, SQL Server sys.indexes), so resolve index
        // metadata through the JDBC driver instead of a dialect-specific query.
        return Boolean.TRUE.equals(jdbcTemplate.runWithConnection(
            SQL.begin().querySpace(querySpace).name("check-index-exists").end(),
            conn -> indexExists(conn, tableName, indexName, schemaName)));
    }

    static boolean indexExists(Connection conn, String tableName, String indexName, String schemaName) {
        try {
            DatabaseMetaData metaData = conn.getMetaData();
            // Databases store unquoted identifiers with different letter case,
            // so probe the common case variants of the table name.
            for (String candidate : new String[]{tableName, tableName.toUpperCase(), tableName.toLowerCase()}) {
                try (ResultSet rs = metaData.getIndexInfo(null, schemaName, candidate, false, false)) {
                    while (rs.next()) {
                        String name = rs.getString("INDEX_NAME");
                        if (name != null && name.equalsIgnoreCase(indexName)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        } catch (SQLException e) {
            throw new NopException(ERR_DB_MIGRATION_HISTORY_QUERY_FAILED, e)
                .param(ARG_TABLE_NAME, tableName);
        }
    }
}
