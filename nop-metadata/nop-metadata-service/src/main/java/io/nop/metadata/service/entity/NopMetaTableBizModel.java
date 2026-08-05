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
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.biz.INopMetaTableBiz;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.api.dto.AggregationResultDTO;
import io.nop.metadata.api.dto.CreateSqlTableResultDTO;
import io.nop.metadata.api.dto.PreviewSqlFieldsResultDTO;
import io.nop.metadata.api.dto.ProfileResultDTO;
import io.nop.metadata.api.dto.QueryJoinDataResultDTO;
import io.nop.metadata.api.dto.QueryTableDataResultDTO;
import io.nop.metadata.api.dto.ResolveTableFieldsResultDTO;
import io.nop.metadata.api.dto.ResolvedTableFieldDTO;
import io.nop.metadata.api.dto.SqlViewFieldDTO;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaProfilingResult;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataArgs;
import io.nop.metadata.service.connection.IMetaDataSourceConnectionProcessor;
import io.nop.metadata.service.event.MetaModelChangedEventPublisher;
import io.nop.metadata.service.field.ResolvedTableField;
import io.nop.metadata.service.profiling.MetaTableProfiler;
import io.nop.metadata.service.profiling.ProfilingSnapshot;
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
import java.util.Set;

@BizModel("NopMetaTable")
public class NopMetaTableBizModel extends CrudBizModel<NopMetaTable> implements INopMetaTableBiz {

    /** MA7.4-03：queryTableData 缺省 limit（省略时避免全表拉入内存） */
    public static final int DEFAULT_QUERY_LIMIT = 1000;

    /** MA7.4-03：queryTableData limit 上限（防单次查询内存放大） */
    public static final int DEFAULT_MAX_QUERY_LIMIT = 10000;

    @InjectValue(value = "@cfg:nop.metadata.query.max-limit|0")
    protected int configuredMaxQueryLimit = 0;

    private static final Logger LOG = LoggerFactory.getLogger(NopMetaTableBizModel.class);

    @Inject
    protected IMetaDataSourceConnectionProcessor connectionService;

    @Inject
    protected MetaModelChangedEventPublisher eventPublisher;

    @Inject
    protected NopMetaSearchProcessor searchService;

    static final String EVENT_ENTITY_TYPE = "NopMetaTable";

    private final MetaTableProfiler profiler = new MetaTableProfiler();
    private final MetaJoinExecutor joinExecutor = new MetaJoinExecutor();
    private final MetaAggregationExecutor aggregationExecutor = new MetaAggregationExecutor(joinExecutor);
    private final NopMetaTableQueryAction queryAction = new NopMetaTableQueryAction();

    private TableReferenceExecutor tableRefExecutor;

    public NopMetaTableBizModel() {
        setEntityName(NopMetaTable.class.getName());
    }

    @Override
    public NopMetaTable save(@Name("data") Map<String, Object> data, IServiceContext context) {
        String id = data == null ? null : NopMetadataHelper.stringOf(data, NopMetaTable.PROP_NAME_metaTableId);
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
        searchService.addToIndex("MetaTable", saved.getMetaTableId(), toSearchableDoc(saved));
        return saved;
    }

    @Override
    public boolean delete(@Name("id") String id, IServiceContext context) {
        NopMetaTable before = requireEntity(id, "delete", context);
        boolean deleted = super.delete(id, context);
        String beforeSnapshot = eventPublisher.buildSnapshot(before, EVENT_ENTITY_TYPE, id);
        eventPublisher.publishEventWithSnapshots(
                _NopMetadataCoreConstants.CHANGE_EVENT_TYPE_ENTITY_DELETED,
                EVENT_ENTITY_TYPE, id, before.getTableName(),
                MetaModelChangedEventPublisher.CHANGE_SOURCE_API,
                beforeSnapshot, null,
                MetaModelChangedEventPublisher.newTransactionId(), context);
        searchService.removeFromIndex("MetaTable", id);
        return deleted;
    }

