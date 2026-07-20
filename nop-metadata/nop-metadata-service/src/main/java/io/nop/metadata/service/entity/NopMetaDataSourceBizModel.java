package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
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
import io.nop.metadata.biz.INopMetaDataSourceBiz;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaCatalog;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.service.catalog.CatalogTableStats;
import io.nop.metadata.service.catalog.MetaCatalogCollector;
import io.nop.metadata.service.connection.IMetaDataSourceConnectionService;
import io.nop.metadata.service.datasource.MetaDataSourceResolver;
import io.nop.metadata.service.event.MetaModelChangedEventPublisher;
import io.nop.metadata.service.field.MetaTableFieldResolver;
import io.nop.metadata.service.sync.ExternalColumnInfo;
import io.nop.metadata.service.sync.ExternalTableInfo;
import io.nop.metadata.service.sync.ExternalTableStructureReader;
import io.nop.metadata.service.tableref.MetaTableReferenceResolver;
import io.nop.metadata.service.tableref.TableReference;
import io.nop.metadata.service.tableref.TableReferenceExecutor;
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
import java.util.concurrent.atomic.AtomicInteger;

@BizModel("NopMetaDataSource")
public class NopMetaDataSourceBizModel extends CrudBizModel<NopMetaDataSource> implements INopMetaDataSourceBiz {

    private static final Logger LOG = LoggerFactory.getLogger(NopMetaDataSourceBizModel.class);

    /** 外部表归属的系统模块 moduleId（架构基线 §2.5.1 方案 A）。由 syncExternalTables 惰性初始化。 */
    static final String EXTERNAL_MODULE_ID = "nop/meta-external";
    static final String EXTERNAL_MODULE_NAME = "meta-external";

    static final ErrorCode ERR_DATASOURCE_NOT_FOUND =
            ErrorCode.define("metadata.datasource-not-found",
                    "DataSource not found: {dataSourceId}", "dataSourceId");
    /** AR-14 / 维度09-11：collectCatalogForTable 找不到 metaTableId 时用专属 ErrorCode（原误用 ERR_DATASOURCE_NOT_FOUND）。 */
    public static final ErrorCode ERR_TABLE_NOT_FOUND =
            ErrorCode.define("metadata.table-not-found",
                    "Meta table not found: {metaTableId}", "metaTableId");
    static final ErrorCode ERR_DATASOURCE_DISABLED =
            ErrorCode.define("metadata.datasource-disabled",
                    "DataSource is disabled, cannot test connection: {dataSourceId}", "dataSourceId");

    @Inject
    protected IMetaDataSourceConnectionService connectionService;

    /** 元数据变更事件发布 helper（架构基线 §2.8 D2，IoC bean）。 */
    @Inject
    protected MetaModelChangedEventPublisher eventPublisher;

    /** 事件 entityType（架构基线 §2.8 D3）。 */
    static final String EVENT_ENTITY_TYPE = "NopMetaDataSource";

    /** 外部表结构读取器（无状态，直接持有，与 OrmModelImporter 的持有方式一致）。 */
    private final ExternalTableStructureReader structureReader = new ExternalTableStructureReader();

    /** Catalog 运行时统计收集器（无状态，在 callback 内调用，不自建连接）。 */
    private final MetaCatalogCollector catalogCollector = new MetaCatalogCollector();

    /** 共享 table-reference 解析器（架构基线 §4.4.3 D3）。 */
    private final MetaTableReferenceResolver tableRefResolver = new MetaTableReferenceResolver(
            new MetaDataSourceResolver(), new MetaTableFieldResolver());

    /** 按 table-reference 形态分派 Connection 获取（§4.4.3 D1/D2）。延迟初始化（需 orm()）。 */
    private TableReferenceExecutor tableRefExecutor;

    public NopMetaDataSourceBizModel() {
        setEntityName(NopMetaDataSource.class.getName());
    }

