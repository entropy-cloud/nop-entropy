package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.biz.INopMetaEntityBiz;
import io.nop.metadata.biz.INopMetaEntityFieldBiz;
import io.nop.metadata.biz.INopMetaModuleBiz;
import io.nop.metadata.biz.INopMetaProfilingResultBiz;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.NopMetadataDaoConstants;
import io.nop.metadata.api.dto.AggregationResultDTO;
import io.nop.metadata.api.dto.CreateSqlViewResultDTO;
import io.nop.metadata.api.dto.PreviewSqlFieldsResultDTO;
import io.nop.metadata.api.dto.ProfileResultDTO;
import io.nop.metadata.api.dto.QueryEntityDataResultDTO;
import io.nop.metadata.api.dto.QueryJoinDataResultDTO;
import io.nop.metadata.api.dto.ResolveEntityFieldsResultDTO;
import io.nop.metadata.api.dto.ResolvedEntityFieldDTO;
import io.nop.metadata.api.dto.SqlViewFieldDTO;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaOrmModel;
import io.nop.metadata.dao.entity.NopMetaProfilingResult;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataArgs;
import io.nop.metadata.service.connection.IMetaDataSourceConnectionProcessor;
import io.nop.metadata.service.event.MetaModelChangedEventPublisher;
import io.nop.metadata.service.field.ResolvedTableField;
import io.nop.metadata.service.profiling.ProfilingSnapshot;
import io.nop.metadata.service.profiling.MetaEntityProfiler;
import io.nop.metadata.service.query.MetaAggregationExecutor;
import io.nop.metadata.service.query.MetaJoinExecutor;
import io.nop.metadata.service.query.MetaQueryContext;
import io.nop.metadata.service.NopMetadataHelper;
import io.nop.metadata.service.search.NopMetaSearchProcessor;
import io.nop.metadata.service.sqlview.SqlViewField;
import io.nop.metadata.service.sqlview.SqlViewFieldTypeInferrer;
import io.nop.metadata.service.tableref.TableReference;
import io.nop.metadata.service.tableref.TableReferenceExecutor;
import io.nop.search.api.SearchableDoc;
import io.nop.metadata.service.NopMetadataException;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@BizModel("NopMetaEntity")
public class NopMetaEntityBizModel extends CrudBizModel<NopMetaEntity> implements INopMetaEntityBiz {

    /** MA7.4-03：queryData 缺省 limit（省略时避免全表拉入内存） */
    public static final int DEFAULT_QUERY_LIMIT = 1000;

    /** MA7.4-03：queryData limit 上限（防单次查询内存放大） */
    public static final int DEFAULT_MAX_QUERY_LIMIT = 10000;

    @InjectValue(value = "@cfg:nop.metadata.query.max-limit|0")
    protected int configuredMaxQueryLimit = 0;

    private static final Logger LOG = LoggerFactory.getLogger(NopMetaEntityBizModel.class);

    @Inject
    protected IMetaDataSourceConnectionProcessor connectionService;

    @Inject
    protected MetaModelChangedEventPublisher eventPublisher;

    @Inject
    protected NopMetaSearchProcessor searchService;

    /** 跨聚合访问（plan 353 MD-1）：MetaEntityField 经 Biz 接口而非 dao 直连。 */
    @Inject
    protected INopMetaEntityFieldBiz entityFieldBiz;

    /** 跨聚合访问（plan 353 MD-1）：MetaModule 读取 / ProfilingResult 写入经 Biz 接口而非 dao 直连。 */
    @Inject
    protected INopMetaModuleBiz moduleBiz;

    @Inject
    protected INopMetaProfilingResultBiz profilingResultBiz;

    static final String EVENT_ENTITY_TYPE = "NopMetaEntity";

    private final MetaEntityProfiler profiler = new MetaEntityProfiler();
    private final MetaJoinExecutor joinExecutor = new MetaJoinExecutor();
    private final MetaAggregationExecutor aggregationExecutor = new MetaAggregationExecutor(joinExecutor);
    private final NopMetaEntityQueryAction queryAction = new NopMetaEntityQueryAction();