    @BizMutation
    public ProfileResultDTO profileTable(@Name("metaTableId") String metaTableId,
                                          @Optional @Name("schemaPattern") String schemaPattern,
                                          @Optional @Name("columns") String columns,
                                          IServiceContext context) {
        NopMetaTable table = requireEntity(metaTableId, "profile", context);
        TableReference ref = queryAction.tableRefResolver().resolve(table,
                daoFor(NopMetaDataSource.class), daoFor(NopMetaEntity.class),
                daoFor(NopMetaEntityField.class), orm());
        String effectiveSchema = resolveDefaultSchema(schemaPattern, table);
        ProfilingSnapshot snapshot = ensureTableRefExecutor().execute(ref,
                (conn, metaData, productName) -> profiler.profile(conn, metaData, ref, effectiveSchema, columns, productName));
        NopMetaProfilingResult row = appendProfilingResult(null, metaTableId, snapshot);
        return NopMetaTableQueryAction.buildProfileResultDTO(row, snapshot);
    }

    @BizMutation
    public CreateSqlTableResultDTO createSqlTable(@Name("sql") String sql,
                                                   @Name("tableName") String tableName,
                                                   @Name("metaModuleId") String metaModuleId,
                                                   @Optional @Name("querySpace") String querySpace,
                                                   @Optional @Name("displayName") String displayName,
                                                   IServiceContext context) {
        List<SqlViewField> fields = queryAction.sqlFieldExtractor().extract(sql);
        if (querySpace != null && !querySpace.trim().isEmpty()) {
            fields = queryAction.ensureSqlFieldTypeInferrer(connectionService).inferTypes(
                    fields, sql, querySpace, daoFor(NopMetaDataSource.class));
        }
        IEntityDao<NopMetaModule> moduleDao = daoFor(NopMetaModule.class);
        NopMetaModule module = moduleDao.requireEntityById(metaModuleId);
        IEntityDao<NopMetaTable> tableDao = dao();
        // R4.2（plan-2026-08-05-1625-1 D5）：4 列 UK 含可空 META_SCHEMA 后 SQL 表（恒 null-schema）
        // 第二次创建不再被 DB 层 UK 拦截——补 find-or-fail 守卫保持 fail-fast 语义
        QueryBean dupQuery = new QueryBean();
        dupQuery.addFilter(FilterBeans.eq(NopMetaTable.PROP_NAME_metaModuleId, metaModuleId));
        dupQuery.addFilter(FilterBeans.eq(NopMetaTable.PROP_NAME_tableName, tableName));
        if (tableDao.findFirstByQuery(dupQuery) != null) {
            throw new NopMetadataException(NopMetadataErrors.ERR_SQL_VIEW_TABLE_EXISTS)
                    .param(NopMetadataArgs.ARG_META_MODULE_ID, metaModuleId)
                    .param(NopMetadataArgs.ARG_TABLE_NAME, tableName);
        }
        NopMetaTable table = tableDao.newEntity();
        table.setMetaModuleId(metaModuleId);
        table.setIsDelta((byte) 0);
        table.setTableName(tableName);
        table.setDisplayName(displayName != null ? displayName : tableName);
        table.setTableType(_NopMetadataCoreConstants.TABLE_TYPE_SQL);
        if (querySpace != null) table.setQuerySpace(querySpace);
        table.setSourceSql(sql);
        tableDao.saveEntity(table);
        eventPublisher.publishEvent(
                _NopMetadataCoreConstants.CHANGE_EVENT_TYPE_ENTITY_CREATED,
                EVENT_ENTITY_TYPE, table.getMetaTableId(), table.getTableName(),
                MetaModelChangedEventPublisher.CHANGE_SOURCE_UI,
                null, table, MetaModelChangedEventPublisher.newTransactionId(), context);
        CreateSqlTableResultDTO result = new CreateSqlTableResultDTO();
        result.setMetaTableId(table.getMetaTableId());
        result.setTableName(table.getTableName());
        result.setTableType(table.getTableType());
        result.setFields(toSqlViewFieldDTOs(fields));
        return result;
    }