    /**
     * 连通性验证：按 dataSourceId 加载 → 校验非 DISABLED → 调连接服务建连并读取 DatabaseMetaData。
     *
     * <p>设计决策 D1：
     * <ul>
     *   <li>实体不存在抛 {@code metadata.datasource-not-found}（不 NPE）</li>
     *   <li>DISABLED 抛 {@code metadata.datasource-disabled}（不静默通过）</li>
     *   <li>非 jdbc 类型抛 {@link UnsupportedOperationException}（不静默返回成功）</li>
     *   <li>connectionConfig 缺必填字段抛 {@code metadata.datasource-config-invalid}（快速失败）</li>
     *   <li>建连失败（SQLException）catch 后返回 {@code {connected:false, error}}，不向上抛，
     *       使 GraphQL 调用方拿到结构化失败结果</li>
     * </ul>
     *
     * <p>设计决策 D3：成功时从 DatabaseMetaData 识别的产品名放入返回 Map，不写回任何 ORM 列。
     */
    @BizMutation
    public Map<String, Object> testConnection(@Name("dataSourceId") String dataSourceId, IServiceContext context) {
        NopMetaDataSource dataSource = dao().getEntityById(dataSourceId);
        if (dataSource == null) {
            throw new NopException(ERR_DATASOURCE_NOT_FOUND).param("dataSourceId", dataSourceId);
        }

        String status = dataSource.getStatus();
        if (_NopMetadataCoreConstants.DATASOURCE_STATUS_DISABLED.equals(status)) {
            throw new NopException(ERR_DATASOURCE_DISABLED).param("dataSourceId", dataSourceId);
        }

        return connectionService.testConnect(dataSource.getDatasourceType(), dataSource.getConnectionConfig());
    }

