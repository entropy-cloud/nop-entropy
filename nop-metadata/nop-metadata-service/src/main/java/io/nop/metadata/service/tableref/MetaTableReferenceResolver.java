package io.nop.metadata.service.tableref;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.jdbc.txn.IJdbcTransaction;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.service.datasource.MetaDataSourceResolver;
import io.nop.metadata.service.field.MetaTableFieldResolver;
import io.nop.metadata.service.field.ResolvedTableField;

import java.util.ArrayList;
import java.util.List;

/**
 * 共享 table-reference 解析器（架构基线 §4.4 D3）：输入 {@link NopMetaTable} → 按 {@code tableType} 分派解析为
 * {@link TableReference}（external/entity/sql 三态），供 Catalog/Quality/Profiling 三大执行器统一消费。
 *
 * <p>三态解析（D1/D2/D3）：
 * <ul>
 *   <li><b>external</b>：经 {@link MetaDataSourceResolver} 解析 querySpace→{@link NopMetaDataSource}；
 *       物理表名取 {@code NopMetaTable.tableName}。</li>
 *   <li><b>entity</b>（D1）：解析 baseEntityId→{@link NopMetaEntity}，校验实体已注册（{@code IOrmTemplate.isValidEntityName}）
 *       + tableName 非空，取平台 querySpace（{@code entity.querySpace}，null 回退默认）。**不经 NopMetaDataSource**，
 *       数据在平台库（平台 Connection 经 {@link IJdbcTransaction} 取）。</li>
 *   <li><b>sql</b>（D2）：经 {@link MetaDataSourceResolver} 解析 querySpace→数据源；sourceSql 非空校验；
 *       字段集合由 {@link MetaTableFieldResolver} AST 解析（DatabaseMetaData.getColumns 对子查询不适用）。</li>
 * </ul>
 *
 * <p>失败路径显式化（不静默返回 null、不静默空集，对齐 Minimum Rules #24）：
 * <ul>
 *   <li>tableType 不在 entity/external/sql → {@link #ERR_REF_UNKNOWN_TABLE_TYPE}</li>
 *   <li>entity 表 baseEntityId 为 null → {@link #ERR_REF_ENTITY_BASE_NULL}</li>
 *   <li>entity 实体记录不存在 → {@link #ERR_REF_ENTITY_NOT_FOUND}</li>
 *   <li>entity 实体未注册于运行时 IOrmSessionFactory → {@link #ERR_REF_ENTITY_NOT_REGISTERED}</li>
 *   <li>entity.tableName 为空 → {@link #ERR_REF_ENTITY_TABLE_NAME_EMPTY}</li>
 *   <li>sql 表 sourceSql 为空 → {@link #ERR_REF_SQL_SOURCE_EMPTY}</li>
 *   <li>external/sql querySpace 无数据源/DISABLED → 由 {@link MetaDataSourceResolver} 抛 inline ErrorCode</li>
 * </ul>
 *
 * <p>无状态（依赖的 {@link MetaDataSourceResolver} / {@link MetaTableFieldResolver} 亦无状态），
 * 可在多 BizModel 间共享实例。DAO 与 {@link IOrmTemplate} 由调用方在调用时获取传入。
 */
public class MetaTableReferenceResolver {