    @BizQuery
    public PreviewSqlFieldsResultDTO previewSqlFields(@Name("sql") String sql, IServiceContext context) {
        List<SqlViewField> fields = queryAction.sqlFieldExtractor().extract(sql);
        PreviewSqlFieldsResultDTO result = new PreviewSqlFieldsResultDTO();
        result.setFields(toSqlViewFieldDTOs(fields));
        return result;
    }

    @BizQuery
    public ResolveTableFieldsResultDTO resolveTableFields(@Name("metaTableId") String metaTableId,
                                                           IServiceContext context) {
        NopMetaTable table = requireEntity(metaTableId, "query", context);
        IEntityDao<NopMetaEntityField> fieldDao = daoFor(NopMetaEntityField.class);
        List<ResolvedTableField> fields = queryAction.fieldResolver().resolve(table, fieldDao);
        if (_NopMetadataCoreConstants.TABLE_TYPE_SQL.equals(table.getTableType())
                && table.getQuerySpace() != null && !table.getQuerySpace().trim().isEmpty()) {
            fields = inferResolvedSqlFieldTypes(table, fields);
        }
        ResolveTableFieldsResultDTO result = new ResolveTableFieldsResultDTO();
        result.setTableType(table.getTableType());
        result.setFields(toResolvedTableFieldDTOs(fields));
        return result;
    }

    @BizQuery
    public QueryTableDataResultDTO queryTableData(@Name("metaTableId") String metaTableId,
                                                   @Optional @Name("filter") TreeBean filter,
                                                   @Optional @Name("limit") Long limit,
                                                   @Optional @Name("offset") Long offset,
                                                   @Optional @Name("selection") FieldSelectionBean selection,
                                                   IServiceContext context) {
        NopMetaTable table = requireEntity(metaTableId, "query", context);
        String tableType = table.getTableType();
        QueryTableDataResultDTO result = new QueryTableDataResultDTO();
        result.setTableType(tableType);
        limit = normalizeQueryLimit(limit);
        if (_NopMetadataCoreConstants.TABLE_TYPE_ENTITY.equals(tableType)) {
            result.setItems(queryAction.queryEntityData(table, filter, limit, offset, daoProvider(), orm()));
        } else if (_NopMetadataCoreConstants.TABLE_TYPE_EXTERNAL.equals(tableType)) {
            result.setItems(queryAction.queryExternalData(table, filter, limit, offset, connectionService, daoProvider(), orm()));
        } else if (_NopMetadataCoreConstants.TABLE_TYPE_SQL.equals(tableType)) {
            result.setItems(queryAction.querySqlData(table, filter, limit, offset, connectionService, daoProvider(), orm()));
        } else {
            throw new NopMetadataException(NopMetadataErrors.ERR_QUERY_UNSUPPORTED_TABLE_TYPE)
                    .param("metaTableId", metaTableId)
                    .param("tableType", String.valueOf(tableType));
        }
        return result;
    }

    @BizQuery
    public QueryJoinDataResultDTO queryJoinData(@Name("metaTableId") String metaTableId,
                                                  @Name("joinId") String joinId,
                                                  @Optional @Name("filter") TreeBean filter,
                                                  @Optional @Name("limit") Long limit,
                                                  @Optional @Name("offset") Long offset,
                                                  @Optional @Name("selection") FieldSelectionBean selection,
                                                  IServiceContext context) {
        NopMetaTable table = requireEntity(metaTableId, "query", context);
        Map<String, Object> raw = joinExecutor.executeJoin(table, joinId, filter, limit, offset, buildQueryContext());
        QueryJoinDataResultDTO result = new QueryJoinDataResultDTO();
        Object items = raw.get("items");
        if (items instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> itemsList = (List<Map<String, Object>>) items;
            result.setItems(itemsList);
        }
        return result;
    }