    private TableReferenceExecutor tableRefExecutor;

    public NopMetaEntityBizModel() {
        setEntityName(NopMetaEntity.class.getName());
    }

    @Override
    public NopMetaEntity save(@Name("data") Map<String, Object> data, IServiceContext context) {
        // P2-19（plan 2026-08-16-0226-3）：null/empty data 提前委托基类
        if (CollectionHelper.isEmptyMap(data)) {
            return super.save(data, context);
        }
        String id = NopMetadataHelper.stringOf(data, NopMetaEntity.PROP_NAME_metaEntityId);
        NopMetaEntity before = id != null ? dao().getEntityById(id) : null;
        NopMetaEntity saved = super.save(data, context);
        String eventType = before == null
                ? _NopMetadataCoreConstants.CHANGE_EVENT_TYPE_ENTITY_CREATED
                : _NopMetadataCoreConstants.CHANGE_EVENT_TYPE_ENTITY_UPDATED;
        String afterSnapshot = eventPublisher.buildSnapshot(saved, EVENT_ENTITY_TYPE, saved.getMetaEntityId());
        String beforeSnapshot = before != null
                ? eventPublisher.buildSnapshot(before, EVENT_ENTITY_TYPE, saved.getMetaEntityId()) : null;
        eventPublisher.publishEventWithSnapshots(eventType, EVENT_ENTITY_TYPE, saved.getMetaEntityId(),
                saved.getEntityName(), MetaModelChangedEventPublisher.CHANGE_SOURCE_API,
                beforeSnapshot, afterSnapshot,
                MetaModelChangedEventPublisher.newTransactionId(), context);
        searchService.addToIndex("MetaEntity", saved.getMetaEntityId(), toSearchableDoc(saved));
        return saved;
    }

    @Override
    public boolean delete(@Name("id") String id, IServiceContext context) {
        NopMetaEntity before = requireEntity(id, "delete", context);
        // AR-08（plan 2026-08-06-0553-3 Phase 3）：删除前收集子实体（MetaEntityField）id，
        // 删除后一并 removeFromIndex——级联删除的字段索引残留清理。
        List<String> fieldIds = collectEntityFieldIds(id, context);
        boolean deleted = super.delete(id, context);
        String beforeSnapshot = eventPublisher.buildSnapshot(before, EVENT_ENTITY_TYPE, id);
        eventPublisher.publishEventWithSnapshots(
                _NopMetadataCoreConstants.CHANGE_EVENT_TYPE_ENTITY_DELETED,
                EVENT_ENTITY_TYPE, id, before.getEntityName(),
                MetaModelChangedEventPublisher.CHANGE_SOURCE_API,
                beforeSnapshot, null,
                MetaModelChangedEventPublisher.newTransactionId(), context);
        // check2 P2-06：主实体清理与子实体清理统一 best-effort（fail-closed 会回滚 DB 删除 →
        // "实体留存、索引已删"分裂）
        safeRemoveFromIndex("MetaEntity", id);
        for (String fid : fieldIds) {
            safeRemoveFromIndex("MetaEntityField", fid);
        }
        return deleted;
    }

