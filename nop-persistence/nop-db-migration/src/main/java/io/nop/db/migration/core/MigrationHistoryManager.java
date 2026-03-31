/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.db.migration.core;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dataset.IDataRow;

import java.util.HashSet;
import java.util.Set;

import static io.nop.db.migration.DbMigrationErrors.ARG_TABLE_NAME;
import static io.nop.db.migration.DbMigrationErrors.ERR_DB_MIGRATION_HISTORY_QUERY_FAIL;

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
        
        String sql = "SELECT version FROM " + TABLE_NAME + " WHERE success = TRUE ORDER BY installed_on";
        
        try {
            jdbcTemplate.executeQuery(
                SQL.begin().querySpace(querySpace).append(sql).end(),
                dataSet -> {
                    for (IDataRow row : dataSet) {
                        int versionIndex = row.getMeta().getFieldIndex("VERSION");
                        versions.add(row.getString(versionIndex));
                    }
                    return null;
                }
            );
        } catch (Exception e) {
            throw new NopException(ERR_DB_MIGRATION_HISTORY_QUERY_FAIL, e)
                .param(ARG_TABLE_NAME, TABLE_NAME);
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
        String updateSql = "UPDATE " + TABLE_NAME +
            " SET description = ?, type = ?, checksum = ?, installed_on = ?, execution_time = ?, " +
            " success = ?, installed_by = ?, error_message = ? WHERE version = ?";

        String insertSql = "INSERT INTO " + TABLE_NAME +
            " (version, description, type, checksum, installed_on, execution_time, success, installed_by, error_message) " +
            " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        boolean success = record.isSuccess();
        if (!success && record.getErrorMessage() == null) {
            success = true;
        }

        java.sql.Timestamp installedOn = new java.sql.Timestamp(System.currentTimeMillis());

        long updated = jdbcTemplate.executeUpdate(
            SQL.begin()
                .querySpace(querySpace)
                .name("update-migration-record")
                .sql(updateSql,
                    record.getDescription(),
                    record.getType(),
                    record.getChecksum(),
                    installedOn,
                    record.getExecutionTime(),
                    success,
                    record.getInstalledBy(),
                    record.getErrorMessage(),
                    record.getVersion())
                .end()
        );

        if (updated > 0) {
            return;
        }

        jdbcTemplate.executeUpdate(
            SQL.begin()
                .querySpace(querySpace)
                .name("insert-migration-record")
                .sql(insertSql,
                    record.getVersion(),
                    record.getDescription(),
                    record.getType(),
                    record.getChecksum(),
                    installedOn,
                    record.getExecutionTime(),
                    success,
                    record.getInstalledBy(),
                    record.getErrorMessage())
                .end()
        );
    }
    
    public MigrationRecord getMigrationByVersion(String version) {
        if (!tableExists()) {
            return null;
        }
        
        String sql = "SELECT version, description, type, checksum, installed_on, execution_time, success, installed_by, error_message " +
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
                        int versionIdx = row.getMeta().getFieldIndex("VERSION");
                        int descIdx = row.getMeta().getFieldIndex("DESCRIPTION");
                        int typeIdx = row.getMeta().getFieldIndex("TYPE");
                        int checksumIdx = row.getMeta().getFieldIndex("CHECKSUM");
                        int installedOnIdx = row.getMeta().getFieldIndex("INSTALLED_ON");
                        int execTimeIdx = row.getMeta().getFieldIndex("EXECUTION_TIME");
                        int successIdx = row.getMeta().getFieldIndex("SUCCESS");
                        int installedByIdx = row.getMeta().getFieldIndex("INSTALLED_BY");
                        int errorMessageIdx = row.getMeta().getFieldIndex("ERROR_MESSAGE");
                        
                        record.setVersion(row.getString(versionIdx));
                        record.setDescription(row.getString(descIdx));
                        record.setType(row.getString(typeIdx));
                        record.setChecksum(row.getString(checksumIdx));
                        record.setInstalledOn(row.getTimestamp(installedOnIdx));
                        record.setExecutionTime(row.getLong(execTimeIdx));
                        record.setSuccess(row.getBoolean(successIdx));
                        record.setInstalledBy(row.getString(installedByIdx));
                        record.setErrorMessage(row.getString(errorMessageIdx));
                        result[0] = record;
                        break;
                    }
                    return null;
                }
            );
        } catch (Exception e) {
            return null;
        }
        
        return result[0];
    }
    
    protected boolean tableExists() {
        return jdbcTemplate.existsTable(querySpace, TABLE_NAME);
    }
    
    protected String buildCreateHistoryTableSQL(IDialect dialect) {
        return "CREATE TABLE " + TABLE_NAME + " (" +
            "version VARCHAR(200) NOT NULL PRIMARY KEY, " +
            "description VARCHAR(500), " +
            "type VARCHAR(20), " +
            "checksum VARCHAR(100), " +
            "installed_on TIMESTAMP, " +
            "execution_time BIGINT, " +
            "success BOOLEAN, " +
                "installed_by VARCHAR(100), " +
                "error_message CLOB" +
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
}