    /**
     * 外部表元数据同步：从已注册数据源扫描物理表结构 → 写入元数据目录。
     *
     * <p>设计决策 D2（架构基线 §2.5.1）：
     * <ul>
     *   <li>实体不存在抛 {@code metadata.datasource-not-found}（不 NPE）</li>
     *   <li>DISABLED 抛 {@code metadata.datasource-disabled}（不静默通过）</li>
     *   <li>非 jdbc 类型由连接服务抛 {@link UnsupportedOperationException}（不静默成功）</li>
     *   <li>不支持的方言由读取器抛 {@link UnsupportedOperationException}（不静默跳过）</li>
     *   <li>复用 P2-1 callback 式连接服务 {@code withConnection}：callback 内运行时取方言 + 扫描，
     *       callback 结束自动释放外部连接（本方法不自建连接）</li>
     *   <li>按 D1 方案 A 写入：tableType=external，metaModuleId 指向系统模块 nop/meta-external，
     *       列结构序列化为 JSON 存入 buildSql（子方案 A2）</li>
     *   <li>幂等 upsert：按 (metaModuleId, schema, tableName) 复合键去重（plan 0852-3 收敛自
     *       {@code (metaModuleId, tableName)}），同名不同 schema 不再互相覆盖；重复同步更新而非追加</li>
     *   <li>单表失败收集到 errors 不中断整批（flushSession 隔离 + clearSession 清理失败态）</li>
     * </ul>
     *
     * @param dataSourceId  目标数据源 ID
     * @param schemaPattern 可选，限定扫描的 schema（null/空串表示全部）
     * @return {@code {syncedTableCount: int, errors: [{tableName, error}, ...]}}
     */
    @BizMutation
    public Map<String, Object> syncExternalTables(@Name("dataSourceId") String dataSourceId,
                                                  @Optional @Name("schemaPattern") String schemaPattern,
                                                  IServiceContext context) {
        NopMetaDataSource dataSource = dao().getEntityById(dataSourceId);
        if (dataSource == null) {
            throw new NopException(ERR_DATASOURCE_NOT_FOUND).param("dataSourceId", dataSourceId);
        }

        String status = dataSource.getStatus();
        if (_NopMetadataCoreConstants.DATASOURCE_STATUS_DISABLED.equals(status)) {
            throw new NopException(ERR_DATASOURCE_DISABLED).param("dataSourceId", dataSourceId);
        }

        // 系统模块作为外部表归属（方案 A），首次同步时惰性创建
        String externalModuleId = ensureExternalSystemModule();

        AtomicInteger syncedCount = new AtomicInteger(0);
        List<Map<String, Object>> errors = new ArrayList<>();

        // 复用 P2-1 callback 式建连：callback 内取方言 + 扫描；callback 结束自动释放连接
        connectionService.withConnection(dataSource.getDatasourceType(), dataSource.getConnectionConfig(),
                (Connection conn, DatabaseMetaData metaData) -> {
                    List<ExternalTableInfo> tables = structureReader.read(conn, metaData, schemaPattern);
                    for (ExternalTableInfo table : tables) {
                        try {
                            upsertExternalTable(externalModuleId, dataSource, table);
                            orm().flushSession();
                            syncedCount.incrementAndGet();
                        } catch (Exception e) {
                            LOG.error("syncExternalTables failed for table: {}", table.getTableName(), e);
                            Map<String, Object> err = new LinkedHashMap<>();
                            err.put("tableName", table.getTableName());
                            err.put("error", toErrorMessage(e));
                            errors.add(err);
                            // 隔离失败：清理未刷出的脏实体，不影响已 flush 的表与后续表
                            orm().clearSession();
                        }
                    }
                });

        // 元数据变更事件（架构基线 §2.8 D3）：外部表同步是批量操作，主实体级记录 1 行 DataSource UPDATED
        // （changeSource=SYNC，表述「外部表已同步」）。子实体细粒度事件（per-table）deferred。
        // 事件行在持久化成功后写入，避免幽灵事件。before 为同步前的数据源快照。
        String beforeSnapshot = eventPublisher.buildSnapshot(dataSource, EVENT_ENTITY_TYPE, dataSourceId);
        orm().flushSession();
        String afterSnapshot = eventPublisher.buildSnapshot(dataSource, EVENT_ENTITY_TYPE, dataSourceId);
        eventPublisher.publishEventWithSnapshots(
                _NopMetadataCoreConstants.CHANGE_EVENT_TYPE_ENTITY_UPDATED,
                EVENT_ENTITY_TYPE, dataSourceId, dataSource.getName(),
                MetaModelChangedEventPublisher.CHANGE_SOURCE_SYNC,
                beforeSnapshot, afterSnapshot,
                MetaModelChangedEventPublisher.newTransactionId(), context);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("syncedTableCount", syncedCount.get());
        result.put("errors", errors);
        return result;
    }

