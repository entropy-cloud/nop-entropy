package io.nop.metadata.service;

import io.nop.api.core.exceptions.ErrorCode;

interface DataSourceErrors extends NopMetadataArgs {

    ErrorCode ERR_DATASOURCE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.datasource-not-found",
                    "MetaDataSource not found: {dataSourceId}", ARG_DATA_SOURCE_ID);
    ErrorCode ERR_DATASOURCE_DISABLED =
            ErrorCode.define("nop.err.metadata.datasource-disabled",
                    "DataSource is disabled, cannot test connection: {dataSourceId}", ARG_DATA_SOURCE_ID);
    ErrorCode ERR_DATASOURCE_TYPE_NOT_SUPPORTED =
            ErrorCode.define("nop.err.metadata.datasource-type-not-supported",
                    "DataSource type not supported yet: {datasourceType}", ARG_DATASOURCE_TYPE);
    ErrorCode ERR_DATASOURCE_CONFIG_INVALID =
            ErrorCode.define("nop.err.metadata.datasource-config-invalid",
                    "Invalid connection config for datasourceType={datasourceType}: {reason}",
                    ARG_DATASOURCE_TYPE, ARG_REASON);
    ErrorCode ERR_DATASOURCE_CONNECT_FAILED =
            ErrorCode.define("nop.err.metadata.datasource-connect-failed",
                    "DataSource connection failed for datasourceType={datasourceType}: {error}",
                    ARG_DATASOURCE_TYPE, ARG_ERROR);
    ErrorCode ERR_DATASOURCE_JDBC_URL_BLOCKED =
            ErrorCode.define("nop.err.metadata.datasource-jdbc-url-blocked",
                    "JDBC URL is blocked by security policy (protocol/host not allowed or dangerous parameter present): "
                            + "{jdbcUrl} reason={reason}", ARG_JDBC_URL, ARG_REASON);
    ErrorCode ERR_DATASOURCE_DRIVER_NOT_ALLOWED =
            ErrorCode.define("nop.err.metadata.datasource-driver-not-allowed",
                    "JDBC driver class is not in the allowed whitelist: {driverClassName}", ARG_DRIVER_CLASS_NAME);
    ErrorCode ERR_DATASOURCE_RESOLVE_NO_DATASOURCE =
            ErrorCode.define("nop.err.metadata.datasource-resolve-no-datasource",
                    "No registered MetaDataSource for querySpace: {querySpace}", ARG_QUERY_SPACE);
    ErrorCode ERR_DATASOURCE_RESOLVE_DISABLED =
            ErrorCode.define("nop.err.metadata.datasource-resolve-disabled",
                    "MetaDataSource is DISABLED, cannot be used for query execution: {dataSourceId} querySpace={querySpace}",
                    ARG_DATA_SOURCE_ID, ARG_QUERY_SPACE);
    ErrorCode ERR_DATASOURCE_DUPLICATE_QUERY_SPACE =
            ErrorCode.define("nop.err.metadata.datasource-duplicate-query-space",
                    "Multiple MetaDataSource rows match the same querySpace (UK violation in live data): "
                            + "querySpace={querySpace} dataSourceCount={dataSourceCount}",
                    ARG_QUERY_SPACE, ARG_DATA_SOURCE_COUNT);
    ErrorCode ERR_TABLEREF_ENTITY_QUERY_SPACE_NOT_JDBC =
            ErrorCode.define("nop.err.metadata.tableref-entity-query-space-not-jdbc",
                    "Platform transaction for entity querySpace is not a JDBC transaction: {querySpace}",
                    ARG_QUERY_SPACE);
    ErrorCode ERR_TABLEREF_UNKNOWN_TABLE_TYPE =
            ErrorCode.define("nop.err.metadata.tableref-unknown-table-type",
                    "Unknown tableType for table-reference resolution: {metaTableId} tableType={tableType}",
                    ARG_META_TABLE_ID, ARG_TABLE_TYPE);
    ErrorCode ERR_TABLEREF_ENTITY_BASE_NULL =
            ErrorCode.define("nop.err.metadata.tableref-entity-base-null",
                    "Cannot resolve entity table: baseEntityId is null (dangling reference): {metaTableId}",
                    ARG_META_TABLE_ID);
    ErrorCode ERR_TABLEREF_ENTITY_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.tableref-entity-not-found",
                    "Cannot resolve entity table: NopMetaEntity not found for baseEntityId: "
                            + "{metaTableId} baseEntityId={baseEntityId}", ARG_META_TABLE_ID, ARG_BASE_ENTITY_ID);
    ErrorCode ERR_TABLEREF_ENTITY_NOT_REGISTERED =
            ErrorCode.define("nop.err.metadata.tableref-entity-not-registered",
                    "Entity is not registered in runtime IOrmSessionFactory: "
                            + "{metaTableId} entityName={entityName}", ARG_META_TABLE_ID, ARG_ENTITY_NAME);
    ErrorCode ERR_TABLEREF_ENTITY_TABLE_NAME_EMPTY =
            ErrorCode.define("nop.err.metadata.tableref-entity-table-name-empty",
                    "Cannot resolve entity table: NopMetaEntity.tableName is empty: "
                            + "{metaTableId} entityName={entityName}", ARG_META_TABLE_ID, ARG_ENTITY_NAME);
    ErrorCode ERR_TABLEREF_SQL_SOURCE_EMPTY =
            ErrorCode.define("nop.err.metadata.tableref-sql-source-empty",
                    "Cannot resolve sql table: sourceSql is empty: {metaTableId}", ARG_META_TABLE_ID);
    ErrorCode ERR_TABLEREF_PLATFORM_META_FAILED =
            ErrorCode.define("nop.err.metadata.tableref-platform-meta-failed",
                    "Failed to get DatabaseMetaData from platform connection: {error}", ARG_ERROR);
    ErrorCode ERR_TABLEREF_EXEC_FAILED =
            ErrorCode.define("nop.err.metadata.tableref-exec-failed",
                    "Table-reference execution failed: {metaTableId} -- {error}",
                    ARG_META_TABLE_ID, ARG_ERROR);
    ErrorCode ERR_TABLE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.table-not-found",
                    "Meta table not found: {metaTableId}", ARG_META_TABLE_ID);
    ErrorCode ERR_QUERY_TABLE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.query-table-not-found",
                    "NopMetaTable not found for queryTableData: {metaTableId}", ARG_META_TABLE_ID);
    ErrorCode ERR_QUERY_UNSUPPORTED_TABLE_TYPE =
            ErrorCode.define("nop.err.metadata.query-unsupported-table-type",
                    "Unsupported tableType for queryTableData: {metaTableId} tableType={tableType}",
                    ARG_META_TABLE_ID, ARG_TABLE_TYPE);
    ErrorCode ERR_QUERY_ENTITY_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.query-entity-not-found",
                    "Entity record not found for entity table (baseEntityId dangling): "
                            + "{metaTableId} baseEntityId={baseEntityId}", ARG_META_TABLE_ID, ARG_BASE_ENTITY_ID);
    ErrorCode ERR_QUERY_ENTITY_NOT_REGISTERED =
            ErrorCode.define("nop.err.metadata.query-entity-not-registered",
                    "Entity is not registered in runtime IOrmSessionFactory: "
                            + "{metaTableId} entityName={entityName}", ARG_META_TABLE_ID, ARG_ENTITY_NAME);
    ErrorCode ERR_QUERY_SQL_SOURCE_EMPTY =
            ErrorCode.define("nop.err.metadata.query-sql-source-empty",
                    "sql table sourceSql is empty, cannot query: {metaTableId}", ARG_META_TABLE_ID);
    ErrorCode ERR_QUERY_UNSUPPORTED_DIALECT =
            ErrorCode.define("nop.err.metadata.query-unsupported-dialect",
                    "Dialect not supported in first version (only H2/MySQL/PostgreSQL): "
                            + "{databaseProductName} metaTableId={metaTableId}",
                    ARG_DATABASE_PRODUCT_NAME, ARG_META_TABLE_ID);
    ErrorCode ERR_QUERY_SQL_EXEC_FAILED =
            ErrorCode.define("nop.err.metadata.query-sql-exec-failed",
                    "Query SQL execution failed: metaTableId={metaTableId} -- {error}",
                    ARG_META_TABLE_ID, ARG_ERROR);
}
