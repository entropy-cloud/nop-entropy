package io.nop.metadata.service.entity;


import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.OrderFieldBean;
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
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaProfilingResult;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.service.connection.IMetaDataSourceConnectionProcessor;
import io.nop.metadata.service.datasource.MetaDataSourceResolver;
import io.nop.metadata.service.event.MetaModelChangedEventPublisher;
import io.nop.metadata.service.field.MetaTableFieldResolver;
import io.nop.metadata.service.field.ResolvedTableField;
import io.nop.metadata.service.profiling.MetaTableProfiler;
import io.nop.metadata.service.profiling.ProfilingColumnStats;
import io.nop.metadata.service.profiling.ProfilingSnapshot;
import io.nop.metadata.service.query.FilterToSqlTranslator;
import io.nop.metadata.service.query.MetaAggregationExecutor;
import io.nop.metadata.service.query.MetaJoinExecutor;
import io.nop.metadata.service.query.MetaQueryContext;
import io.nop.metadata.service.query.MetaTableQueryExecutor;
import io.nop.metadata.service.query.SqlPagination;
import io.nop.metadata.service.sqlview.SqlSelectFieldExtractor;
import io.nop.metadata.service.sqlview.SqlViewField;
import io.nop.metadata.service.sqlview.SqlViewFieldTypeInferrer;
import io.nop.metadata.service.tableref.MetaTableReferenceResolver;
import io.nop.metadata.service.tableref.TableReference;
import io.nop.metadata.service.tableref.TableReferenceExecutor;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 *   <li>非 jdbc 类型 → 由 {@code withConnection} 抛 NopException</li>
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

    // ===== 单表数据查询（queryTableData，架构基线 §4.4 D1/D2）=====
    static final ErrorCode ERR_QUERY_TABLE_NOT_FOUND =
            ErrorCode.define("metadata.query-table-not-found",
                    "NopMetaTable not found for queryTableData: {metaTableId}", "metaTableId");
    static final ErrorCode ERR_QUERY_UNSUPPORTED_TABLE_TYPE =
            ErrorCode.define("metadata.query-unsupported-table-type",
                    "Unsupported tableType for queryTableData: {metaTableId} tableType={tableType}",
                    "metaTableId", "tableType");
    static final ErrorCode ERR_QUERY_ENTITY_NOT_FOUND =
            ErrorCode.define("metadata.query-entity-not-found",
                    "Entity record not found for entity table (baseEntityId dangling): "
                            + "{metaTableId} baseEntityId={baseEntityId}", "metaTableId", "baseEntityId");
    static final ErrorCode ERR_QUERY_ENTITY_NOT_REGISTERED =
            ErrorCode.define("metadata.query-entity-not-registered",
                    "Entity is not registered in runtime IOrmSessionFactory (cannot query, would be silent empty set): "
                            + "{metaTableId} entityName={entityName}", "metaTableId", "entityName");
    static final ErrorCode ERR_QUERY_SQL_SOURCE_EMPTY =
            ErrorCode.define("metadata.query-sql-source-empty",
                    "sql table sourceSql is empty, cannot query: {metaTableId}", "metaTableId");
    static final ErrorCode ERR_QUERY_UNSUPPORTED_DIALECT =
            ErrorCode.define("metadata.query-unsupported-dialect",
                    "Dialect not supported in first version (only H2/MySQL/PostgreSQL): "
                            + "{databaseProductName} metaTableId={metaTableId}",
                    "databaseProductName", "metaTableId");
    static final ErrorCode ERR_QUERY_SQL_EXEC_FAILED =
            ErrorCode.define("metadata.query-sql-exec-failed",
                    "Query SQL execution failed: metaTableId={metaTableId} -- {error}",
                    "metaTableId", "error");

    @Inject
    protected IMetaDataSourceConnectionProcessor connectionService;

    /** 元数据变更事件发布 helper（架构基线 §2.8 D2，IoC bean）。 */
    @Inject
    protected MetaModelChangedEventPublisher eventPublisher;

    /** 事件 entityType（架构基线 §2.8 D3）。 */
    static final String EVENT_ENTITY_TYPE = "NopMetaTable";

    /** 数据剖析器（无状态，参考 MetaCatalogCollector 收集器模式）。 */
    private final MetaTableProfiler profiler = new MetaTableProfiler();

    /** SELECT 字段解析器（无状态，参考 SqlSourceTableExtractor AST 遍历模式，架构基线 §4.2.1）。 */
    private final SqlSelectFieldExtractor sqlFieldExtractor = new SqlSelectFieldExtractor();

    /**
     * 跨表类型字段解析器（架构基线 §2.5.2 D2）：按 tableType 分派解析可用字段（entity/external/sql），
     * 是 Measure/Dimension/Join 字段引用校验的基础，也是 {@link #resolveTableFields} 的核心。
     */
    private final MetaTableFieldResolver fieldResolver = new MetaTableFieldResolver(sqlFieldExtractor);

    /** querySpace→NopMetaDataSource 解析共享组件（架构基线 §4.4 D2）。 */
    private final MetaDataSourceResolver dataSourceResolver = new MetaDataSourceResolver();

    /**
     * SQL 视图字段类型推断器（架构基线 §4.2.1 方案 B，plan 0900-1）：querySpace 可达时经 LIMIT 0
     * + ResultSetMetaData 推断字段类型，补全 {@link SqlViewField#getType()}（方案 A 下恒为 null）。
     * 独立组件，不修改 {@link SqlSelectFieldExtractor} / {@code MetaTableFieldResolver}（plan R1 M3 + R2 N1）。
     *
     * <p>延迟初始化：{@link #connectionService} 经 {@code @Inject} 注入，构造时不可用，故仿照 {@link #tableRefExecutor}
     * 用 {@code ensureXxx()} 模式按需构造。
     */
    private SqlViewFieldTypeInferrer sqlFieldTypeInferrer;

    /** 共享 table-reference 解析器（架构基线 §4.4.3 D3）。 */
    private final MetaTableReferenceResolver tableRefResolver = new MetaTableReferenceResolver(
            dataSourceResolver, fieldResolver);

    /** 按 table-reference 形态分派 Connection 获取（§4.4.3 D1/D2）。延迟初始化（需 orm()）。 */
    private TableReferenceExecutor tableRefExecutor;

    /** TreeBean filter→SQL WHERE 翻译器（架构基线 §4.4 D1，复用 §2.7.1 D3 注入防护）。无状态。 */
    private final FilterToSqlTranslator filterTranslator = new FilterToSqlTranslator();

    /** 跨表 JOIN 执行器（架构基线 §4.4.1，D3/D4/D5）。 */
    private final MetaJoinExecutor joinExecutor = new MetaJoinExecutor();

    /**
     * 指标/维度聚合执行器（架构基线 §4.4.2，D6/D7 + plan 0852-1 entity↔entity JOIN 聚合）。
     * 复用 {@link #joinExecutor} 的 join 加载/端点解析（显式选定「抽取共享」，见 plan 0852-1 Decision）。
     */
    private final MetaAggregationExecutor aggregationExecutor = new MetaAggregationExecutor(joinExecutor);

    /** external/sql 查询路径首版支持的方言（LIMIT/OFFSET 便携语法，架构基线 §4.4 D1）。 */
    private static final Set<String> SUPPORTED_QUERY_DIALECTS =
            Collections.unmodifiableSet(new java.util.HashSet<>(Arrays.asList("H2", "MySQL", "PostgreSQL")));

    public NopMetaTableBizModel() {
        setEntityName(NopMetaTable.class.getName());
    }

    /**
     * save override（架构基线 §2.8 D3）：通用 CRUD 路径的 CREATE/UPDATE 事件发布。
     *
     * <p>before 快照在 super.save 前按 PK 加载（null=CREATE，非 null=UPDATE）；事件行在 super.save 成功后写入
     * （避免幽灵事件）。per-op UUID 作为 transactionId。本 override 覆盖通用 CRUD（UI/GraphQL/xbiz）；
     * {@link #createSqlTable} 等关键 mutation action 自行调 helper（不经本 override），二者独立。
     *
     * <p>plan 2026-07-19-1250-3 Phase 5 维度14-02 裁定：事件表写入仍在事务内（保证回滚一致，
     * {@code NopMetaModelChangedEvent} 行作为业务数据落入 NOP_META_MODEL_CHANGED_EVENT 表）；
     * 「通知/外部副作用」afterCommit 钩子首版不引入（保留单值裁定记录在此，未来接入消息系统时统一改造）。
     */
    @Override
    public NopMetaTable save(@Name("data") Map<String, Object> data, IServiceContext context) {
        String id = data == null ? null : stringOf(data, NopMetaTable.PROP_NAME_metaTableId);
        NopMetaTable before = id != null ? dao().getEntityById(id) : null;
        NopMetaTable saved = super.save(data, context);
        String eventType = before == null
                ? _NopMetadataCoreConstants.CHANGE_EVENT_TYPE_ENTITY_CREATED
                : _NopMetadataCoreConstants.CHANGE_EVENT_TYPE_ENTITY_UPDATED;
        String afterSnapshot = eventPublisher.buildSnapshot(saved, EVENT_ENTITY_TYPE, saved.getMetaTableId());
        String beforeSnapshot = before != null
                ? eventPublisher.buildSnapshot(before, EVENT_ENTITY_TYPE, saved.getMetaTableId()) : null;
        eventPublisher.publishEventWithSnapshots(eventType, EVENT_ENTITY_TYPE, saved.getMetaTableId(),
                saved.getTableName(), MetaModelChangedEventPublisher.CHANGE_SOURCE_API,
                beforeSnapshot, afterSnapshot,
                MetaModelChangedEventPublisher.newTransactionId(), context);
        return saved;
    }

    /**
     * delete override（架构基线 §2.8 D3）：通用 CRUD 路径的 DELETE 事件发布。save 不覆盖 delete，DELETE 走独立 override。
     *
     * <p>before 快照在 super.delete 前按 PK 加载；事件行在 super.delete 成功后写入。若实体不存在则不发事件。
     */
    @Override
    public boolean delete(@Name("id") String id, IServiceContext context) {
        NopMetaTable before = dao().getEntityById(id);
        boolean deleted = super.delete(id, context);
        if (before != null) {
            String beforeSnapshot = eventPublisher.buildSnapshot(before, EVENT_ENTITY_TYPE, id);
            eventPublisher.publishEventWithSnapshots(
                    _NopMetadataCoreConstants.CHANGE_EVENT_TYPE_ENTITY_DELETED,
                    EVENT_ENTITY_TYPE, id, before.getTableName(),
                    MetaModelChangedEventPublisher.CHANGE_SOURCE_API,
                    beforeSnapshot, null,
                    MetaModelChangedEventPublisher.newTransactionId(), context);
        }
        return deleted;
    }

    private static String stringOf(Map<String, Object> data, String key) {
        Object v = data.get(key);
        return v == null ? null : v.toString();
    }

    /**
     * 数据剖析主入口（架构基线 §2.7.2 / 设计 06 §三 D3 + §4.4.3 D1-D5）：对任意 tableType
     * （external/entity/sql）的逻辑表的列做统计分析。
     *
     * <p>解析路径（D3）：metaTableId → NopMetaTable → {@link MetaTableReferenceResolver} → {@link TableReference}
     * → {@link TableReferenceExecutor} 按 ref 形态分派 Connection（external/sql 经 withConnection，
     * entity 经平台 IJdbcTransaction）→ 剖析器逐列统计 → 追加一行 NopMetaProfilingResult（snapshotTime=now）。
     *
     * @param metaTableId   目标逻辑表 ID（任意 tableType）
     * @param schemaPattern 可选 schema 限定（null/空串表示依赖连接默认 schema；sql 子查询忽略）
     * @param columns       可选，要剖析的列名（逗号分隔，null/空=所有列）
     * @param context       服务上下文
     * @return {@code {profilingResultId, columnCount, unavailable:[...], errors:[...]}}
     */
    @BizMutation
    public Map<String, Object> profileTable(@Name("metaTableId") String metaTableId,
                                             @Optional @Name("schemaPattern") String schemaPattern,
                                             @Optional @Name("columns") String columns,
                                             IServiceContext context) {
        NopMetaTable table = resolveTableOrThrow(metaTableId);
        TableReference ref = tableRefResolver.resolve(table,
                daoFor(NopMetaDataSource.class), daoFor(NopMetaEntity.class),
                daoFor(NopMetaEntityField.class), orm());

        // plan 0852-3 Phase 3: 默认 schema 解析在 BizModel 层（持有 NopMetaTable）
        // 未显式传 schemaPattern 且 table.schema 非空 → 默认取 table.schema（持久化一次、多次执行无需重传）
        String effectiveSchema = resolveDefaultSchema(schemaPattern, table);

        // 按 ref 形态分派 Connection 获取（external/sql 经 withConnection，entity 经平台 IJdbcTransaction）
        ProfilingSnapshot snapshot = ensureTableRefExecutor().execute(ref,
                (conn, metaData, productName) -> profiler.profile(conn, metaData, ref, effectiveSchema, columns, productName));

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

        // 1b. plan 0900-1（方案 B）：querySpace 提供 时显式调 inferrer 推断 type（无"或"歧义，R2 N2 修复）。
        // querySpace 为空 → type=null（方案 A，不变）。失败路径（连接/方言/SQL 执行）显式抛 ErrorCode（不静默 fallback）。
        if (querySpace != null && !querySpace.trim().isEmpty()) {
            fields = ensureSqlFieldTypeInferrer().inferTypes(
                    fields, sql, querySpace, daoFor(NopMetaDataSource.class));
        }

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

        // 元数据变更事件（架构基线 §2.8 D3）：SQL 视图创建记 1 行 Table CREATED（changeSource=UI）。
        // 事件行在持久化成功后写入，避免幽灵事件。
        eventPublisher.publishEvent(
                _NopMetadataCoreConstants.CHANGE_EVENT_TYPE_ENTITY_CREATED,
                EVENT_ENTITY_TYPE, table.getMetaTableId(), table.getTableName(),
                MetaModelChangedEventPublisher.CHANGE_SOURCE_UI,
                null, table, MetaModelChangedEventPublisher.newTransactionId(), context);

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
     * 跨类型字段解析入口（架构基线 §2.5.2 D2 + §4.2.2 D2）：加载 NopMetaTable → 按 {@code tableType} 分派
     * 解析可用字段（entity→{@link NopMetaEntityField}；external→{@code buildSql} JSON 列；
     * sql→{@link SqlSelectFieldExtractor}）→ 返回统一字段列表。
     *
     * <p>本方法由 plan 0700-1 的 sql-only 版本扩展为全 {@code tableType} 分派版本（item 1.2b 裁定，
     * 架构基线 §2.5.2 D2 ownership）。entity/external 分派独立可用；sql 分派复用 0700-1 的解析器。
     *
     * <p>失败路径显式（不静默返回空字段列表、不静默跳过）：
     * <ul>
     *   <li>表不存在 → {@link #ERR_SQL_VIEW_TABLE_NOT_FOUND}</li>
     *   <li>entity 表 baseEntityId 为 null → 由 {@link MetaTableFieldResolver} 抛
     *       {@code metadata.field-resolve-base-entity-null}</li>
     *   <li>external 表 buildSql JSON 损坏/非数组/为空 → 由 {@link MetaTableFieldResolver} 抛
     *       {@code metadata.field-resolve-external-build-sql-invalid}</li>
     *   <li>sql 表 sourceSql 空/不可解析/多语句/非 SELECT/通配符 → 由 {@link MetaTableFieldResolver} 经
     *       {@link SqlSelectFieldExtractor} 抛 inline ErrorCode</li>
     * </ul>
     *
     * @param metaTableId 目标逻辑表 ID（任意 tableType）
     * @param context     服务上下文
     * @return {@code {tableType, fields:[{name, sourceType, type?}]}}（统一结构，{@code type} 可能为 null）
     */
    @BizQuery
    public Map<String, Object> resolveTableFields(@Name("metaTableId") String metaTableId,
                                                    IServiceContext context) {
        IEntityDao<NopMetaTable> tableDao = dao();
        NopMetaTable table = tableDao.getEntityById(metaTableId);
        if (table == null) {
            throw new NopException(ERR_SQL_VIEW_TABLE_NOT_FOUND).param("metaTableId", metaTableId);
        }
        // 跨 tableType 分派：entity/external/sql 各自解析；解析失败由 resolver 显式抛 ErrorCode
        IEntityDao<NopMetaEntityField> fieldDao = daoFor(NopMetaEntityField.class);
        List<ResolvedTableField> fields = fieldResolver.resolve(table, fieldDao);

        // plan 0900-1（方案 B）：sql 表 querySpace 非空时，作为 resolve 之后的独立补全步骤调 inferrer
        // 补 type（R2 N1 修复——MetaTableFieldResolver 不改，类型推断在 BizModel 层完成）。
        // querySpace 为空 → type=null（方案 A，不变）。
        if (_NopMetadataCoreConstants.TABLE_TYPE_SQL.equals(table.getTableType())
                && table.getQuerySpace() != null && !table.getQuerySpace().trim().isEmpty()) {
            fields = inferResolvedSqlFieldTypes(table, fields);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tableType", table.getTableType());
        result.put("fields", toResolvedFieldMaps(fields));
        return result;
    }

    /**
     * 方案 B 类型推断（plan 0900-1）：对 resolveTableFields 返回的 sql 表 ResolvedTableField（type=null）
     * 调 {@link SqlViewFieldTypeInferrer} 推断真实类型，按 D3 列序对齐重构造 ResolvedTableField。
     *
     * <p>实现：把 ResolvedTableField 退化为 SqlViewField（仅取 name/alias）→ 调 inferrer → 取补全后的 type
     * 重构造。**MetaTableFieldResolver 不改**（R2 N1）。
     */
    private List<ResolvedTableField> inferResolvedSqlFieldTypes(NopMetaTable table,
                                                                  List<ResolvedTableField> resolvedFields) {
        // 退化为 SqlViewField（仅 name/alias，type=null）供 inferrer 消费；alias 在 resolveTableFields 后未知，
        // 取 null（不影响 type 推断——inferrer 只按列序对齐 ResultSetMetaData）。
        List<SqlViewField> asViewFields = new ArrayList<>(resolvedFields.size());
        for (ResolvedTableField f : resolvedFields) {
            asViewFields.add(new SqlViewField(f.getName(), null, null));
        }
        List<SqlViewField> inferred = ensureSqlFieldTypeInferrer().inferTypes(
                asViewFields, table.getSourceSql(), table.getQuerySpace(), daoFor(NopMetaDataSource.class));
        // 按列序对齐重构造 ResolvedTableField（保留 sourceType，更新 dataType）
        List<ResolvedTableField> out = new ArrayList<>(resolvedFields.size());
        for (int i = 0; i < resolvedFields.size(); i++) {
            ResolvedTableField orig = resolvedFields.get(i);
            out.add(new ResolvedTableField(orig.getName(), orig.getSourceType(), inferred.get(i).getType()));
        }
        return out;
    }

    // ============================================================
    // 单表数据查询 queryTableData（架构基线 §4.4 D1/D2）
    // ============================================================

    /**
     * 统一单表数据查询入口（架构基线 §4.4 D1）：按 tableType 三路分派——
     * <ul>
     *   <li><b>entity</b> → 经平台 {@code IOrmTemplate}：{@code QueryBean.sourceName=entityName}+{@code orm().findListByQuery}
     *       （实体须注册于运行时 {@code IOrmSessionFactory}，否则显式失败）</li>
     *   <li><b>external</b> → 经 {@code withConnection} 跑限定物理表名的原生 SELECT（querySpace→NopMetaDataSource）</li>
     *   <li><b>sql</b> → 经 {@code withConnection} 执行 sourceSql（包一层子查询 + WHERE/LIMIT/OFFSET，D2）</li>
     * </ul>
     *
     * <p>{@code filter} 为平台 TreeBean filter 树（与 §2.5.2 D1 {@code MetaTableFilter.definition} 同结构，非整个 QueryBean；
     * 过滤只是 QueryBean 的 {@code filter} 子树）。external/sql 路径的 filter→WHERE 翻译复用 §2.7.1 D3 注入防护
     * （标识符白名单 + PreparedStatement 值绑定）。方言范围 H2/MySQL/PostgreSQL。
     *
     * <p>失败路径显式（不静默空集、不吞异常）：表不存在 / querySpace 无数据源 / DISABLED / 非 jdbc（withConnection 抛）/
     * sql querySpace null 或无匹配 / 实体未注册 / 不支持的方言 / sourceSql 空 / 未知 tableType 均显式失败。
     *
     * @param metaTableId 目标逻辑表 ID（任意 tableType）
     * @param filter      可选，平台 TreeBean filter 树
     * @param limit       可选分页上限（null 表示不限）
     * @param offset      可选分页偏移（null 表示 0）
     * @param context     服务上下文
     * @return {@code {tableType, items:[{行数据}]}}
     */
    @BizQuery
    public Map<String, Object> queryTableData(@Name("metaTableId") String metaTableId,
                                               @Optional @Name("filter") TreeBean filter,
                                               @Optional @Name("limit") Long limit,
                                               @Optional @Name("offset") Long offset,
                                               @Optional @Name("selection") FieldSelectionBean selection,
                                               IServiceContext context) {
        // plan 维度12-01：FieldSelectionBean 参数注入（首版忽略，仅在接口契约上明确；后续 slice 下推到执行器）
        IEntityDao<NopMetaTable> tableDao = dao();
        NopMetaTable table = tableDao.getEntityById(metaTableId);
        if (table == null) {
            throw new NopException(ERR_QUERY_TABLE_NOT_FOUND).param("metaTableId", metaTableId);
        }
        String tableType = table.getTableType();
        if (_NopMetadataCoreConstants.TABLE_TYPE_ENTITY.equals(tableType)) {
            return queryEntityTable(table, filter, limit, offset);
        }
        if (_NopMetadataCoreConstants.TABLE_TYPE_EXTERNAL.equals(tableType)) {
            return queryExternalTable(table, filter, limit, offset);
        }
        if (_NopMetadataCoreConstants.TABLE_TYPE_SQL.equals(tableType)) {
            return querySqlTable(table, filter, limit, offset);
        }
        // 未知 tableType → 显式失败（No Silent No-Op Rule）
        throw new NopException(ERR_QUERY_UNSUPPORTED_TABLE_TYPE)
                .param("metaTableId", metaTableId)
                .param("tableType", String.valueOf(tableType));
    }

    // ============================================================
    // 跨表 JOIN + 指标/维度聚合查询（架构基线 §4.4.1/§4.4.2，plan 0800-2）
    // ============================================================

    /**
     * 跨表 JOIN 查询（架构基线 §4.4.1 D3/D4/D5）。
     *
     * <p>按 {@code joinId} 指定的 {@code NopMetaTableJoin} 关联多表：同库（同 querySpace）走原生 JOIN SQL，
     * 跨库（不同 querySpace）走应用层拼接。{@code joinType=right} 首版显式不支持。
     *
     * @param metaTableId 左表（join 所属逻辑表）
     * @param joinId      NopMetaTableJoin 主键
     * @param filter      可选 filter（TreeBean，左表属性名字段）
     * @param limit       可选分页/截断上限
     * @param offset      可选分页偏移
     * @param context     服务上下文
     * @return {@code {items:[{行数据}]}}
     */
    @BizQuery
    public Map<String, Object> queryJoinData(@Name("metaTableId") String metaTableId,
                                              @Name("joinId") String joinId,
                                              @Optional @Name("filter") TreeBean filter,
                                              @Optional @Name("limit") Long limit,
                                              @Optional @Name("offset") Long offset,
                                              @Optional @Name("selection") FieldSelectionBean selection,
                                              IServiceContext context) {
        // plan 维度12-01：FieldSelectionBean 参数注入（首版忽略；后续 slice 下推到执行器）
        IEntityDao<NopMetaTable> tableDao = dao();
        NopMetaTable table = tableDao.getEntityById(metaTableId);
        if (table == null) {
            throw new NopException(ERR_QUERY_TABLE_NOT_FOUND).param("metaTableId", metaTableId);
        }
        return joinExecutor.executeJoin(table, joinId, filter, limit, offset, buildQueryContext());
    }

    /**
     * 指标/维度聚合查询（架构基线 §4.4.2 D6/D7 + plan 0852-1 entity↔entity JOIN 聚合）。
     *
     * <p>按选定 Measures + Dimensions 生成聚合 SQL（GROUP BY 维度 + aggFunc 指标），时间维度按 granularity 分桶。
     * {@code expression} 型 Measure 首版显式不支持。
     *
     * <p>{@code joinId} 可选（plan 0852-1）：提供时对 {@code NopMetaTableJoin} 定义的 entity↔entity 关联执行
     * 跨表聚合（所选 Measure/Dimension 可来自左 entity 或经 JOIN 可达的右 entity）；为空时维持单表聚合既有行为。
     * 仅同库 entity↔entity 支持；external/sql 端点 / 跨库 / self-join / right / 字段不可归属 / EQL 保留字 显式失败。
     *
     * @param metaTableId    目标逻辑表
     * @param measures       选定指标名列表（NopMetaTableMeasure.measureName）
     * @param dimensions     选定维度名列表（NopMetaTableDimension.dimensionName）
     * @param filter         可选 filter（TreeBean）
     * @param joinId         可选 NopMetaTableJoin 主键（null/空 → 单表聚合）
     * @param limit          可选分页上限
     * @param offset         可选分页偏移
     * @param having         可选 having（TreeBean，聚合后过滤；plan 2026-07-18-0900-2）
     * @param orderBy        可选 orderBy（List<OrderFieldBean>，按 measure/dimension 排序；plan 2026-07-18-0900-2）
     * @param context        服务上下文
     * @return {@code {items:[{维度值, 指标聚合值}]}}
     */
    @BizQuery
    public Map<String, Object> queryAggregation(@Name("metaTableId") String metaTableId,
                                                   @Name("measures") List<String> measures,
                                                   @Name("dimensions") List<String> dimensions,
                                                   @Optional @Name("filter") TreeBean filter,
                                                   @Optional @Name("joinId") String joinId,
                                                   @Optional @Name("limit") Long limit,
                                                   @Optional @Name("offset") Long offset,
                                                   @Optional @Name("having") TreeBean having,
                                                   @Optional @Name("orderBy") List<OrderFieldBean> orderBy,
                                                   @Optional @Name("selection") FieldSelectionBean selection,
                                                   IServiceContext context) {
        // plan 维度12-01：FieldSelectionBean 参数注入（首版忽略；后续 slice 下推到执行器）
        IEntityDao<NopMetaTable> tableDao = dao();
        NopMetaTable table = tableDao.getEntityById(metaTableId);
        if (table == null) {
            throw new NopException(ERR_QUERY_TABLE_NOT_FOUND).param("metaTableId", metaTableId);
        }
        return aggregationExecutor.executeAggregation(table, measures, dimensions, filter, joinId, limit, offset,
                having, orderBy, buildQueryContext());
    }

    /** 构造 JOIN/聚合执行器共享的依赖上下文（取本 BizModel 已注入的组件）。 */
    private MetaQueryContext buildQueryContext() {
        return new MetaQueryContext(daoProvider(), orm(), connectionService, ensureTableRefExecutor(), dataSourceResolver,
                fieldResolver, filterTranslator);
    }

    // ---- entity 分派：经 IOrmTemplate（架构基线 §4.4 D1）----

    private Map<String, Object> queryEntityTable(NopMetaTable table, TreeBean filter, Long limit, Long offset) {
        String baseEntityId = table.getBaseEntityId();
        if (baseEntityId == null || baseEntityId.isEmpty()) {
            // baseEntityId 悬空 → 显式失败（不静默空集）
            throw new NopException(ERR_QUERY_ENTITY_NOT_FOUND)
                    .param("metaTableId", table.getMetaTableId())
                    .param("baseEntityId", String.valueOf(baseEntityId));
        }
        IEntityDao<NopMetaEntity> metaEntityDao = daoFor(NopMetaEntity.class);
        NopMetaEntity entity = metaEntityDao.getEntityById(baseEntityId);
        if (entity == null) {
            throw new NopException(ERR_QUERY_ENTITY_NOT_FOUND)
                    .param("metaTableId", table.getMetaTableId())
                    .param("baseEntityId", baseEntityId);
        }
        String entityName = entity.getEntityName();
        // 前置：实体须注册于运行时 IOrmSessionFactory，否则显式失败（不静默空集）
        if (entityName == null || entityName.isEmpty() || !orm().isValidEntityName(entityName)) {
            throw new NopException(ERR_QUERY_ENTITY_NOT_REGISTERED)
                    .param("metaTableId", table.getMetaTableId())
                    .param("entityName", String.valueOf(entityName));
        }
        // 经平台 ORM：实体 querySpace 字段已承担路由（§设计结论 #9 + §七），无额外抽象层。
        // 按实体名取其 IEntityDao（运行时已注册），findAllByQuery 走 ORM 查询（filter/limit/offset 委托 QueryBean）。
        @SuppressWarnings({"rawtypes", "unchecked"})
        io.nop.orm.dao.IOrmEntityDao<io.nop.orm.IOrmEntity> targetDao =
                (io.nop.orm.dao.IOrmEntityDao<io.nop.orm.IOrmEntity>) (io.nop.orm.dao.IOrmEntityDao) daoProvider().dao(entityName);
        QueryBean query = new QueryBean();
        if (filter != null) {
            query.setFilter(filter);
        }
        if (limit != null) {
            query.setLimit(limit.intValue());
        }
        if (offset != null) {
            query.setOffset(offset.intValue());
        }
        List<io.nop.orm.IOrmEntity> entities = targetDao.findAllByQuery(query);
        // 取实体列名集合作为投影（与 MetaTableFieldResolver entity 分派字段集合一致）
        io.nop.orm.model.IEntityModel entityModel = targetDao.getEntityModel();
        List<String> propNames = new ArrayList<>();
        for (io.nop.orm.model.IColumnModel col : entityModel.getColumns()) {
            propNames.add(col.getName());
        }
        List<Map<String, Object>> items = new ArrayList<>(entities.size());
        for (io.nop.orm.IOrmEntity row : entities) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (String prop : propNames) {
                map.put(prop, row.orm_propValueByName(prop));
            }
            items.add(map);
        }
        return buildQueryResult(_NopMetadataCoreConstants.TABLE_TYPE_ENTITY, items);
    }

    // ---- external 分派：withConnection 跑限定表名原生 SQL（架构基线 §4.4 D1）----

    private Map<String, Object> queryExternalTable(NopMetaTable table, TreeBean filter, Long limit, Long offset) {
        NopMetaDataSource dataSource = resolveQueryDataSourceOrThrow(table);
        // 列名取自 buildSql JSON 的 columnName（架构基线 §4.4 D1）；解析失败由 fieldResolver 显式抛 ErrorCode
        IEntityDao<NopMetaEntityField> fieldDao = daoFor(NopMetaEntityField.class);
        List<ResolvedTableField> fields = fieldResolver.resolve(table, fieldDao);
        List<String> columns = new ArrayList<>(fields.size());
        for (ResolvedTableField f : fields) {
            columns.add(f.getName());
        }

        final List<Map<String, Object>>[] holder = newArrayHolder();
        connectionService.withConnection(dataSource.getDatasourceType(), dataSource.getConnectionConfig(),
                (Connection conn, DatabaseMetaData metaData) -> {
                    String productName = safeProductName(metaData);
                    requireSupportedDialect(productName, table.getMetaTableId());
                    FilterToSqlTranslator.TranslatedFilter tf = filterTranslator.translate(filter);
                    String sql = buildExternalSelectSql(table.getTableName(), columns, tf.getSql(), limit, offset, productName);
                    holder[0] = executeQuery(conn, sql, tf.getParams(), limit, offset);
                });
        return buildQueryResult(_NopMetadataCoreConstants.TABLE_TYPE_EXTERNAL, holder[0]);
    }

    // ---- sql 分派：withConnection 执行 sourceSql 子查询（架构基线 §4.4 D1 + D2）----

    private Map<String, Object> querySqlTable(NopMetaTable table, TreeBean filter, Long limit, Long offset) {
        String sourceSql = table.getSourceSql();
        if (sourceSql == null || sourceSql.trim().isEmpty()) {
            // sourceSql 空 → 显式失败（不静默空集）
            throw new NopException(ERR_QUERY_SQL_SOURCE_EMPTY).param("metaTableId", table.getMetaTableId());
        }
        // D2：querySpace 必须非 null 且匹配 NopMetaDataSource（平台 ORM querySpace fallback 首版不做）
        NopMetaDataSource dataSource = resolveQueryDataSourceOrThrow(table);

        final List<Map<String, Object>>[] holder = newArrayHolder();
        connectionService.withConnection(dataSource.getDatasourceType(), dataSource.getConnectionConfig(),
                (Connection conn, DatabaseMetaData metaData) -> {
                    String productName = safeProductName(metaData);
                    requireSupportedDialect(productName, table.getMetaTableId());
                    FilterToSqlTranslator.TranslatedFilter tf = filterTranslator.translate(filter);
                    String sql = buildSqlSelectSql(sourceSql, tf.getSql(), limit, offset, productName);
                    holder[0] = executeQuery(conn, sql, tf.getParams(), limit, offset);
                });
        return buildQueryResult(_NopMetadataCoreConstants.TABLE_TYPE_SQL, holder[0]);
    }

    /** querySpace→数据源解析（external + sql 共用），失败时附加 metaTableId 上下文。 */
    private NopMetaDataSource resolveQueryDataSourceOrThrow(NopMetaTable table) {
        IEntityDao<NopMetaDataSource> dsDao = daoFor(NopMetaDataSource.class);
        try {
            return dataSourceResolver.resolveActiveOrThrow(dsDao, table.getQuerySpace());
        } catch (NopException e) {
            // 附加 metaTableId 上下文（resolver 仅含 querySpace/dataSourceId），便于诊断
            if (e.getParam("metaTableId") == null) {
                e.param("metaTableId", table.getMetaTableId());
            }
            throw e;
        }
    }

    /** 方言范围检查：首版仅 H2/MySQL/PostgreSQL（LIMIT/OFFSET 便携语法）。其他方言显式失败（不静默跳过）。 */
    private void requireSupportedDialect(String databaseProductName, String metaTableId) {
        if (databaseProductName == null || !SUPPORTED_QUERY_DIALECTS.contains(databaseProductName)) {
            throw new NopException(ERR_QUERY_UNSUPPORTED_DIALECT)
                    .param("databaseProductName", String.valueOf(databaseProductName))
                    .param("metaTableId", metaTableId);
        }
    }

    /**
     * 构建 external 路径 SELECT SQL。
     *
     * @deprecated plan 2026-07-19-1250-3 Phase 3：委托到 {@link MetaTableQueryExecutor#buildExternalSelectSql}。
     * 保留本方法仅为 BizModel 内部调用兼容；后续 slice 整体迁移后移除。
     */
    @Deprecated
    private static String buildExternalSelectSql(String tableName, List<String> columns,
                                                  String filterSql, Long limit, Long offset, String dialect) {
        return MetaTableQueryExecutor.buildExternalSelectSql(tableName, columns, filterSql, limit, offset, dialect);
    }

    /**
     * 构建 sql 路径 SELECT SQL。
     *
     * @deprecated plan 2026-07-19-1250-3 Phase 3：委托到 {@link MetaTableQueryExecutor#buildSqlSelectSql}。
     */
    @Deprecated
    private static String buildSqlSelectSql(String sourceSql, String filterSql, Long limit, Long offset, String dialect) {
        return MetaTableQueryExecutor.buildSqlSelectSql(sourceSql, filterSql, limit, offset, dialect);
    }

    /**
     * 执行查询 SQL。
     *
     * @deprecated plan 2026-07-19-1250-3 Phase 3：委托到 {@link MetaTableQueryExecutor#executeQuery}。
     */
    @Deprecated
    private static List<Map<String, Object>> executeQuery(Connection conn, String sql, List<Object> filterParams,
                                                           Long limit, Long offset) {
        return MetaTableQueryExecutor.executeQuery(conn, sql, filterParams, limit, offset);
    }

    private static Map<String, Object> buildQueryResult(String tableType, List<Map<String, Object>> items) {
        return MetaTableQueryExecutor.buildQueryResult(tableType, items);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>>[] newArrayHolder() {
        return MetaTableQueryExecutor.newArrayHolder();
    }

    /** 提取异常消息（null 时回退类名），避免 catch 块直接 e.getMessage() 丢失堆栈。 */
    private static String messageOf(Throwable t) {
        return MetaTableQueryExecutor.messageOf(t);
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

    /** 将 ResolvedTableField 列表序列化为返回 Map 列表（跨 tableType 统一 {name, sourceType, type?} 结构）。 */
    private static List<Map<String, Object>> toResolvedFieldMaps(List<ResolvedTableField> fields) {
        List<Map<String, Object>> list = new ArrayList<>(fields.size());
        for (ResolvedTableField f : fields) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", f.getName());
            m.put("sourceType", f.getSourceType());
            // type 可能为 null（sql 表首版不取类型、entity 字段 stdSqlType 可空）——不伪造，按原值返回
            m.put("type", f.getDataType());
            list.add(m);
        }
        return list;
    }

    /** 解析目标表：metaTableId → NopMetaTable；不存在显式失败（任意 tableType）。 */
    NopMetaTable resolveTableOrThrow(String metaTableId) {
        IEntityDao<NopMetaTable> tableDao = dao();
        NopMetaTable table = tableDao.getEntityById(metaTableId);
        if (table == null) {
            throw new NopException(ERR_PROFILING_TABLE_NOT_FOUND).param("metaTableId", metaTableId);
        }
        return table;
    }

    /**
     * 默认 schema 解析（plan 0852-3 Phase 3）：未显式传 schemaPattern（null/空/纯空白）且
     * {@code table.schema} 非空 → 默认取 {@code table.schema}；否则维持入参（可能为 null=不过滤）。
     * 与 {@code NopMetaDataSourceBizModel.resolveDefaultSchema} 同语义。
     */
    private static String resolveDefaultSchema(String schemaPattern, NopMetaTable table) {
        if (schemaPattern != null && !schemaPattern.trim().isEmpty()) {
            return schemaPattern;
        }
        return table.getSchema();
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

    /** 延迟初始化 TableReferenceExecutor（需 orm()，构造时 orm 不可用）。 */
    private TableReferenceExecutor ensureTableRefExecutor() {
        if (tableRefExecutor == null) {
            tableRefExecutor = new TableReferenceExecutor(connectionService, orm());
        }
        return tableRefExecutor;
    }

    /** 延迟初始化 SqlViewFieldTypeInferrer（需 connectionService，构造时 @Inject 未完成）。 */
    private SqlViewFieldTypeInferrer ensureSqlFieldTypeInferrer() {
        if (sqlFieldTypeInferrer == null) {
            sqlFieldTypeInferrer = new SqlViewFieldTypeInferrer(dataSourceResolver, connectionService);
        }
        return sqlFieldTypeInferrer;
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
        row.setSnapshotTime(CoreMetrics.currentTimestamp());
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
