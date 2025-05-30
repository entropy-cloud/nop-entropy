/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dbtool.core.discovery.jdbc;

import io.nop.commons.type.StdSqlType;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.Pair;
import io.nop.core.lang.xml.XNode;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.SQLDataType;
import io.nop.dao.dialect.model.SqlDataTypeModel;
import io.nop.dbtool.core.DataBaseMeta;
import io.nop.dbtool.core.TableSchemaMeta;
import io.nop.orm.OrmConstants;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.model.OrmIndexColumnModel;
import io.nop.orm.model.OrmIndexModel;
import io.nop.orm.model.OrmJoinOnModel;
import io.nop.orm.model.OrmToOneReferenceModel;
import io.nop.orm.model.OrmUniqueKeyModel;
import io.nop.xlang.xdsl.DslModelHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JdbcMetaDiscovery {
    static final Logger LOG = LoggerFactory.getLogger(JdbcMetaDiscovery.class);

    private final IDialect dialect;
    private final DataSource dataSource;
    private final Connection connection;

    private String packageName = "app";
    private boolean commentAsDisplayName = true;

    private boolean includeRelations = true;

    private boolean includeIndexes = true;

    private boolean includeUniqueKeys = true;

    private boolean ignoreUnknownType = false;

    public static JdbcMetaDiscovery forDataSource(DataSource dataSource) {
        return new JdbcMetaDiscovery(dataSource, null);
    }

    public static JdbcMetaDiscovery forConnection(Connection connection) {
        return new JdbcMetaDiscovery(null, connection);
    }

    private JdbcMetaDiscovery(DataSource dataSource, Connection connection) {
        this.dataSource = dataSource;
        this.connection = connection;
        this.dialect = dataSource != null
                ? DialectManager.instance().getDialectForDataSource(dataSource)
                : DialectManager.instance().getDialectForConnection(connection);
    }

    public JdbcMetaDiscovery ignoreUnknownType(boolean ignoreUnknownType) {
        this.ignoreUnknownType = ignoreUnknownType;
        return this;
    }

    public JdbcMetaDiscovery includeRelations(boolean includeRelations) {
        this.includeRelations = includeRelations;
        return this;
    }

    public JdbcMetaDiscovery includeIndexes(boolean includeIndexes) {
        this.includeIndexes = includeIndexes;
        return this;
    }

    public JdbcMetaDiscovery includeUniqueKeys(boolean includeUniqueKeys) {
        this.includeUniqueKeys = includeUniqueKeys;
        return this;
    }

    public JdbcMetaDiscovery basePackageName(String packageName) {
        this.packageName = packageName;
        return this;
    }

    public JdbcMetaDiscovery commentAsDisplayName(boolean b) {
        this.commentAsDisplayName = b;
        return this;
    }

    public IDialect getDialect() {
        return this.dialect;
    }

    public List<String> getCatalogs() {
        Connection conn = null;
        try {
            conn = getConnection();

            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet rs = metaData.getCatalogs();
            List<String> ret = new ArrayList<>();
            while (rs.next()) {
                String catalogName = rs.getString("TABLE_CAT");
                ret.add(catalogName);
            }
            return ret;
        } catch (SQLException e) {
            throw dialect.getSQLExceptionTranslator().translate("sql-discovery", e);
        } finally {
            closeConnection(conn);
        }
    }

    public List<String> getSchemas() {
        Connection conn = null;
        try {
            conn = getConnection();

            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet rs = metaData.getSchemas();
            List<String> ret = new ArrayList<>();
            while (rs.next()) {
                String schemaName = rs.getString("TABLE_SCHEM");
                ret.add(schemaName);
            }
            return ret;
        } catch (SQLException e) {
            throw dialect.getSQLExceptionTranslator().translate("sql-discovery", e);
        } finally {
            closeConnection(conn);
        }
    }

    /**
     * @param catalog 数据库模式名，为 null 时，仅查询当前连接指定的库
     */
    public DataBaseMeta discover(String catalog, String schemaPattern, String tablePattern) {
        Connection conn = null;
        try {
            conn = getConnection();

            DatabaseMetaData metaData = conn.getMetaData();
            DataBaseMeta meta = createMeta(metaData);
            initSchemas(meta, conn.getMetaData());
            discoverTables(meta, metaData, catalog, schemaPattern, tablePattern);

            if (includeRelations)
                discoverRelations(metaData, meta, catalog, schemaPattern);

            if (includeIndexes)
                discoverIndexes(metaData, meta, catalog, schemaPattern);

            if (includeUniqueKeys)
                discoverUniqueKeys(metaData, meta, catalog, schemaPattern);

            meta.init();

            if (LOG.isDebugEnabled()) {
                XNode node = DslModelHelper.dslModelToXNode(OrmConstants.XDSL_SCHEMA_ORM, meta.getOrmModel());
                node.dump("discover-from-jdbc-meta");
            }
            return meta;
        } catch (SQLException e) {
            throw dialect.getSQLExceptionTranslator().translate("sql-discovery", e);
        } finally {
            closeConnection(conn);
        }
    }

    /**
     * 优先通过 {@link #dataSource} 获取 {@link Connection}
     */
    private Connection getConnection() throws SQLException {
        return dataSource != null ? dataSource.getConnection() : connection;
    }

    /**
     * 仅关闭由 {@link #dataSource} 获得的 {@link Connection}
     */
    private void closeConnection(Connection conn) {
        if (dataSource != null) {
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

                ret.add(schema);
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

    public String normalizeTableName(String tableName) {
        return DataBaseMeta.normalizeTableName(dialect, tableName);
    }

    public String normalizeColName(String colName) {
        return DataBaseMeta.normalizeColName(dialect, colName);
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
                String defaultValue = columns.getString("COLUMN_DEF");
                //int ordinal = columns.getInt("ORDINAL_POSITION");

                columnName = normalizeColName(columnName);

                OrmColumnModel col = new OrmColumnModel();
                col.setCode(columnName);
                col.setName(StringHelper.colCodeToPropName(columnName));
                // Note：获取到的默认值可能是包含引号的转义值（若值为函数，则不会被转义），在使用时需注意
                if (defaultValue != null) {
                    if (StringHelper.isNumber(defaultValue)) {
                        col.setDefaultValue(defaultValue);
                    } else if (defaultValue.startsWith("'") && defaultValue.endsWith("'") && defaultValue.length() > 2) {
                        defaultValue = defaultValue.substring(1, defaultValue.length() - 1);
                        col.setDefaultValue(defaultValue);
                    }
                }

                SqlDataTypeModel dataType = dialect.getNativeType(typeName, ignoreUnknownType);
                SQLDataType sqlDataType;
                if (dataType == null) {
                    StdSqlType stdSqlType = StdSqlType.fromJdbcType(jdbcType);
                    if (stdSqlType == StdSqlType.OTHER) {
                        LOG.warn("nop.warn.jdbc.unknown-sql-type:sqlType={},dialect={}", typeName, dialect.getName());
                        if (ignoreUnknownType)
                            stdSqlType = StdSqlType.VARCHAR;
                    }
                    col.setStdSqlType(stdSqlType);
                    col.setStdDataType(stdSqlType.getStdDataType());
                    sqlDataType = dialect.stdToNativeSqlType(stdSqlType, columnSize, digits);
                } else {
                    sqlDataType = new SQLDataType(dataType.getCodeOrName(), columnSize, digits);
                    col.setStdSqlType(dataType.getStdSqlType());
                    col.setStdDataType(dataType.getStdSqlType().getStdDataType());
                }

                col.setSqlType(typeName);

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
                // col.setPropId(ordinal);
                if ("Yes".equalsIgnoreCase(generated)) {
                    col.setInsertable(false);
                    col.setUpdatable(false);
                }
                if (StringHelper.isEmpty(col.getDisplayName()))
                    col.setDisplayName(StringHelper.camelCase(col.getCode(), true));
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

            // ordinal存在重复的情况
            for (int i = 0, n = cols.size(); i < n; i++) {
                cols.get(i).setPropId(i + 1);
            }

            // 没有主键，则这里强制设置第一个字段为主键
            if (pk.getColumns().isEmpty()) {
                cols.get(0).setPrimary(true);
            } else {
                for (OrmColumnModel col : cols) {
                    if (pk.getColumns().contains(col.getCode()))
                        col.setPrimary(true);
                }
            }

            // 确保主键排在前面
            cols.sort((c1, c2) -> {
                if (c1.isPrimary() && !c2.isPrimary())
                    return -1;
                if (!c1.isPrimary() && c2.isPrimary())
                    return 1;
                return Integer.compare(c1.getPropId(), c2.getPropId());
            });

            // 重新设置propId
            for (int i = 0, n = cols.size(); i < n; i++) {
                cols.get(i).setPropId(i + 1);
            }

            try {
                table.setColumns(cols);

                table.init();
            } catch (RuntimeException e) {
                LOG.error("nop.err.discover-table-fail:table={}", table.getTableName(), e);
                throw e;
            }
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

    private void discoverRelations(
            DatabaseMetaData metaData, DataBaseMeta meta,
            String catalog, String schemaPattern) throws SQLException {

        Map<String, Map<String, OrmToOneReferenceModel>> allRefs = new HashMap<>();

        for (OrmEntityModel table : meta.getTables().values()) {
            try (ResultSet rs = metaData.getImportedKeys(catalog, schemaPattern, table.getTableName())) {
                while (rs.next()) {
                    String fkTableName = rs.getString("FKTABLE_NAME");
                    String fkColumnName = rs.getString("FKCOLUMN_NAME");
                    String pkTableName = rs.getString("PKTABLE_NAME");
                    String pkColumnName = rs.getString("PKCOLUMN_NAME");
                    String fkName = rs.getString("FK_NAME");

                    if (StringHelper.isEmpty(fkName))
                        continue;

                    fkTableName = normalizeTableName(fkTableName);
                    pkTableName = normalizeTableName(pkTableName);
                    fkColumnName = normalizeColName(fkColumnName);
                    pkColumnName = normalizeColName(pkColumnName);

                    OrmEntityModel refTable = meta.getTable(pkTableName);
                    if (refTable == null)
                        continue;

                    Map<String, OrmToOneReferenceModel> tableRefs = allRefs.computeIfAbsent(fkTableName, k -> new HashMap<>());
                    OrmToOneReferenceModel ref = tableRefs.computeIfAbsent(fkName, k -> new OrmToOneReferenceModel());
                    ref.setName(StringHelper.decapitalize(refTable.getShortName()));
                    ref.setDisplayName(refTable.getShortName());
                    ref.setRefEntityName(refTable.getName());
                    ref.setConstraint(fkName);
                    ref.setTagSet(new LinkedHashSet<>(Arrays.asList("pub")));

                    OrmJoinOnModel join = new OrmJoinOnModel();
                    join.setLeftProp(StringHelper.colCodeToPropName(fkColumnName));
                    join.setRightProp(StringHelper.colCodeToPropName(pkColumnName));
                    ref.addJoinOn(join);
                }
            }
        }

        addRefs(meta, allRefs);
    }

    private void addRefs(DataBaseMeta meta, Map<String, Map<String, OrmToOneReferenceModel>> allRefs) {
        for (OrmEntityModel table : meta.getTables().values()) {
            Map<String, OrmToOneReferenceModel> tableRefs = allRefs.get(table.getTableName());
            if (tableRefs != null) {
                for (OrmToOneReferenceModel ref : tableRefs.values()) {
                    String name = guessRelationName(ref);
                    ref.setName(name);

                    while (table.getProp(name, true) != null) {
                        name = incName(name);
                    }
                    table.addProp(ref);
                }
            }
        }
    }

    String guessRelationName(OrmToOneReferenceModel ref) {
        if (ref.getJoin().size() == 1) {
            // 如果是USER_ID这种类型的关联字段
            OrmJoinOnModel join = ref.getJoin().get(0);
            if (join.getLeftProp().endsWith("Id") && join.getLeftProp().length() > 2) {
                return StringHelper.removeTail(join.getLeftProp(), "Id");
            }
        }
        return StringHelper.decapitalize(StringHelper.simpleClassName(ref.getRefEntityName()));
    }

    String incName(String name) {
        char c = name.charAt(name.length() - 1);
        if (StringHelper.isDigit(c)) {
            return name.substring(0, name.length() - 1) + (c - '0' + 1);
        }
        return name + "1";
    }

    void discoverIndexes(
            DatabaseMetaData metaData, DataBaseMeta meta,
            String catalog, String schemaPattern) throws SQLException {

        for (OrmEntityModel table : meta.getTables().values()) {
            Map<String, OrmIndexModel> idxMap = new HashMap<>();

            try (ResultSet rs = metaData.getIndexInfo(catalog, schemaPattern, table.getTableName(), false, false)) {
                while (rs.next()) {
                    String indexName = rs.getString("INDEX_NAME");
                    String columnName = rs.getString("COLUMN_NAME");
                    String tableName = rs.getString("TABLE_NAME");
                    boolean asc = "A".equals(rs.getString("ASC_OR_DESC"));
                    boolean unique = !rs.getBoolean("NON_UNIQUE");

                    if (StringHelper.isEmpty(columnName))
                        continue;

                    tableName = normalizeTableName(tableName);
                    columnName = normalizeColName(columnName);

                    OrmEntityModel refTable = meta.getTable(tableName);
                    if (refTable == null)
                        continue;

                    OrmIndexModel idx = idxMap.computeIfAbsent(indexName, k -> new OrmIndexModel());
                    idx.setUnique(unique);
                    idx.setName(indexName);
                    OrmIndexColumnModel col = new OrmIndexColumnModel();
                    col.setName(StringHelper.colCodeToPropName(columnName));
                    col.setDesc(asc ? null : true);
                    idx.addColumn(col);
                }
            }

            for (OrmIndexModel indexModel : idxMap.values()) {
                if (isPrimary(indexModel, table))
                    continue;
                table.addIndex(indexModel);
            }
        }
    }

    private boolean isPrimary(OrmIndexModel idx, OrmEntityModel table) {
        for (OrmIndexColumnModel idxCol : idx.getColumns()) {
            OrmColumnModel col = table.getColumn(idxCol.getName());
            if (col == null || !col.isPrimary())
                return false;
        }
        return true;
    }

    void discoverUniqueKeys(
            DatabaseMetaData metaData, DataBaseMeta meta,
            String catalog, String schemaPattern) throws SQLException {

        for (OrmEntityModel table : meta.getTables().values()) {
            Map<String, OrmUniqueKeyModel> idxMap = new HashMap<>();

            try (ResultSet rs = metaData.getIndexInfo(catalog, schemaPattern, table.getTableName(), true, false)) {
                while (rs.next()) {
                    String indexName = rs.getString("INDEX_NAME");
                    String columnName = rs.getString("COLUMN_NAME");
                    String tableName = rs.getString("TABLE_NAME");

                    indexName = uniqueConstraintByIndexName(indexName);
                    tableName = normalizeTableName(tableName);
                    columnName = normalizeColName(columnName);

                    OrmEntityModel refTable = meta.getTable(tableName);
                    if (refTable == null)
                        continue;

                    OrmUniqueKeyModel idx = idxMap.computeIfAbsent(indexName, k -> new OrmUniqueKeyModel());
                    idx.setName(indexName);
                    idx.setConstraint(indexName);
                    idx.addColumn(StringHelper.colCodeToPropName(columnName));
                }
            }

            for (OrmUniqueKeyModel indexModel : idxMap.values()) {
                if (isPrimary(indexModel, table))
                    continue;
                table.addUniqueKey(indexModel);

                indexModel.setColumnModels(indexModel.getColumns()
                        .stream()
                        .map(table::getColumn)
                        .collect(Collectors.toList()));
            }
        }
    }

    private boolean isPrimary(OrmUniqueKeyModel idx, OrmEntityModel table) {
        for (String colName : idx.getColumns()) {
            OrmColumnModel col = table.getColumn(colName);
            if (col == null || !col.isPrimary())
                return false;
        }
        return true;
    }

    /**
     * 根据索引名称得到唯一键约束名称
     */
    private String uniqueConstraintByIndexName(String indexName) {
        // 目前已知通过 java.sql.DatabaseMetaData.getIndexInfo
        // 获取的 H2 的唯一键的约束名（返回结果的 INDEX_NAME 列）会附加
        // _INDEX_? 形式的后缀，问号处可能是数字，也可能是字母，这里将其去除以恢复原始约束名称。
        // 注：没有通用接口可以通过 JDBC 获取到真实的约束名称
        if ("h2".equals(this.dialect.getName())) {
            indexName = indexName.replaceAll("_(INDEX|index)_.$", "");
        }

        return normalizeColName(indexName);
    }
}