    /**
     * Catalog 运行时统计收集：从已注册数据源收集该 querySpace 下 external 表的物理运行时统计
     * （行数/索引/…），每次收集追加为新的时序快照行写入 NopMetaCatalog。
     *
     * <p>设计决策 D2（架构基线 §2.3.2 / 设计 05 §4.6）：
     * <ul>
     *   <li>实体不存在抛 {@code metadata.datasource-not-found}（不 NPE）</li>
     *   <li>DISABLED 抛 {@code metadata.datasource-disabled}（不静默通过）</li>
     *   <li>非 jdbc 类型由连接服务抛 {@link UnsupportedOperationException}（不静默成功）</li>
     *   <li>复用 P2-1 callback 式连接服务 {@code withConnection}：callback 内运行时取方言 + 逐表收集，
     *       callback 结束自动释放外部连接（本方法不自建连接）</li>
     *   <li>统计不可用（sizeBytes/partitionCount/lastModified 等方言特定）记 null +
     *       {@code details.unavailable} 显式标记（不静默跳过整行、不伪造 0）</li>
     *   <li>时序语义：每表每收集一次追加新行（collectedAt=now），不覆盖旧行</li>
     *   <li>单表失败（SQL 异常）收集到 errors 不中断整批（flushSession 隔离 + clearSession 清理失败态）</li>
     * </ul>
     *
     * @param dataSourceId  目标数据源 ID
     * @param schemaPattern 可选，限定 COUNT/索引查询的物理 schema（null/空串表示**逐表默认解析**：
     *                      各表取自身 {@code NopMetaTable.schema}，plan 0852-3 Phase 3）。
     *                      显式入参对该批次所有表覆盖默认；null=各表用持久化 schema（仍 null 则不过滤）。
     * @return {@code {collectedCount: int, errors: [{tableName, error}, ...]}}
     */
    @BizMutation
    public Map<String, Object> collectCatalog(@Name("dataSourceId") String dataSourceId,
                                              @Optional @Name("schemaPattern") String schemaPattern,
                                              IServiceContext context) {
        NopMetaDataSource dataSource = dao().getEntityById(dataSourceId);
        if (dataSource == null) {
            throw new NopException(ERR_DATASOURCE_NOT_FOUND).param("dataSourceId", dataSourceId);
        }

        String status = dataSource.getStatus();
        if (_NopMetadataCoreConstants.DATASOURCE_STATUS_DISABLED.equals(status)) {
            throw new NopException(ERR_DATASOURCE_DISABLED).param("dataSourceId", dataSourceId);
        }

        // 该 querySpace 下已目录化的 external 表（NopMetaTable 无 schema 列，按 querySpace+tableType 查找，
        // 首版全表扫描可接受；schemaPattern 仅限定物理 SQL，不过滤目录行——见架构基线 §2.3.2）。
        List<NopMetaTable> externalTables = findExternalTables(dataSource.getQuerySpace());

        AtomicInteger collectedCount = new AtomicInteger(0);
        List<Map<String, Object>> errors = new ArrayList<>();

        // 复用 P2-1 callback 式建连：callback 内取方言 + 逐表收集；callback 结束自动释放连接
        connectionService.withConnection(dataSource.getDatasourceType(), dataSource.getConnectionConfig(),
                (Connection conn, DatabaseMetaData metaData) -> {
                    String productName = safeProductName(metaData);
                    for (NopMetaTable table : externalTables) {
                        try {
                            // 构建 external table-reference（批内表共享同一数据源连接）
                            TableReference ref = new TableReference(TableReference.Kind.EXTERNAL,
                                    table.getMetaTableId(), table.getTableName(), null,
                                    dataSource, null, null, null);
                            // plan 0852-3 Phase 3: 批量入口逐表默认 schema 解析（每表 schema 可能不同）
                            // 替代旧「单一 schemaPattern 透传循环内所有表」——多 schema 数据源各表正确命中
                            String effectiveSchema = resolveDefaultSchema(schemaPattern, table);
                            CatalogTableStats stats = catalogCollector.collectForTable(
                                    conn, metaData, ref, effectiveSchema, productName);
                            appendCatalogRow(table.getMetaTableId(), stats);
                            orm().flushSession();
                            collectedCount.incrementAndGet();
                        } catch (Exception e) {
                            LOG.error("collectCatalog failed for table: {}", table.getTableName(), e);
                            Map<String, Object> err = new LinkedHashMap<>();
                            err.put("tableName", table.getTableName());
                            err.put("error", toErrorMessage(e));
                            errors.add(err);
                            // 隔离失败：清理未刷出的脏实体，不影响已 flush 的表与后续表
                            orm().clearSession();
                        }
                    }
                });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("collectedCount", collectedCount.get());
        result.put("errors", errors);
        return result;
    }