    @BizQuery
    public AggregationResultDTO queryAggregation(@Name("metaTableId") String metaTableId,
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
        NopMetaTable table = requireEntity(metaTableId, "query", context);
        Map<String, Object> raw = aggregationExecutor.executeAggregation(table, measures, dimensions, filter, joinId, limit, offset,
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

    private List<ResolvedTableField> inferResolvedSqlFieldTypes(NopMetaTable table,
                                                                 List<ResolvedTableField> resolvedFields) {
        List<SqlViewField> asViewFields = new ArrayList<>(resolvedFields.size());
        for (ResolvedTableField f : resolvedFields) {
            asViewFields.add(new SqlViewField(f.getName(), null, null));
        }
        List<SqlViewField> inferred = queryAction.ensureSqlFieldTypeInferrer(connectionService).inferTypes(
                asViewFields, table.getSourceSql(), table.getQuerySpace(), daoFor(NopMetaDataSource.class));
        List<ResolvedTableField> out = new ArrayList<>(resolvedFields.size());
        for (int i = 0; i < resolvedFields.size(); i++) {
            ResolvedTableField orig = resolvedFields.get(i);
            out.add(new ResolvedTableField(orig.getName(), orig.getSourceType(), inferred.get(i).getType()));
        }
        return out;
    }

    NopMetaProfilingResult appendProfilingResult(String profilingRuleId, String metaTableId,
                                                  ProfilingSnapshot snapshot) {
        IEntityDao<NopMetaProfilingResult> resultDao = daoFor(NopMetaProfilingResult.class);
        NopMetaProfilingResult row = resultDao.newEntity();
        if (profilingRuleId != null) row.setProfilingRuleId(profilingRuleId);
        row.setMetaTableId(metaTableId);
        row.setSnapshotTime(io.nop.api.core.time.CoreMetrics.currentTimestamp());
        row.setTableStats(JsonTool.stringify(snapshot.toTableStatsMap()));
        row.setColumnStats(JsonTool.stringify(snapshot.toColumnStatsList()));
        resultDao.saveEntity(row);
        return row;
    }

    private static List<SqlViewFieldDTO> toSqlViewFieldDTOs(List<SqlViewField> fields) {
        List<SqlViewFieldDTO> list = new ArrayList<>(fields.size());
        for (SqlViewField f : fields) {
            list.add(new SqlViewFieldDTO(f.getName(), f.getAlias(), f.getType()));
        }
        return list;
    }

    private static List<ResolvedTableFieldDTO> toResolvedTableFieldDTOs(List<ResolvedTableField> fields) {
        List<ResolvedTableFieldDTO> list = new ArrayList<>(fields.size());
        for (ResolvedTableField f : fields) {
            list.add(new ResolvedTableFieldDTO(f.getName(), f.getSourceType(), f.getDataType()));
        }
        return list;
    }

    private static String resolveDefaultSchema(String schemaPattern, NopMetaTable table) {
        if (schemaPattern != null && !schemaPattern.trim().isEmpty()) {
            return schemaPattern;
        }
        return table.getMetaSchema();
    }

    private TableReferenceExecutor ensureTableRefExecutor() {
        if (tableRefExecutor == null) {
            tableRefExecutor = new TableReferenceExecutor(connectionService, orm());
        }
        return tableRefExecutor;
    }

    private SearchableDoc toSearchableDoc(NopMetaTable entity) {
        return NopMetadataHelper.toSearchableDoc(entity);
    }

    /**
     * 归一化 queryTableData 的 limit（MA7.4-03）：缺省给默认值，超上限封顶，
     * 防止省略 limit 的大表查询把全表拉入内存序列化。
     */
    private Long normalizeQueryLimit(Long limit) {
        if (limit == null || limit <= 0) {
            return (long) DEFAULT_QUERY_LIMIT;
        }
        long max = configuredMaxQueryLimit > 0 ? configuredMaxQueryLimit : DEFAULT_MAX_QUERY_LIMIT;
        return Math.min(limit, max);
    }
}
