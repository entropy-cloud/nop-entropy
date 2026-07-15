package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.biz.INopMetaTableBiz;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaProfilingResult;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.service.connection.IMetaDataSourceConnectionService;
import io.nop.metadata.service.profiling.MetaTableProfiler;
import io.nop.metadata.service.profiling.ProfilingColumnStats;
import io.nop.metadata.service.profiling.ProfilingSnapshot;
import io.nop.metadata.service.sqlview.SqlSelectFieldExtractor;
import io.nop.metadata.service.sqlview.SqlViewField;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 逻辑表 BizModel：基线 CRUD（{@link CrudBizModel}）+ 数据剖析入口（架构基线 §2.7.2 / 设计 06 §三 D3）。
 *
 * <p>剖析入口 {@link #profileTable} 是数据剖析的主入口（metaTableId 是入口键，操作对象是表，
 * 与 collectCatalog 入口风格一致）。辅助入口 {@code NopMetaProfilingRuleBizModel.executeProfilingRule}
 * 按规则定义执行，内部委托同一剖析路径。
 *
 * <p>执行机制（D3）：复用 P2-1/P2-4/P2-6 范式——BizModel action + {@code withConnection} callback + 无状态剖析器
 * （{@link MetaTableProfiler}）。
 *
 * <p>失败/不可执行路径均显式（不静默通过、不吞异常、不伪造值）：
 * <ul>
 *   <li>表不存在 → 抛 {@link #ERR_PROFILING_TABLE_NOT_FOUND}（不 NPE）</li>
 *   <li>目标表非 external（首版） → 抛 {@link #ERR_PROFILING_TABLE_NOT_EXTERNAL}</li>
 *   <li>无注册数据源 → 抛 {@link #ERR_PROFILING_NO_DATASOURCE}</li>
 *   <li>DISABLED 数据源 → 抛 {@link #ERR_PROFILING_DATASOURCE_DISABLED}</li>
 *   <li>非 jdbc 类型 → 由 {@code withConnection} 抛 UnsupportedOperationException</li>
 *   <li>单列剖析失败 → per-column try/catch 收集进 errors，不中断整表</li>
 *   <li>方言特定统计（sizeBytes/lastModified）→ null + unavailable 显式标记（不伪造）</li>
 * </ul>
 */
@BizModel("NopMetaTable")
public class NopMetaTableBizModel extends CrudBizModel<NopMetaTable> implements INopMetaTableBiz {

    private static final Logger LOG = LoggerFactory.getLogger(NopMetaTableBizModel.class);

    static final ErrorCode ERR_PROFILING_TABLE_NOT_FOUND =
            ErrorCode.define("metadata.profiling-table-not-found",
                    "Profiling target table not found: {metaTableId}", "metaTableId");
    static final ErrorCode ERR_PROFILING_TABLE_NOT_EXTERNAL =
            ErrorCode.define("metadata.profiling-table-not-external",
                    "Profiling target table is not external (first version supports external-only execution): "
                            + "{metaTableId} tableType={tableType}", "metaTableId", "tableType");
    static final ErrorCode ERR_PROFILING_NO_DATASOURCE =
            ErrorCode.define("metadata.profiling-no-datasource",
                    "No registered MetaDataSource for querySpace of target table: "
                            + "{metaTableId} querySpace={querySpace}", "metaTableId", "querySpace");
    static final ErrorCode ERR_PROFILING_DATASOURCE_DISABLED =
            ErrorCode.define("metadata.profiling-datasource-disabled",
                    "MetaDataSource is disabled, cannot profile table: {dataSourceId}", "dataSourceId");

    /** inline ErrorCode：profiling action 执行失败的通用包装。 */
    static final ErrorCode ERR_PROFILING_TABLE_FAILED =
            ErrorCode.define("metadata.profiling-table-failed",
                    "Profile table failed: {metaTableId} -- {error}", "metaTableId", "error");

    // ===== SQL 视图（createSqlTable / previewSqlFields / resolveTableFields，架构基线 §4.2）=====
    // 这些 ErrorCode 名以 sql- 前缀，与 lineage 模块的 metadata.lineage-* 区分（item 1.1 裁定：
    // 采用 sql 视图专属名，避免重名）。解析器内部 ErrorCode（sql-empty/parse-failed/multi-statement/
    // not-select/wildcard）定义在 SqlSelectFieldExtractor，BizModel 层只补充 createSqlTable/resolveTableFields
    // 入口专属的 module/table 校验 ErrorCode。
    static final ErrorCode ERR_SQL_VIEW_MODULE_NOT_FOUND =
            ErrorCode.define("metadata.sql-module-not-found",
                    "MetaModule not found for createSqlTable: {metaModuleId}", "metaModuleId");
    static final ErrorCode ERR_SQL_VIEW_TABLE_NOT_FOUND =
            ErrorCode.define("metadata.sql-table-not-found",
                    "NopMetaTable not found for resolveTableFields: {metaTableId}", "metaTableId");
    static final ErrorCode ERR_SQL_VIEW_TABLE_NOT_SQL =
            ErrorCode.define("metadata.sql-table-not-sql",
                    "resolveTableFields target is not a sql-view table: {metaTableId} (tableType={tableType})",
                    "metaTableId", "tableType");
    static final ErrorCode ERR_SQL_VIEW_SOURCE_SQL_EMPTY =
            ErrorCode.define("metadata.sql-source-sql-empty",
                    "resolveTableFields target sql-view table has empty sourceSql: {metaTableId}", "metaTableId");

    @Inject
    protected IMetaDataSourceConnectionService connectionService;

    /** 数据剖析器（无状态，参考 MetaCatalogCollector 收集器模式）。 */
    private final MetaTableProfiler profiler = new MetaTableProfiler();

    /** SELECT 字段解析器（无状态，参考 SqlSourceTableExtractor AST 遍历模式，架构基线 §4.2.1）。 */
    private final SqlSelectFieldExtractor sqlFieldExtractor = new SqlSelectFieldExtractor();

    public NopMetaTableBizModel() {
        setEntityName(NopMetaTable.class.getName());
    }

    /**
     * 数据剖析主入口（架构基线 §2.7.2 / 设计 06 §三 D3）：对 external 表的列做统计分析。
     *
     * <p>解析路径：metaTableId → NopMetaTable(external) → querySpace → NopMetaDataSource →
     * {@code withConnection} callback → 剖析器逐列统计 → 追加一行 NopMetaProfilingResult（snapshotTime=now）。
     *
     * @param metaTableId   目标逻辑表 ID（须 external 类型）
     * @param schemaPattern 可选 schema 限定（null/空串表示依赖连接默认 schema）
     * @param columns       可选，要剖析的列名（逗号分隔，null/空=所有列，运行时由 DatabaseMetaData.getColumns 解析）
     * @param context       服务上下文
     * @return {@code {profilingResultId, columnCount, unavailable:[...], errors:[...]}}
     */
    @BizMutation
    public Map<String, Object> profileTable(@Name("metaTableId") String metaTableId,
                                             @Optional @Name("schemaPattern") String schemaPattern,
                                             @Optional @Name("columns") String columns,
                                             IServiceContext context) {
        NopMetaTable table = resolveExternalTableOrThrow(metaTableId);
        NopMetaDataSource dataSource = resolveDataSourceOrThrow(table);

        // 剖析在 withConnection callback 内执行；callback 结束自动释放外部连接（本方法不自建连接）
        final ProfilingSnapshot[] holder = new ProfilingSnapshot[1];
        connectionService.withConnection(dataSource.getDatasourceType(), dataSource.getConnectionConfig(),
                (Connection conn, DatabaseMetaData metaData) -> {
                    holder[0] = profiler.profile(conn, metaData, schemaPattern,
                            table.getTableName(), columns, safeProductName(metaData));
                });

        ProfilingSnapshot snapshot = holder[0];
        // 按规则定义执行的剖析可挂规则 id；profileTable 入口无规则，留空（结果行的 profilingRuleId 可空，便于无规则直接剖析）
        NopMetaProfilingResult row = appendProfilingResult(null, metaTableId, snapshot);
        return buildResultMap(row, snapshot);
    }

    // ============================================================
    // SQL 视图创建 + SELECT 字段解析（架构基线 §4.2 / §4.2.1 / §4.2.2）
    // ============================================================

    /**
     * SQL 视图创建入口（架构基线 §4.2.2 D2）：校验 sql 为单条可解析 SELECT → 解析 SELECT 字段 →
     * 新建 {@code NopMetaTable(tableType=sql, sourceSql=sql)} → save → 返回新建表 + 解析出的字段列表。
     *
     * <p>失败路径显式（不静默存入脏数据、不静默返回空字段列表、不吞异常）：
     * <ul>
     *   <li>sql 空 / 不可解析 / 多语句 / 非 SELECT / 含通配符 → 由 {@link SqlSelectFieldExtractor} 抛 inline ErrorCode</li>
     *   <li>metaModuleId 不存在 → 抛 {@link #ERR_SQL_VIEW_MODULE_NOT_FOUND}</li>
     * </ul>
     *
     * @param sql          SELECT SQL 文本（视图定义）
     * @param tableName    逻辑表名
     * @param metaModuleId 所属模块 ID
     * @param querySpace   可选查询空间（tableType=sql 时显式指定）
     * @param displayName  可选显示名
     * @param context      服务上下文
     * @return {@code {metaTableId, tableName, tableType:"sql", fields:[{name, alias?, type?}]}}
     */
    @BizMutation
    public Map<String, Object> createSqlTable(@Name("sql") String sql,
                                               @Name("tableName") String tableName,
                                               @Name("metaModuleId") String metaModuleId,
                                               @Optional @Name("querySpace") String querySpace,
                                               @Optional @Name("displayName") String displayName,
                                               IServiceContext context) {
        // 1. 解析字段（sqlFieldExtractor 内部对 空/不可解析/多语句/非 SELECT/通配符 显式失败抛 ErrorCode）
        List<SqlViewField> fields = sqlFieldExtractor.extract(sql);

        // 2. 校验 metaModuleId 存在（不静默存入孤儿表）
        IEntityDao<NopMetaModule> moduleDao = daoFor(NopMetaModule.class);
        NopMetaModule module = moduleDao.getEntityById(metaModuleId);
        if (module == null) {
            throw new NopException(ERR_SQL_VIEW_MODULE_NOT_FOUND).param("metaModuleId", metaModuleId);
        }

        // 3. 新建 NopMetaTable(tableType=sql, sourceSql=sql)
        IEntityDao<NopMetaTable> tableDao = dao();
        NopMetaTable table = tableDao.newEntity();
        table.setMetaModuleId(metaModuleId);
        table.setTableName(tableName);
        table.setDisplayName(displayName != null ? displayName : tableName);
        table.setTableType(_NopMetadataCoreConstants.TABLE_TYPE_SQL);
        if (querySpace != null) {
            table.setQuerySpace(querySpace);
        }
        table.setSourceSql(sql);
        tableDao.saveEntity(table);

        // 4. 返回新建表 + 字段列表（接线验证：fields 来自真实 AST 解析，非空壳）
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("metaTableId", table.getMetaTableId());
        result.put("tableName", table.getTableName());
        result.put("tableType", table.getTableType());
        result.put("fields", toFieldMaps(fields));
        return result;
    }

    /**
     * SELECT 字段预览入口（架构基线 §4.2.2 D2）：对纯 SQL 文本解析 SELECT 字段，**不持久化**。
     *
     * <p>失败路径同 {@link #createSqlTable}（空/不可解析/多语句/非 SELECT/通配符显式失败）。
     *
     * @param sql     SELECT SQL 文本
     * @param context 服务上下文
     * @return {@code {fields:[{name, alias?, type?}]}}
     */
    @BizQuery
    public Map<String, Object> previewSqlFields(@Name("sql") String sql, IServiceContext context) {
        List<SqlViewField> fields = sqlFieldExtractor.extract(sql);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fields", toFieldMaps(fields));
        return result;
    }

    /**
     * 已建 sql 视图表字段解析入口（架构基线 §4.2 / §4.2.2 D2，对齐"运行时解析、不单独存储"）：
     * 加载 NopMetaTable(tableType=sql) → 解析 sourceSql → 返回字段列表。
     *
     * <p>失败路径显式（不静默返回空字段列表）：
     * <ul>
     *   <li>表不存在 → {@link #ERR_SQL_VIEW_TABLE_NOT_FOUND}</li>
     *   <li>非 sql 类型表 → {@link #ERR_SQL_VIEW_TABLE_NOT_SQL}</li>
     *   <li>sourceSql 空 → {@link #ERR_SQL_VIEW_SOURCE_SQL_EMPTY}</li>
     *   <li>sourceSql 不可解析/多语句/非 SELECT/通配符 → 由 {@link SqlSelectFieldExtractor} 抛 inline ErrorCode</li>
     * </ul>
     *
     * @param metaTableId 目标 sql 视图表 ID
     * @param context     服务上下文
     * @return {@code {fields:[{name, alias?, type?}]}}（与 {@link #previewSqlFields} 结构统一）
     */
    @BizQuery
    public Map<String, Object> resolveTableFields(@Name("metaTableId") String metaTableId,
                                                    IServiceContext context) {
        IEntityDao<NopMetaTable> tableDao = dao();
        NopMetaTable table = tableDao.getEntityById(metaTableId);
        if (table == null) {
            throw new NopException(ERR_SQL_VIEW_TABLE_NOT_FOUND).param("metaTableId", metaTableId);
        }
        if (!_NopMetadataCoreConstants.TABLE_TYPE_SQL.equals(table.getTableType())) {
            throw new NopException(ERR_SQL_VIEW_TABLE_NOT_SQL)
                    .param("metaTableId", metaTableId)
                    .param("tableType", String.valueOf(table.getTableType()));
        }
        String sourceSql = table.getSourceSql();
        if (sourceSql == null || sourceSql.trim().isEmpty()) {
            throw new NopException(ERR_SQL_VIEW_SOURCE_SQL_EMPTY).param("metaTableId", metaTableId);
        }

        List<SqlViewField> fields = sqlFieldExtractor.extract(sourceSql);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fields", toFieldMaps(fields));
        return result;
    }

    // ============================================================
    // helpers
    // ============================================================

    /** 将 SqlViewField 列表序列化为返回 Map 列表（统一 {name, alias?, type?} 结构）。 */
    private static List<Map<String, Object>> toFieldMaps(List<SqlViewField> fields) {
        List<Map<String, Object>> list = new ArrayList<>(fields.size());
        for (SqlViewField f : fields) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", f.getName());
            if (f.getAlias() != null) {
                m.put("alias", f.getAlias());
            }
            // 方案 A：type 恒为 null（不伪造）。type 字段始终写入，便于 UI/后续方案 B 统一结构。
            m.put("type", f.getType());
            list.add(m);
        }
        return list;
    }

    /** 解析目标表：metaTableId → NopMetaTable；不存在/非 external 显式失败。 */
    NopMetaTable resolveExternalTableOrThrow(String metaTableId) {
        IEntityDao<NopMetaTable> tableDao = dao();
        NopMetaTable table = tableDao.getEntityById(metaTableId);
        if (table == null) {
            throw new NopException(ERR_PROFILING_TABLE_NOT_FOUND).param("metaTableId", metaTableId);
        }
        if (!_NopMetadataCoreConstants.TABLE_TYPE_EXTERNAL.equals(table.getTableType())) {
            throw new NopException(ERR_PROFILING_TABLE_NOT_EXTERNAL)
                    .param("metaTableId", metaTableId)
                    .param("tableType", String.valueOf(table.getTableType()));
        }
        return table;
    }

    /** 解析目标表对应数据源：table.querySpace → NopMetaDataSource；不存在/DISABLED 显式失败。 */
    NopMetaDataSource resolveDataSourceOrThrow(NopMetaTable table) {
        IEntityDao<NopMetaDataSource> dsDao = daoFor(NopMetaDataSource.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaDataSource.PROP_NAME_querySpace, table.getQuerySpace()));
        NopMetaDataSource dataSource = dsDao.findFirstByQuery(q);
        if (dataSource == null) {
            throw new NopException(ERR_PROFILING_NO_DATASOURCE)
                    .param("metaTableId", table.getMetaTableId())
                    .param("querySpace", table.getQuerySpace());
        }
        if (_NopMetadataCoreConstants.DATASOURCE_STATUS_DISABLED.equals(dataSource.getStatus())) {
            throw new NopException(ERR_PROFILING_DATASOURCE_DISABLED)
                    .param("dataSourceId", dataSource.getDataSourceId());
        }
        return dataSource;
    }

    /**
     * 将剖析快照追加为一行新的 NopMetaProfilingResult（时序语义：snapshotTime=now，不覆盖旧行）。
     * tableStats/columnStats 用 JsonTool 序列化（mediumtext 列承载，已含 unavailable 标记）。
     */
    NopMetaProfilingResult appendProfilingResult(String profilingRuleId, String metaTableId,
                                                 ProfilingSnapshot snapshot) {
        IEntityDao<NopMetaProfilingResult> resultDao = daoFor(NopMetaProfilingResult.class);
        NopMetaProfilingResult row = resultDao.newEntity();
        if (profilingRuleId != null) {
            row.setProfilingRuleId(profilingRuleId);
        }
        row.setMetaTableId(metaTableId);
        row.setSnapshotTime(new Timestamp(System.currentTimeMillis()));
        row.setTableStats(JsonTool.stringify(snapshot.toTableStatsMap()));
        row.setColumnStats(JsonTool.stringify(snapshot.toColumnStatsList()));
        resultDao.saveEntity(row);
        return row;
    }

    /** 构建返回 Map（profilingResultId + 列数 + 表级不可用 + 列级 errors）。 */
    static Map<String, Object> buildResultMap(NopMetaProfilingResult row, ProfilingSnapshot snapshot) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("profilingResultId", row.getProfilingResultId());
        m.put("columnCount", snapshot.getColumnStats().size());
        m.put("unavailable", snapshot.getTableUnavailable());
        List<String> columnUnavailable = new ArrayList<>();
        for (ProfilingColumnStats cs : snapshot.getColumnStats()) {
            columnUnavailable.addAll(cs.getUnavailable());
        }
        m.put("columnUnavailable", columnUnavailable);
        m.put("errors", snapshot.getErrors());
        return m;
    }

    private static String safeProductName(DatabaseMetaData metaData) {
        try {
            return metaData.getDatabaseProductName();
        } catch (SQLException e) {
            LOG.warn("getDatabaseProductName failed, product name will be absent from tableStats", e);
            return null;
        }
    }
}
