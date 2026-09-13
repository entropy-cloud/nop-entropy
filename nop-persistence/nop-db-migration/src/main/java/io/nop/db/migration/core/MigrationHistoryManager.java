/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.db.migration.core;

import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdSqlType;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dataset.IDataRow;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import static io.nop.db.migration.DbMigrationErrors.ARG_QUERY_SPACE;
import static io.nop.db.migration.DbMigrationErrors.ARG_TABLE_NAME;
import static io.nop.db.migration.DbMigrationErrors.ERR_DB_MIGRATION_HISTORY_QUERY_FAILED;

public class MigrationHistoryManager {

    public static final String TABLE_NAME = "nop_db_migration_history";

    private final IJdbcTemplate jdbcTemplate;
    private final String querySpace;

    public MigrationHistoryManager(IJdbcTemplate jdbcTemplate, String querySpace) {
        this.jdbcTemplate = jdbcTemplate;
        this.querySpace = querySpace != null ? querySpace : "default";
    }

    public Set<String> getExecutedVersions() {
        Set<String> versions = new HashSet<>();

        if (!tableExists()) {
            return versions;
        }

        // Column values are read by position, because JDBC drivers return the
        // column label using the case in which the table was created (e.g. MySQL
        // keeps lowercase), while some databases upper-case it (e.g. H2).
        // The success flag is bound as a parameter: Oracle and SQL Server do not
        // support the untyped TRUE/FALSE literals in comparisons.
        String sql = "SELECT version FROM " + TABLE_NAME + " WHERE success = ? ORDER BY installed_on";

        try {
            jdbcTemplate.executeQuery(
                SQL.begin().querySpace(querySpace).sql(sql, Boolean.TRUE).end(),
                dataSet -> {
                    for (IDataRow row : dataSet) {
                        versions.add(row.getString(0));
                    }
                    return null;
                }
            );
        } catch (Exception e) {
            throw historyQueryFailed(e);
        }

        return versions;
    }

    public void ensureHistoryTableExists(IDialect dialect) {
        if (tableExists()) {
            return;
        }

        String sql = buildCreateHistoryTableSQL(dialect);

        jdbcTemplate.executeUpdate(
            SQL.begin()
                .querySpace(querySpace)
                .name("create-migration-history-table")
                .append(sql)
                .end()
        );
    }