    static final ErrorCode ERR_REF_UNKNOWN_TABLE_TYPE =
            ErrorCode.define("metadata.tableref-unknown-table-type",
                    "Unknown tableType for table-reference resolution: {metaTableId} tableType={tableType}",
                    "metaTableId", "tableType");
    static final ErrorCode ERR_REF_ENTITY_BASE_NULL =
            ErrorCode.define("metadata.tableref-entity-base-null",
                    "Cannot resolve entity table: baseEntityId is null (dangling reference): {metaTableId}",
                    "metaTableId");
    static final ErrorCode ERR_REF_ENTITY_NOT_FOUND =
            ErrorCode.define("metadata.tableref-entity-not-found",
                    "Cannot resolve entity table: NopMetaEntity not found for baseEntityId: "
                            + "{metaTableId} baseEntityId={baseEntityId}",
                    "metaTableId", "baseEntityId");
    static final ErrorCode ERR_REF_ENTITY_NOT_REGISTERED =
            ErrorCode.define("metadata.tableref-entity-not-registered",
                    "Entity is not registered in runtime IOrmSessionFactory (would be silent empty set): "
                            + "{metaTableId} entityName={entityName}",
                    "metaTableId", "entityName");
    static final ErrorCode ERR_REF_ENTITY_TABLE_NAME_EMPTY =
            ErrorCode.define("metadata.tableref-entity-table-name-empty",
                    "Cannot resolve entity table: NopMetaEntity.tableName is empty: "
                            + "{metaTableId} entityName={entityName}",
                    "metaTableId", "entityName");
    static final ErrorCode ERR_REF_SQL_SOURCE_EMPTY =
            ErrorCode.define("metadata.tableref-sql-source-empty",
                    "Cannot resolve sql table: sourceSql is empty: {metaTableId}", "metaTableId");

    private final MetaDataSourceResolver dataSourceResolver;
    private final MetaTableFieldResolver fieldResolver;

    public MetaTableReferenceResolver(MetaDataSourceResolver dataSourceResolver,
                                       MetaTableFieldResolver fieldResolver) {
        this.dataSourceResolver = dataSourceResolver;
        this.fieldResolver = fieldResolver;
    }

    public MetaTableReferenceResolver() {
        this(new MetaDataSourceResolver(), new MetaTableFieldResolver());
    }

    /**
     * 解析给定逻辑表的执行引用。
     *
     * @param table     目标逻辑表（非 null）
     * @param dsDao     数据源 DAO（external/sql 分派使用）
     * @param entityDao 实体 DAO（entity 分派使用）
     * @param fieldDao  实体字段 DAO（sql 分派 AST 字段解析使用）
     * @param orm       平台 ORM 模板（entity 分派的 isValidEntityName 校验使用）
     * @return 三态 TableReference 之一（永不 null；不可解析时由本方法显式抛出）
     * @throws NopException 解析失败（tableType 未知 / baseEntityId null / 实体未注册 / tableName 空 / sourceSql 空 /
     *                      querySpace 无数据源 / DISABLED）
     */
    public TableReference resolve(NopMetaTable table,
                                   IEntityDao<NopMetaDataSource> dsDao,
                                   IEntityDao<NopMetaEntity> entityDao,
                                   IEntityDao<io.nop.metadata.dao.entity.NopMetaEntityField> fieldDao,
                                   IOrmTemplate orm) {
        if (table == null) {
            throw new NopException(ERR_REF_UNKNOWN_TABLE_TYPE);
        }
        String tableType = table.getTableType();
        if (_NopMetadataCoreConstants.TABLE_TYPE_EXTERNAL.equals(tableType)) {
            return resolveExternal(table, dsDao);
        }
        if (_NopMetadataCoreConstants.TABLE_TYPE_ENTITY.equals(tableType)) {
            return resolveEntity(table, entityDao, orm);
        }
        if (_NopMetadataCoreConstants.TABLE_TYPE_SQL.equals(tableType)) {
            return resolveSql(table, dsDao, fieldDao);
        }
        throw new NopException(ERR_REF_UNKNOWN_TABLE_TYPE)
                .param("metaTableId", table.getMetaTableId())
                .param("tableType", String.valueOf(tableType));
    }

    // ============================================================
    // tableType 分派实现
    // ============================================================

    private TableReference resolveExternal(NopMetaTable table, IEntityDao<NopMetaDataSource> dsDao) {
        NopMetaDataSource ds = resolveDataSourceOrThrow(dsDao, table.getQuerySpace(), table.getMetaTableId());
        return new TableReference(TableReference.Kind.EXTERNAL, table.getMetaTableId(),
                table.getTableName(), null, ds, null, null, null);
    }

