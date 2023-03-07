/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dbtool.core.discovery.jdbc;

import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.Pair;
import io.nop.core.lang.sql.StdSqlType;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.SQLDataType;
import io.nop.dao.dialect.model.SqlDataTypeModel;
import io.nop.dbtool.core.DataBaseMeta;
import io.nop.dbtool.core.TableSchemaMeta;
import io.nop.orm.OrmConstants;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.model.OrmUniqueKeyModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JdbcMetaDiscovery {
    static final Logger LOG = LoggerFactory.getLogger(JdbcMetaDiscovery.class);

    private IDialect dialect;
    private String packageName;
    private boolean commentAsDisplayName = true;

    public JdbcMetaDiscovery basePackageName(String packageName) {
        this.packageName = packageName;
        return this;
    }

    public JdbcMetaDiscovery commentAsDisplayName(boolean b) {
        this.commentAsDisplayName = b;
        return this;
    }

    public DataBaseMeta discover(DataSource dataSource,
                                 String catalog, String schemaPattern,
                                 String tablePattern) {
        dialect = DialectManager.instance().getDialectForDataSource(dataSource);

        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            DatabaseMetaData metaData = conn.getMetaData();
            DataBaseMeta meta = createMeta(metaData);
            initSchemas(meta, conn.getMetaData());
            discoverTables(meta, metaData, catalog, schemaPattern, tablePattern);
            meta.init();
            return meta;
        } catch (SQLException e) {
            throw dialect.getSQLExceptionTranslator().translate("sql-discovery", e);
        } finally {
            IoHelper.safeCloseObject(conn);
        }
    }

    private DataBaseMeta createMeta(DatabaseMetaData metaData) {
        DataBaseMeta meta = new DataBaseMeta();
        meta.setDriverName(meta.getDriverName());
        meta.setDriverVersion(meta.getDriverVersion());
        meta.setProductName(meta.getProductName());
        meta.setProductVersion(meta.getProductVersion());

        try {
            meta.setSupportsBatchUpdates(metaData.supportsBatchUpdates());
        } catch (Exception e) {
            LOG.debug("ignore-error", e);
        }

        try {
            meta.setSupportsFullOuterJoins(metaData.supportsFullOuterJoins());
        } catch (Exception e) {
            LOG.debug("ignore-error", e);
        }

        try {
            meta.setSupportsTransactions(metaData.supportsTransactions());
        } catch (Exception e) {
            LOG.debug("ignore-error", e);
        }

        try {
            meta.setSupportsStoreProcedures(metaData.supportsStoredProcedures());
        } catch (Exception e) {
            LOG.debug("ignore-error", e);
        }
        return meta;
    }

    private void initSchemas(DataBaseMeta meta, DatabaseMetaData metaData) throws SQLException {
        try (ResultSet schemas = metaData.getSchemas()) {
            List<TableSchemaMeta> ret = new ArrayList<>();
            while (schemas.next()) {
                String table_schem = schemas.getString("TABLE_SCHEM");
                String table_catalog = schemas.getString("TABLE_CATALOG");

                TableSchemaMeta schema = new TableSchemaMeta();
                schema.setSchema(table_schem);
                schema.setCatalog(table_catalog);
            }
            meta.setSchemas(ret);
        }
    }

    private void discoverTables(DataBaseMeta meta, DatabaseMetaData metaData,
                                String catalog, String schemaPattern, String tablePattern) throws SQLException {
        try (ResultSet resultSet = metaData.getTables(catalog, schemaPattern, tablePattern,
                new String[]{"TABLE", "VIEW"})) {
            while (resultSet.next()) {
                String tableCat = resultSet.getString("TABLE_CAT");
                String tableSchema = resultSet.getString("TABLE_SCHEM");
                String tableName = resultSet.getString("TABLE_NAME");
                String remarks = resultSet.getString("REMARKS");
                String tableType = resultSet.getString("TABLE_TYPE");

                tableName = normalizeTableName(tableName);

                OrmEntityModel table = new OrmEntityModel();
                table.setTableName(tableName);
                table.setName(StringHelper.camelCase(tableName, true));
                if (commentAsDisplayName) {
                    table.setDisplayName(remarks);
                } else {
                    table.setComment(remarks);
                }
                table.setDbCatalog(tableCat);
                table.setDbSchema(tableSchema);
                table.setClassName(StringHelper.fullClassName(table.getName(), packageName));
                table.setTableView("VIEW".equals(tableType));

                if (StringHelper.isEmpty(table.getDisplayName()))
                    table.setDisplayName(StringHelper.camelCase(table.getName(), true));

                meta.addTable(table);
            }
        }

        discoverColumns(meta, metaData, catalog, schemaPattern, tablePattern);
    }

    private String normalizeTableName(String tableName) {
        if (dialect.getTableNameCase() == null)
            return tableName;
        return dialect.getTableNameCase().normalize(tableName);
    }

    private String normalizeColName(String colName) {
        if (dialect.getColumnNameCase() == null)
            return colName;
        return dialect.getColumnNameCase().normalize(colName);
    }

    private void discoverColumns(DataBaseMeta meta, DatabaseMetaData metaData, String catalog, String schemaPattern,
                                 String tablePattern) throws SQLException {

        Map<String, List<OrmColumnModel>> tableCols = new HashMap<>();

        try (ResultSet columns = metaData.getColumns(catalog, schemaPattern, tablePattern, null)) {

            while (columns.next()) {
                String tableName = columns.getString("TABLE_NAME");
                String columnName = columns.getString("COLUMN_NAME");
                int columnSize = columns.getInt("COLUMN_SIZE");
                int jdbcType = columns.getInt("DATA_TYPE");
                String typeName = columns.getString("TYPE_NAME");
                String isNullable = columns.getString("IS_NULLABLE");
                int digits = columns.getInt("DECIMAL_DIGITS");
                //String isAutoIncrement = columns.getString("IS_AUTOINCREMENT");
                String remarks = columns.getString("REMARKS");
                String generated = columns.getString("IS_GENERATEDCOLUMN");
                int ordinal = columns.getInt("ORDINAL_POSITION");

                columnName = normalizeColName(columnName);

                OrmColumnModel col = new OrmColumnModel();
                col.setCode(columnName);
                col.setName(StringHelper.camelCase(columnName, false));
                if (OrmConstants.PROP_ID.equals(col.getName())) {
                    col.setName("id_");
                }

                SqlDataTypeModel dataType = dialect.getNativeType(typeName);
                SQLDataType sqlDataType;
                if (dataType == null) {
                    StdSqlType stdSqlType = StdSqlType.fromJdbcType(jdbcType);
                    col.setStdSqlType(stdSqlType);
                    col.setStdDataType(stdSqlType.getStdDataType());
                    sqlDataType = dialect.stdToNativeSqlType(stdSqlType, columnSize, digits);
                } else {
                    sqlDataType = new SQLDataType(dataType.getCodeOrName(), columnSize, digits);
                    col.setStdSqlType(dataType.getStdSqlType());
                    col.setStdDataType(dataType.getStdSqlType().getStdDataType());
                }

                if (col.getStdSqlType().isAllowPrecision()) {
                    col.setPrecision(sqlDataType.getPrecision());
                }
                if (col.getStdSqlType().isAllowScale()) {
                    col.setScale(sqlDataType.getScale());
                }
                col.setMandatory("No".equalsIgnoreCase(isNullable));
                if (commentAsDisplayName) {
                    col.setDisplayName(remarks);
                } else {
                    col.setComment(remarks);
                }
                col.setPropId(ordinal);
                if ("Yes".equalsIgnoreCase(generated)) {
                    col.setInsertable(false);
                    col.setUpdatable(false);
                }
                if (StringHelper.isEmpty(col.getDisplayName()))
                    col.setDisplayName(StringHelper.camelCase(col.getName(), true));
                tableCols.computeIfAbsent(tableName, k -> new ArrayList<>()).add(col);
            }
        }

        for (Map.Entry<String, List<OrmColumnModel>> entry : tableCols.entrySet()) {
            String tableName = entry.getKey();
            List<OrmColumnModel> cols = entry.getValue();

            OrmUniqueKeyModel pk = discoverPrimaryKeys(metaData, catalog, schemaPattern, tableName);

            tableName = normalizeTableName(tableName);

            OrmEntityModel table = meta.getTable(tableName);
            if (table == null) {
                continue;
            }
            table.setDbPkName(pk.getConstraint());

            cols.sort(Comparator.comparing(OrmColumnModel::getPropId));

            // 没有主键，则这里强制设置第一个字段为主键
            if (pk.getColumns().isEmpty()) {
                cols.get(0).setPrimary(true);
            } else {
                for (OrmColumnModel col : cols) {
                    if (pk.getColumns().contains(col.getCode()))
                        col.setPrimary(true);
                }
            }

            // ordinal存在重复的情况
            for (int i = 0, n = cols.size(); i < n; i++) {
                cols.get(i).setPropId(i + 1);
            }

            table.setColumns(cols);

            table.init();
        }
    }

    private OrmUniqueKeyModel discoverPrimaryKeys(DatabaseMetaData metaData, String catalog,
                                                  String schemaPattern, String tableName) throws SQLException {
        try (ResultSet primaryKeys = metaData.getPrimaryKeys(catalog, schemaPattern, tableName)) {
            OrmUniqueKeyModel key = new OrmUniqueKeyModel();

            List<Pair<Integer, String>> cols = new ArrayList<>();
            while (primaryKeys.next()) {
                String columnName = primaryKeys.getString("COLUMN_NAME");
                //String pkName = primaryKeys.getString("PK_NAME");  固定为Primary
                int seq = primaryKeys.getInt("KEY_SEQ");
                columnName = normalizeColName(columnName);
                //key.setConstraint(pkName);
                //key.setName(pkName);
                cols.add(Pair.of(seq, columnName));
            }

            cols.sort(Comparator.comparing(Pair::getFirst));
            key.setColumns(cols.stream().map(Pair::getValue).collect(Collectors.toList()));
            return key;
        }
    }
}