    /** 查找该 querySpace 下所有 external 类型逻辑表（按 tableType=external 限定）。 */
    private List<NopMetaTable> findExternalTables(String querySpace) {
        IEntityDao<NopMetaTable> tableDao = daoFor(NopMetaTable.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(NopMetaTable.PROP_NAME_querySpace, querySpace));
        query.addFilter(FilterBeans.eq(NopMetaTable.PROP_NAME_tableType,
                _NopMetadataCoreConstants.TABLE_TYPE_EXTERNAL));
        return tableDao.findAllByQuery(query);
    }

    /**
     * 单表 Catalog 收集入口（架构基线 §4.4.3 D1-D5）：对任意 tableType（external/entity/sql）的逻辑表
     * 收集运行时统计（行数/索引），追加为新的时序快照行写入 NopMetaCatalog。
     *
     * <p>解析路径（D3）：metaTableId → NopMetaTable → {@link MetaTableReferenceResolver} → {@link TableReference}
     * → {@link TableReferenceExecutor} 按 ref 形态分派 Connection → 收集器收集 → 追加 NopMetaCatalog 行。
     *
     * @param metaTableId   目标逻辑表 ID（任意 tableType）
     * @param schemaPattern 可选 schema 限定（null/空串表示依赖连接默认 schema；sql 子查询忽略）
     * @param context       服务上下文
     * @return {@code {metaTableId, rowCount, indexCount, unavailable:[...]}}
     */
    @BizMutation
    public Map<String, Object> collectCatalogForTable(@Name("metaTableId") String metaTableId,
                                                       @Optional @Name("schemaPattern") String schemaPattern,
                                                       IServiceContext context) {
        IEntityDao<NopMetaTable> tableDao = daoFor(NopMetaTable.class);
        NopMetaTable table = tableDao.getEntityById(metaTableId);
        if (table == null) {
            // AR-14 / 维度09-11：错误码语义错配修正——按 metaTableId 查不到表应抛 ERR_TABLE_NOT_FOUND，
            // 原代码误用 ERR_DATASOURCE_NOT_FOUND 且把 metaTableId 写入 dataSourceId 参数，让运维误判为数据源问题。
            throw new NopException(ERR_TABLE_NOT_FOUND).param("metaTableId", metaTableId);
        }
        TableReference ref = tableRefResolver.resolve(table,
                daoFor(NopMetaDataSource.class), daoFor(NopMetaEntity.class),
                daoFor(NopMetaEntityField.class), orm());

        // plan 0852-3 Phase 3: 默认 schema 解析在 BizModel 层（持有 NopMetaTable）
        // 未显式传 schemaPattern 且 table.schema 非空 → 默认取 table.schema（持久化一次、多次执行无需重传）
        String effectiveSchema = resolveDefaultSchema(schemaPattern, table);

        CatalogTableStats stats = ensureTableRefExecutor().execute(ref,
                (conn, metaData, productName) -> catalogCollector.collectForTable(
                        conn, metaData, ref, effectiveSchema, productName));

        appendCatalogRow(table.getMetaTableId(), stats);
        return buildCatalogResultMap(table.getMetaTableId(), stats);
    }

    /**
     * 默认 schema 解析（plan 0852-3 Phase 3）：未显式传 schemaPattern（null/空/纯空白）且
     * {@code table.schema} 非空 → 默认取 {@code table.schema}；否则维持入参（可能为 null=不过滤）。
     *
     * <p>解析点在 BizModel 层（持有 NopMetaTable），执行器仅收最终 schemaPattern——
     * 使「sync 持久化一次 schema → 后续 Catalog/Quality/Profiling 执行无需重传」成立。
     * 显式入参优先覆盖持久化 schema。
     */
    private static String resolveDefaultSchema(String schemaPattern, NopMetaTable table) {
        if (schemaPattern != null && !schemaPattern.trim().isEmpty()) {
            return schemaPattern;
        }
        return table.getSchema();
    }

    /** 延迟初始化 TableReferenceExecutor（需 orm()，构造时 orm 不可用）。 */
    private TableReferenceExecutor ensureTableRefExecutor() {
        if (tableRefExecutor == null) {
            tableRefExecutor = new TableReferenceExecutor(connectionService, orm());
        }
        return tableRefExecutor;
    }