    public void recordMigration(MigrationRecord record) {
        // A version may already have a history row (e.g. a previous failed
        // attempt, or a repeatable migration being re-executed). Replace the
        // existing row with a single UPDATE, and only INSERT when no row
        // exists. The previous DELETE+INSERT pair was not atomic: if the
        // process died (or the INSERT failed, e.g. a description longer than
        // VARCHAR(500)) between the two statements, the version's history row
        // was already gone and the migration would silently re-execute.
        String updateSql = "UPDATE " + TABLE_NAME +
            " SET description = ?, type = ?, checksum = ?, installed_on = ?, execution_time = ?, success = ?, installed_by = ?" +
            " WHERE version = ?";

        long updated = jdbcTemplate.executeUpdate(
            SQL.begin()
                .querySpace(querySpace)
                .name("update-migration-record")
                .sql(updateSql,
                    record.getDescription(),
                    record.getType(),
                    record.getChecksum(),
                    new java.sql.Timestamp(CoreMetrics.currentTimeMillis()),
                    record.getExecutionTime(),
                    record.isSuccess(),
                    record.getInstalledBy(),
                    record.getVersion())
                .end()
        );

        if (updated > 0) {
            return;
        }

        String sql = "INSERT INTO " + TABLE_NAME +
            " (version, description, type, checksum, installed_on, execution_time, success, installed_by) " +
            " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        jdbcTemplate.executeUpdate(
            SQL.begin()
                .querySpace(querySpace)
                .name("record-migration")
                .sql(sql,
                    record.getVersion(),
                    record.getDescription(),
                    record.getType(),
                    record.getChecksum(),
                    new java.sql.Timestamp(CoreMetrics.currentTimeMillis()),
                    record.getExecutionTime(),
                    record.isSuccess(),
                    record.getInstalledBy())
                .end()
        );
    }

    public MigrationRecord getMigrationByVersion(String version) {
        if (!tableExists()) {
            return null;
        }

        String sql = "SELECT version, description, type, checksum, installed_on, execution_time, success, installed_by " +
            " FROM " + TABLE_NAME + " WHERE version = ?";

        final MigrationRecord[] result = new MigrationRecord[1];

        try {
            jdbcTemplate.executeQuery(
                SQL.begin()
                    .querySpace(querySpace)
                    .name("get-migration-by-version")
                    .sql(sql, version)
                    .end(),
                dataSet -> {
                    for (IDataRow row : dataSet) {
                        MigrationRecord record = new MigrationRecord();
                        record.setVersion(row.getString(0));
                        record.setDescription(row.getString(1));
                        record.setType(row.getString(2));
                        record.setChecksum(row.getString(3));
                        record.setInstalledOn(row.getTimestamp(4));
                        record.setExecutionTime(row.getLong(5));
                        record.setSuccess(row.getBoolean(6));
                        record.setInstalledBy(row.getString(7));
                        result[0] = record;
                        break;
                    }
                    return null;
                }
            );
        } catch (Exception e) {
            throw historyQueryFailed(e);
        }

        return result[0];
    }

    protected boolean tableExists() {
        try {
            return Boolean.TRUE.equals(jdbcTemplate.runWithConnection(
                SQL.begin()
                    .querySpace(querySpace)
                    .name("check-table-exists")
                    .end(),
                conn -> tableExists(conn, TABLE_NAME)));
        } catch (NopException e) {
            throw e;
        } catch (Exception e) {
            throw historyQueryFailed(e);
        }
    }

    static boolean tableExists(Connection conn, String tableName) {
        try {
            DatabaseMetaData metaData = conn.getMetaData();
            // Different databases store unquoted identifiers with different
            // letter case (H2/Oracle upper, MySQL/PostgreSQL lower), and the
            // DatabaseMetaData pattern match is driver-dependent, so probe the
            // common case variants.
            for (String candidate : new String[]{tableName, tableName.toUpperCase(), tableName.toLowerCase()}) {
                try (ResultSet rs = metaData.getTables(null, null, candidate, new String[]{"TABLE"})) {
                    while (rs.next()) {
                        String name = rs.getString("TABLE_NAME");
                        if (name != null && name.equalsIgnoreCase(tableName)) {
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

    protected String buildCreateHistoryTableSQL(IDialect dialect) {
        // The success column type must go through the dialect mapping: Oracle
        // (before 23c) and older SQL Server databases have no BOOLEAN column
        // type (e.g. Oracle maps it to CHAR(1)).
        String booleanType = dialect != null
            ? dialect.stdToNativeSqlType(StdSqlType.BOOLEAN, -1, -1).toString()
            : "BOOLEAN";
        return "CREATE TABLE " + TABLE_NAME + " (" +
            "version VARCHAR(200) NOT NULL PRIMARY KEY, " +
            "description VARCHAR(500), " +
            "type VARCHAR(20), " +
            "checksum VARCHAR(100), " +
            "installed_on TIMESTAMP, " +
            "execution_time BIGINT, " +
            "success " + booleanType + ", " +
            "installed_by VARCHAR(100)" +
            ")";
    }

    public void removeMigrationRecord(String version) {
        if (!tableExists()) {
            return;
        }

        String sql = "DELETE FROM " + TABLE_NAME + " WHERE version = ?";

        jdbcTemplate.executeUpdate(
            SQL.begin()
                .querySpace(querySpace)
                .name("remove-migration-record")
                .sql(sql, version)
                .end()
        );
    }

    private NopException historyQueryFailed(Exception e) {
        return new NopException(ERR_DB_MIGRATION_HISTORY_QUERY_FAILED, e)
            .param(ARG_TABLE_NAME, TABLE_NAME)
            .param(ARG_QUERY_SPACE, querySpace);
    }
}