    /** 收集实体被级联删除的字段 id（NopMetaEntityField by metaEntityId，跨聚合经 Biz 接口，plan 353 MD-1）。 */
    private List<String> collectEntityFieldIds(String metaEntityId, IServiceContext context) {
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaEntityField.PROP_NAME_metaEntityId, metaEntityId));
        List<NopMetaEntityField> fields = entityFieldBiz.findList(q, null, context);
        List<String> ids = new ArrayList<>(fields.size());
        for (NopMetaEntityField f : fields) {
            ids.add(f.getEntityFieldId());
        }
        return ids;
    }

    private void safeRemoveFromIndex(String entityType, String id) {
        try {
            searchService.removeFromIndex(entityType, id);
        } catch (RuntimeException e) {
            LOG.warn("delete index cleanup failed for entityType={} id={}, errorCode={}",
                    entityType, id, NopMetadataErrors.ERR_ENTITY_SYNC_ISOLATED.getErrorCode(), e);
        }
    }

    @BizMutation
    public ProfileResultDTO profileEntity(@Name("metaEntityId") String metaEntityId,
                                          @Optional @Name("schemaPattern") String schemaPattern,
                                          @Optional @Name("columns") String columns,
                                          IServiceContext context) {
        NopMetaEntity entity = requireEntity(metaEntityId, "profile", context);
        TableReference ref = queryAction.tableRefResolver().resolve(entity,
                daoFor(NopMetaDataSource.class), daoFor(NopMetaEntity.class),
                daoFor(NopMetaEntityField.class), orm());
        String effectiveSchema = resolveDefaultSchema(schemaPattern, entity);
        ProfilingSnapshot snapshot = ensureTableRefExecutor().execute(ref,
                (conn, metaData, productName) -> profiler.profile(conn, metaData, ref, effectiveSchema, columns, productName));
        NopMetaProfilingResult row = appendProfilingResult(null, metaEntityId, snapshot, context);
        return NopMetaEntityQueryAction.buildProfileResultDTO(row, snapshot);
    }

    @BizMutation
    public CreateSqlViewResultDTO createSqlView(@Name("sql") String sql,
                                                @Name("entityName") String entityName,
                                                @Name("metaModuleId") String metaModuleId,
                                                @Optional @Name("querySpace") String querySpace,
                                                @Optional @Name("displayName") String displayName,
                                                IServiceContext context) {
        List<SqlViewField> fields = queryAction.sqlFieldExtractor().extract(sql);
        if (querySpace != null && !querySpace.trim().isEmpty()) {
            fields = queryAction.ensureSqlFieldTypeInferrer(connectionService).inferTypes(
                    fields, sql, querySpace, daoFor(NopMetaDataSource.class));
        }
        moduleBiz.requireEntity(metaModuleId, null, context);
        NopMetaOrmModel ormModel = ensureOrmModelForModule(metaModuleId);
        IEntityDao<NopMetaEntity> entityDao = dao();
        // R4.2 语义保持（plan 2261 概念缩减）：SQL 视图恒 null-schema、isDelta=0；
        // UK=(ormModelId, entityName) —— find-or-fail 守卫保持 fail-fast 语义。
        QueryBean dupQuery = new QueryBean();
        dupQuery.addFilter(FilterBeans.eq(NopMetaEntity.PROP_NAME_ormModelId, ormModel.getOrmModelId()));
        dupQuery.addFilter(FilterBeans.eq(NopMetaEntity.PROP_NAME_entityName, entityName));
        dupQuery.addFilter(FilterBeans.eq(NopMetaEntity.PROP_NAME_isDelta, (byte) 0));
        if (entityDao.findFirstByQuery(dupQuery) != null) {
            throw new NopMetadataException(NopMetadataErrors.ERR_SQL_VIEW_TABLE_EXISTS)
                    .param(NopMetadataArgs.ARG_META_MODULE_ID, metaModuleId)
                    .param(NopMetadataArgs.ARG_TABLE_NAME, entityName);
        }
        NopMetaEntity entity = entityDao.newEntity();
        entity.setMetaModuleId(metaModuleId);
        entity.setOrmModelId(ormModel.getOrmModelId());
        entity.setIsDelta((byte) 0);
        entity.setEntityName(entityName);
        entity.setTableName(entityName);
        entity.setDisplayName(displayName != null ? displayName : entityName);
        entity.setEntityKind(NopMetadataDaoConstants.ENTITY_KIND_SQL_VIEW);
        if (querySpace != null) entity.setQuerySpace(querySpace);
        entity.setSourceSql(sql);
        entityDao.saveEntity(entity);
        eventPublisher.publishEvent(
                _NopMetadataCoreConstants.CHANGE_EVENT_TYPE_ENTITY_CREATED,
                EVENT_ENTITY_TYPE, entity.getMetaEntityId(), entity.getEntityName(),
                MetaModelChangedEventPublisher.CHANGE_SOURCE_UI,
                null, entity, MetaModelChangedEventPublisher.newTransactionId(), context);
        CreateSqlViewResultDTO result = new CreateSqlViewResultDTO();
        result.setMetaEntityId(entity.getMetaEntityId());
        result.setTableName(entity.getTableName());
        result.setEntityKind(entity.getEntityKind());
        result.setFields(toSqlViewFieldDTOs(fields));
        return result;
    }

    /**
     * SQL 视图实体需要宿主 OrmModel（Entity→OrmModel→Module 归属链）。
     * 模块尚无 ORM 模型行时惰性创建（isDelta=0，modelName 固定 "sql-view"）。
     */
    private NopMetaOrmModel ensureOrmModelForModule(String metaModuleId) {
        IEntityDao<NopMetaOrmModel> ormModelDao = daoFor(NopMetaOrmModel.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaOrmModel.PROP_NAME_metaModuleId, metaModuleId));
        q.addFilter(FilterBeans.eq(NopMetaOrmModel.PROP_NAME_isDelta, (byte) 0));
        NopMetaOrmModel ormModel = ormModelDao.findFirstByQuery(q);
        if (ormModel != null) {
            return ormModel;
        }
        NopMetaOrmModel created = ormModelDao.newEntity();
        created.setMetaModuleId(metaModuleId);
        created.setIsDelta((byte) 0);
        created.setModelName("sql-view");
        ormModelDao.saveEntity(created);
        return created;
    }

    @BizQuery
    public PreviewSqlFieldsResultDTO previewSqlFields(@Name("sql") String sql, IServiceContext context) {
        List<SqlViewField> fields = queryAction.sqlFieldExtractor().extract(sql);
        PreviewSqlFieldsResultDTO result = new PreviewSqlFieldsResultDTO();
        result.setFields(toSqlViewFieldDTOs(fields));
        return result;
    }

    @BizQuery
    public ResolveEntityFieldsResultDTO resolveEntityFields(@Name("metaEntityId") String metaEntityId,
                                                            IServiceContext context) {
        NopMetaEntity entity = requireEntity(metaEntityId, "query", context);
        IEntityDao<NopMetaEntityField> fieldDao = daoFor(NopMetaEntityField.class);
        List<ResolvedTableField> fields = queryAction.fieldResolver().resolve(entity, fieldDao);
        if (NopMetadataDaoConstants.ENTITY_KIND_SQL_VIEW.equals(entity.getEntityKind())
                && entity.getQuerySpace() != null && !entity.getQuerySpace().trim().isEmpty()) {
            fields = inferResolvedSqlFieldTypes(entity, fields);
        }
        ResolveEntityFieldsResultDTO result = new ResolveEntityFieldsResultDTO();
        result.setEntityKind(entity.getEntityKind());
        result.setFields(toResolvedEntityFieldDTOs(fields));
        return result;
    }

    /**
     * F4（plan 2026-08-14-0707-2）——{@code selection} 参数契约说明：
     * GraphQL 引擎注入的响应字段选择集对本方法为显式 no-op（结果为不透明 Map，
     * 列名为 JDBC/ORM 返回键，无字段级 selection 语义）。若未来需要行列裁剪，
     * 应新增显式的 {@code fields} 参数。
     */
    @BizQuery
    public QueryEntityDataResultDTO queryData(@Name("metaEntityId") String metaEntityId,
                                              @Optional @Name("filter") TreeBean filter,
                                              @Optional @Name("limit") Long limit,
                                              @Optional @Name("offset") Long offset,
                                              @Optional @Name("selection") FieldSelectionBean selection,
                                              IServiceContext context) {
        NopMetaEntity entity = requireEntity(metaEntityId, "query", context);
        String entityKind = entity.getEntityKind();
        QueryEntityDataResultDTO result = new QueryEntityDataResultDTO();
        result.setEntityKind(entityKind);
        limit = normalizeQueryLimit(limit);
        if (entity.isPhysical()) {
            result.setItems(queryAction.queryPhysicalData(entity, filter, limit, offset, daoProvider(), orm()));
        } else if (entity.isExternal()) {
            result.setItems(queryAction.queryExternalData(entity, filter, limit, offset, connectionService, daoProvider(), orm()));
        } else if (entity.isSqlView()) {
            result.setItems(queryAction.querySqlData(entity, filter, limit, offset, connectionService, daoProvider(), orm()));
        } else {
            throw new NopMetadataException(NopMetadataErrors.ERR_QUERY_UNSUPPORTED_TABLE_TYPE)
                    .param("metaEntityId", metaEntityId)
                    .param("entityKind", String.valueOf(entityKind));
        }
        return result;
    }

    /** F4：{@code selection} 同 {@link #queryData} 的显式 no-op 契约说明。 */
    @BizQuery
    public QueryJoinDataResultDTO queryJoinData(@Name("metaEntityId") String metaEntityId,
                                                @Name("joinId") String joinId,
                                                @Optional @Name("filter") TreeBean filter,
                                                @Optional @Name("limit") Long limit,
                                                @Optional @Name("offset") Long offset,
                                                @Optional @Name("selection") FieldSelectionBean selection,
                                                IServiceContext context) {
        NopMetaEntity entity = requireEntity(metaEntityId, "query", context);
        // AR-09：拒绝点固定在 BizModel 入口——LIMIT 负值在此显式拒绝（defense-in-depth，见原裁定）。
        limit = normalizeJoinQueryLimit(limit);
        Map<String, Object> raw = joinExecutor.executeJoin(entity, joinId, filter, limit, offset, buildQueryContext());
        QueryJoinDataResultDTO result = new QueryJoinDataResultDTO();
        Object items = raw.get("items");
        if (items instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> itemsList = (List<Map<String, Object>>) items;
            result.setItems(itemsList);
        }
        return result;
    }

    /** F4：{@code selection} 同 {@link #queryData} 的显式 no-op 契约说明。 */
    @BizQuery
    public AggregationResultDTO queryAggregation(@Name("metaEntityId") String metaEntityId,
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
        NopMetaEntity entity = requireEntity(metaEntityId, "query", context);
        limit = normalizeJoinQueryLimit(limit);
        Map<String, Object> raw = aggregationExecutor.executeAggregation(entity, measures, dimensions, filter, joinId, limit, offset,
                having, orderBy, buildQueryContext());
        AggregationResultDTO result = new AggregationResultDTO();
        Object rawItems = raw.get("items");
        if (rawItems instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> itemsList = (List<Map<String, Object>>) rawItems;
            result.setItems(itemsList);
        }
        return result;
    }

    private MetaQueryContext buildQueryContext() {
        return new MetaQueryContext(daoProvider(), orm(), connectionService, ensureTableRefExecutor(),
                queryAction.dataSourceResolver(), queryAction.fieldResolver(), queryAction.filterTranslator());
    }

    private List<ResolvedTableField> inferResolvedSqlFieldTypes(NopMetaEntity entity,
                                                                List<ResolvedTableField> resolvedFields) {
        List<SqlViewField> asViewFields = new ArrayList<>(resolvedFields.size());
        for (ResolvedTableField f : resolvedFields) {
            asViewFields.add(new SqlViewField(f.getName(), null, null));
        }
        List<SqlViewField> inferred = queryAction.ensureSqlFieldTypeInferrer(connectionService).inferTypes(
                asViewFields, entity.getSourceSql(), entity.getQuerySpace(), daoFor(NopMetaDataSource.class));
        List<ResolvedTableField> out = new ArrayList<>(resolvedFields.size());
        for (int i = 0; i < resolvedFields.size(); i++) {
            ResolvedTableField orig = resolvedFields.get(i);
            out.add(new ResolvedTableField(orig.getName(), orig.getSourceType(), inferred.get(i).getType()));
        }
        return out;
    }

    NopMetaProfilingResult appendProfilingResult(String profilingRuleId, String metaEntityId,
                                                  ProfilingSnapshot snapshot, IServiceContext context) {
        NopMetaProfilingResult row = profilingResultBiz.newEntity();
        if (profilingRuleId != null) row.setProfilingRuleId(profilingRuleId);
        row.setMetaEntityId(metaEntityId);
        row.setSnapshotTime(io.nop.api.core.time.CoreMetrics.currentTimestamp());
        row.setTableStats(JsonTool.stringify(snapshot.toTableStatsMap()));
        row.setColumnStats(JsonTool.stringify(snapshot.toColumnStatsList()));
        profilingResultBiz.saveEntity(row, null, context);
        return row;
    }

    private static List<SqlViewFieldDTO> toSqlViewFieldDTOs(List<SqlViewField> fields) {
        List<SqlViewFieldDTO> list = new ArrayList<>(fields.size());
        for (SqlViewField f : fields) {
            list.add(new SqlViewFieldDTO(f.getName(), f.getAlias(), f.getType()));
        }
        return list;
    }

    private static List<ResolvedEntityFieldDTO> toResolvedEntityFieldDTOs(List<ResolvedTableField> fields) {
        List<ResolvedEntityFieldDTO> list = new ArrayList<>(fields.size());
        for (ResolvedTableField f : fields) {
            list.add(new ResolvedEntityFieldDTO(f.getName(), f.getSourceType(), f.getDataType()));
        }
        return list;
    }

    private static String resolveDefaultSchema(String schemaPattern, NopMetaEntity entity) {
        if (schemaPattern != null && !schemaPattern.trim().isEmpty()) {
            return schemaPattern;
        }
        return entity.getDbSchema();
    }

    private TableReferenceExecutor ensureTableRefExecutor() {
        if (tableRefExecutor == null) {
            tableRefExecutor = new TableReferenceExecutor(connectionService, orm());
        }
        return tableRefExecutor;
    }

    private SearchableDoc toSearchableDoc(NopMetaEntity entity) {
        return NopMetadataHelper.toSearchableDoc(entity);
    }

    /**
     * 归一化 queryData 的 limit（MA7.4-03 + INV-LIMIT）：负值显式拒绝；null/0 → 缺省值；
     * 正值超上限静默封顶（数据浏览入口语义，保持原裁定）。
     */
    private Long normalizeQueryLimit(Long limit) {
        if (limit != null && limit < 0) {
            throw new NopMetadataException(NopMetadataErrors.ERR_PAGINATION_LIMIT_INVALID)
                    .param(NopMetadataErrors.ARG_LIMIT, limit);
        }
        if (limit == null || limit == 0) {
            return (long) DEFAULT_QUERY_LIMIT;
        }
        long max = configuredMaxQueryLimit > 0 ? configuredMaxQueryLimit : DEFAULT_MAX_QUERY_LIMIT;
        return Math.min(limit, max);
    }

    /**
     * 归一化 queryJoinData / queryAggregation 的 limit（AR-09）：负值显式拒绝；
     * null/0 → 缺省值；正值原样透传，超大值由截断层显式拒绝（分析/分页入口语义，保持原裁定）。
     */
    private Long normalizeJoinQueryLimit(Long limit) {
        if (limit != null && limit < 0) {
            throw new NopMetadataException(NopMetadataErrors.ERR_PAGINATION_LIMIT_INVALID)
                    .param(NopMetadataErrors.ARG_LIMIT, limit);
        }
        if (limit == null || limit == 0) {
            return (long) DEFAULT_QUERY_LIMIT;
        }
        return limit;
    }
}