    private TableReference resolveEntity(NopMetaTable table, IEntityDao<NopMetaEntity> entityDao, IOrmTemplate orm) {
        String baseEntityId = table.getBaseEntityId();
        if (baseEntityId == null || baseEntityId.isEmpty()) {
            throw new NopException(ERR_REF_ENTITY_BASE_NULL)
                    .param("metaTableId", table.getMetaTableId());
        }
        NopMetaEntity entity = entityDao.getEntityById(baseEntityId);
        if (entity == null) {
            throw new NopException(ERR_REF_ENTITY_NOT_FOUND)
                    .param("metaTableId", table.getMetaTableId())
                    .param("baseEntityId", baseEntityId);
        }
        String entityName = entity.getEntityName();
        if (entityName == null || entityName.isEmpty() || !orm.isValidEntityName(entityName)) {
            throw new NopException(ERR_REF_ENTITY_NOT_REGISTERED)
                    .param("metaTableId", table.getMetaTableId())
                    .param("entityName", String.valueOf(entityName));
        }
        String physicalTable = entity.getTableName();
        if (physicalTable == null || physicalTable.isEmpty()) {
            throw new NopException(ERR_REF_ENTITY_TABLE_NAME_EMPTY)
                    .param("metaTableId", table.getMetaTableId())
                    .param("entityName", entityName);
        }
        String platformQuerySpace = entity.getQuerySpace();
        if (platformQuerySpace == null || platformQuerySpace.trim().isEmpty()) {
            platformQuerySpace = io.nop.dao.DaoConstants.DEFAULT_QUERY_SPACE;
        }
        // entity 表的物理列从 ORM 实体模型取（code=物理列名，stdSqlType=类型），避免 DatabaseMetaData 大小写不匹配
        List<ResolvedTableField> entityFields = resolveEntityColumnsFromOrmModel(entityName, orm);
        return new TableReference(TableReference.Kind.ENTITY, table.getMetaTableId(),
                physicalTable, null, null, entity, platformQuerySpace, entityFields);
    }

    /** 从运行时 ORM 实体模型取物理列名（code）+ 类型（stdSqlType.name），供 profiler/catalog 直接使用。 */
    private List<ResolvedTableField> resolveEntityColumnsFromOrmModel(String entityName, IOrmTemplate orm) {
        IEntityModel entityModel = orm.getOrmModel().getEntityModel(entityName);
        if (entityModel == null) {
            return null;
        }
        List<? extends IColumnModel> columns = entityModel.getColumns();
        List<ResolvedTableField> fields = new ArrayList<>(columns.size());
        for (IColumnModel col : columns) {
            String colName = col.getCode();
            if (colName == null || colName.isEmpty()) {
                continue;
            }
            String typeName = col.getStdSqlType() != null ? col.getStdSqlType().name() : null;
            fields.add(new ResolvedTableField(colName, ResolvedTableField.SOURCE_ENTITY, typeName));
        }
        return fields.isEmpty() ? null : fields;
    }

    private TableReference resolveSql(NopMetaTable table, IEntityDao<NopMetaDataSource> dsDao,
                                       IEntityDao<io.nop.metadata.dao.entity.NopMetaEntityField> fieldDao) {
        String sourceSql = table.getSourceSql();
        if (sourceSql == null || sourceSql.trim().isEmpty()) {
            throw new NopException(ERR_REF_SQL_SOURCE_EMPTY)
                    .param("metaTableId", table.getMetaTableId());
        }
        NopMetaDataSource ds = resolveDataSourceOrThrow(dsDao, table.getQuerySpace(), table.getMetaTableId());
        List<ResolvedTableField> fields = fieldResolver.resolve(table, fieldDao);
        return new TableReference(TableReference.Kind.SQL, table.getMetaTableId(),
                null, sourceSql, ds, null, null, fields);
    }

    /** querySpace→数据源解析，失败时附加 metaTableId 上下文。 */
    private NopMetaDataSource resolveDataSourceOrThrow(IEntityDao<NopMetaDataSource> dsDao,
                                                        String querySpace, String metaTableId) {
        try {
            return dataSourceResolver.resolveActiveOrThrow(dsDao, querySpace);
        } catch (NopException e) {
            if (e.getParam("metaTableId") == null) {
                e.param("metaTableId", metaTableId);
            }
            throw e;
        }
    }
}