    private static Map<String, Object> buildCatalogResultMap(String metaTableId, CatalogTableStats stats) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("metaTableId", metaTableId);
        m.put("rowCount", stats.getRowCount());
        m.put("indexCount", stats.getIndexCount());
        m.put("unavailable", stats.getUnavailable());
        m.putAll(stats.getExtras());
        return m;
    }

    /**
     * 将单表收集结果追加为一行新的 NopMetaCatalog 快照（时序语义：collectedAt=now，不覆盖旧行）。
     * details JSON 承载 unavailable 标记 + 方言特定字段。
     */
    private void appendCatalogRow(String metaTableId, CatalogTableStats stats) {
        IEntityDao<NopMetaCatalog> catalogDao = daoFor(NopMetaCatalog.class);
        NopMetaCatalog row = catalogDao.newEntity();
        row.setMetaTableId(metaTableId);
        row.setRowCount(stats.getRowCount());
        row.setSizeBytes(stats.getSizeBytes());
        row.setIndexCount(stats.getIndexCount());
        row.setPartitionCount(stats.getPartitionCount());
        row.setLastModified(stats.getLastModified());
        row.setCollectedAt(new Timestamp(System.currentTimeMillis()));
        row.setDetails(buildDetailsJson(stats));
        catalogDao.saveEntity(row);
    }

    /** details JSON：{unavailable: [...], databaseProductName: ...}，承载不可用标记 + 方言特定字段。 */
    private String buildDetailsJson(CatalogTableStats stats) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (!stats.getUnavailable().isEmpty()) {
            details.put("unavailable", stats.getUnavailable());
        }
        if (!stats.getExtras().isEmpty()) {
            details.putAll(stats.getExtras());
        }
        return JsonTool.stringify(details);
    }

    private static String safeProductName(DatabaseMetaData metaData) {
        try {
            return metaData.getDatabaseProductName();
        } catch (SQLException e) {
            LOG.warn("getDatabaseProductName failed, product name will be absent from details", e);
            return null;
        }
    }

    /**
     * 幂等 upsert：按 (metaModuleId, schema, tableName) 复合键去重（plan 2026-07-17-0852-3 收敛自
     * {@code (metaModuleId, tableName)}，使同一数据源下不同 schema 的同名表可区分、互不覆盖）。
     * 存在则更新（querySpace/description/buildSql/schema 列快照），不存在则新建。
     *
     * <p><b>跨数据源行为（Decision，plan 0852-3 Phase 2）</b>：去重键仍**不含 querySpace**——
     * 跨数据源、同名同 schema 的表会互相覆盖（与 1905-1 收敛前语义一致）。仅 schema 维度被纳入，
     * 使「同数据源不同 schema 同名表」可区分；「跨数据源同名同 schema」的 querySpace 维度纳入属
     * follow-up（非阻塞理由：应用层 upsert 仍幂等，迁移需评估跨数据源覆盖语义破坏面）。
     *
     * <p><b>实现说明（EQL 关键字规避）</b>：{@code SCHEMA} 在 EQL 文法中是 reserved keyword，
     * {@code o.schema=?} 解析失败。故先按 EQL-safe 的 {@code (metaModuleId, tableName)} 拉候选集，
     * 再在 Java 层按 schema 精确匹配（{@code null==null}）。schema=null 用 {@link #normalizeSchemaForMatch}
     * 归一为 null，使「无 schema」与「无 schema」匹配。
     */
    private void upsertExternalTable(String metaModuleId, NopMetaDataSource dataSource, ExternalTableInfo info) {
        IEntityDao<NopMetaTable> tableDao = daoFor(NopMetaTable.class);

        // EQL-safe 查询：仅按 (metaModuleId, tableName) 拉候选集（schema 维度在 Java 层过滤）
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(NopMetaTable.PROP_NAME_metaModuleId, metaModuleId));
        query.addFilter(FilterBeans.eq(NopMetaTable.PROP_NAME_tableName, info.getTableName()));
        List<NopMetaTable> candidates = tableDao.findAllByQuery(query);

        // Java 层 schema 精确匹配（normalizeSchemaForMatch 将 null/空串归一为 null，使 null==null 成立）
        String infoSchema = normalizeSchemaForMatch(info.getSchema());
        NopMetaTable table = null;
        for (NopMetaTable candidate : candidates) {
            if (java.util.Objects.equals(normalizeSchemaForMatch(candidate.getSchema()), infoSchema)) {
                table = candidate;
                break;
            }
        }

        String columnsJson = serializeColumns(info.getColumns());

        if (table == null) {
            table = tableDao.newEntity();
            table.setMetaModuleId(metaModuleId);
            table.setTableName(info.getTableName());
            table.setSchema(info.getSchema());
            table.setDisplayName(info.getTableName());
            table.setTableType(_NopMetadataCoreConstants.TABLE_TYPE_EXTERNAL);
            table.setQuerySpace(dataSource.getQuerySpace());
            table.setDescription(info.getRemark());
            table.setBuildSql(columnsJson);
            tableDao.saveEntity(table);
        } else {
            table.setSchema(info.getSchema());
            table.setQuerySpace(dataSource.getQuerySpace());
            table.setDescription(info.getRemark());
            table.setBuildSql(columnsJson);
            tableDao.updateEntity(table);
        }
    }

    /** schema 归一化用于匹配：null/空串/纯空白 → null（使「无 schema」行互相匹配）。 */
    private static String normalizeSchemaForMatch(String schema) {
        if (schema == null || schema.trim().isEmpty()) {
            return null;
        }
        return schema;
    }

    /**
     * 确保外部表归属的系统模块存在（moduleId=nop/meta-external，status=RELEASED）。
     * 已存在则复用其 metaModuleId，不存在则惰性创建。
     */
    private String ensureExternalSystemModule() {
        IEntityDao<NopMetaModule> moduleDao = daoFor(NopMetaModule.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(NopMetaModule.PROP_NAME_moduleId, EXTERNAL_MODULE_ID));
        NopMetaModule module = moduleDao.findFirstByQuery(query);
        if (module != null) {
            return module.getMetaModuleId();
        }

        module = moduleDao.newEntity();
        module.setModuleId(EXTERNAL_MODULE_ID);
        module.setModuleName(EXTERNAL_MODULE_NAME);
        module.setDisplayName("外部表系统模块");
        module.setModuleVersion(1L);
        module.setStatus(_NopMetadataCoreConstants.MODULE_STATUS_RELEASED);
        module.setImportedAt(new Timestamp(System.currentTimeMillis()));
        moduleDao.saveEntity(module);
        orm().flushSession();
        return module.getMetaModuleId();
    }

    /** 将扫描到的列结构序列化为 JSON 数组存入 buildSql（子方案 A2）。 */
    private String serializeColumns(List<ExternalColumnInfo> columns) {
        List<Map<String, Object>> list = new ArrayList<>(columns.size());
        for (ExternalColumnInfo col : columns) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("columnName", col.getColumnName());
            m.put("dataType", col.getDataType());
            m.put("precision", col.getPrecision());
            m.put("scale", col.getScale());
            m.put("nullable", col.isNullable());
            m.put("ordinal", col.getOrdinal());
            if (col.getRemark() != null) {
                m.put("comment", col.getRemark());
            }
            if (col.getDefaultValue() != null) {
                m.put("defaultValue", col.getDefaultValue());
            }
            list.add(m);
        }
        return JsonTool.stringify(list);
    }

    private static String toErrorMessage(Exception e) {
        String msg = e.getMessage();
        return msg != null ? msg : e.getClass().getName();
    }
}
