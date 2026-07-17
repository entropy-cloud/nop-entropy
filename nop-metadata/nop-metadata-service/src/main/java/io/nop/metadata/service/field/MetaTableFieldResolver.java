package io.nop.metadata.service.field;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTableJoin;
import io.nop.metadata.service.sqlview.SqlSelectFieldExtractor;
import io.nop.metadata.service.sqlview.SqlViewField;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 跨表类型字段解析器（架构基线 §2.5.2 D2）：输入 {@link NopMetaTable} → 按 {@code tableType} 分派解析可用字段
 * 集合 → 返回统一 {@link ResolvedTableField} 列表。
 *
 * <p>分派规则（D2 可用字段集合范围）：
 * <ul>
 *   <li><b>entity</b>：手动 query——{@code NopMetaTable.baseEntityId} 为 plain string 列、无 ORM relation；
 *       {@code NopMetaEntity} 亦无 fields to-many。按 {@code baseEntityId} 作 {@code metaEntityId} 查
 *       {@link NopMetaEntityField} 集合。{@code baseEntityId} 为 null（ORM nullable）时显式失败抛 inline ErrorCode
 *       （不静默空集、不静默存入悬空引用）。</li>
 *   <li><b>external</b>：解析 {@code buildSql} JSON（结构：JSON 数组，元素 key 含 {@code columnName}/
 *       {@code dataType} 等，见 {@code NopMetaDataSourceBizModel.serializeColumns}）取 {@code columnName} 集合。
 *       JSON 损坏/非数组/为空 → 显式失败。</li>
 *   <li><b>sql</b>：调 P3-1 SELECT 字段解析器 {@link SqlSelectFieldExtractor} 解析 {@code sourceSql} 得字段名集合。
 *       解析失败路径（非 SELECT/多语句/通配符/空）显式失败（见架构基线 §4.2.1）。</li>
 * </ul>
 *
 * <p>降级铁律（不静默通过、不吞异常、不伪造）：字段集合解析失败一律抛 inline {@link ErrorCode}，
 * 不静默返回空集合、不静默跳过校验、不静默存入悬空引用。
 *
 * <p>跨表校验（架构基线 §2.5.2 D3/D4）：
 * <ul>
 *   <li><b>entity 表 Measure/Dimension</b>（plan 0228-3）：{@code entityFieldId} 引用可校验**通过 NopMetaTableJoin
 *       直连可达的 rightEntityId 实体字段**（跨表指标），可达 entityId 集合 = {@code {baseEntityId ∪ join.rightEntityId}}，
 *       见 {@link #resolveAllowedEntityIds}（PK 归属语义，Approach A）。</li>
 *   <li><b>sql/external 表 Measure/Dimension</b>（plan 0700-1 D4）：{@code entityFieldId} 为字段名字符串，其跨表校验采用
 *       **name-based 可达列名集合** = {@code 该表自身列名 ∪ 其 NopMetaTableJoin 各端点解析出的列名/字段名}，
 *       见 {@link #resolveAllowedFieldNames}（区别于 entity 的 PK 归属语义——sql/external 字段引用是 name-based）。
 *       端点经 plan 0700-1 新增的 {@code leftTableId}/{@code rightTableId}（→NopMetaTable，external/sql）或
 *       {@code leftEntityId}/{@code rightEntityId}（→NopMetaEntity）解析。</li>
 * </ul>
 *
 * <p>本解析器无状态（{@link SqlSelectFieldExtractor} 亦无状态），可在多 BizModel 间共享实例。
 */
public class MetaTableFieldResolver {

    static final ErrorCode ERR_FIELD_RESOLVE_TABLE_NOT_FOUND =
            ErrorCode.define("metadata.field-resolve-table-not-found",
                    "MetaTable not found for field resolution: {metaTableId}", "metaTableId");
    static final ErrorCode ERR_FIELD_RESOLVE_BASE_ENTITY_NULL =
            ErrorCode.define("metadata.field-resolve-base-entity-null",
                    "Cannot resolve fields: entity table has null baseEntityId (dangling reference not allowed): "
                            + "{metaTableId}", "metaTableId");
    static final ErrorCode ERR_FIELD_RESOLVE_NO_FIELDS =
            ErrorCode.define("metadata.field-resolve-no-fields",
                    "Resolved field set is empty for table: {metaTableId} tableType={tableType}",
                    "metaTableId", "tableType");
    static final ErrorCode ERR_FIELD_RESOLVE_EXTERNAL_BUILD_SQL_INVALID =
            ErrorCode.define("metadata.field-resolve-external-build-sql-invalid",
                    "Failed to parse external table buildSql JSON (expecting JSON array of column descriptors): "
                            + "{metaTableId}", "metaTableId");
    static final ErrorCode ERR_FIELD_RESOLVE_UNKNOWN_TABLE_TYPE =
            ErrorCode.define("metadata.field-resolve-unknown-table-type",
                    "Unknown tableType for field resolution: {metaTableId} tableType={tableType}",
                    "metaTableId", "tableType");

    private final SqlSelectFieldExtractor sqlSelectFieldExtractor;

    public MetaTableFieldResolver(SqlSelectFieldExtractor sqlSelectFieldExtractor) {
        this.sqlSelectFieldExtractor = sqlSelectFieldExtractor;
    }

    public MetaTableFieldResolver() {
        this(new SqlSelectFieldExtractor());
    }

    /**
     * 解析给定逻辑表的可用字段集合。
     *
     * <p>本重载用于 save 校验等已有 {@link NopMetaTable} 实体的场景（避免重复加载）。
     *
     * @param table    目标逻辑表（非 null）
     * @param fieldDao entity 表字段 DAO（仅 entity 分派使用；external/sql 分派不使用，可传 null）
     * @return 字段列表（永不 null；无字段时由分派逻辑显式失败，不静默返回空）
     * @throws NopException 解析失败（baseEntityId null / buildSql JSON 损坏 / sourceSql 不可解析 / 无字段）
     */
    public List<ResolvedTableField> resolve(NopMetaTable table, IEntityDao<NopMetaEntityField> fieldDao) {
        if (table == null) {
            throw new NopException(ERR_FIELD_RESOLVE_TABLE_NOT_FOUND);
        }
        String tableType = table.getTableType();
        if (_NopMetadataCoreConstants.TABLE_TYPE_ENTITY.equals(tableType)) {
            return resolveEntityFields(table, fieldDao);
        }
        if (_NopMetadataCoreConstants.TABLE_TYPE_EXTERNAL.equals(tableType)) {
            return resolveExternalFields(table);
        }
        if (_NopMetadataCoreConstants.TABLE_TYPE_SQL.equals(tableType)) {
            return resolveSqlFields(table);
        }
        // 未知 tableType——显式失败而非静默跳过（No Silent No-Op Rule）
        throw new NopException(ERR_FIELD_RESOLVE_UNKNOWN_TABLE_TYPE)
                .param("metaTableId", table.getMetaTableId())
                .param("tableType", String.valueOf(tableType));
    }

    /** 解析某实体（metaEntityId）的可用字段集合（供 Join 校验 leftEntity/rightEntity 字段引用）。 */
    public List<ResolvedTableField> resolveEntityFieldsByEntityId(String metaEntityId,
                                                                   IEntityDao<NopMetaEntityField> fieldDao) {
        if (metaEntityId == null || metaEntityId.isEmpty()) {
            throw new NopException(ERR_FIELD_RESOLVE_BASE_ENTITY_NULL);
        }
        List<NopMetaEntityField> entityFields = findEntityFields(metaEntityId, fieldDao);
        if (entityFields.isEmpty()) {
            // 实体存在但无字段——显式失败（不静默空集）
            throw new NopException(ERR_FIELD_RESOLVE_NO_FIELDS)
                    .param("metaTableId", metaEntityId)
                    .param("tableType", _NopMetadataCoreConstants.TABLE_TYPE_ENTITY);
        }
        List<ResolvedTableField> fields = new ArrayList<>(entityFields.size());
        for (NopMetaEntityField ef : entityFields) {
            fields.add(new ResolvedTableField(ef.getFieldName(),
                    ResolvedTableField.SOURCE_ENTITY, ef.getStdSqlType()));
        }
        return fields;
    }

    /** 仅返回字段名集合（save 校验主路径，避免每次构建 ResolvedTableField 列表）。 */
    public Set<String> resolveFieldNames(NopMetaTable table, IEntityDao<NopMetaEntityField> fieldDao) {
        List<ResolvedTableField> fields = resolve(table, fieldDao);
        Set<String> names = new LinkedHashSet<>(fields.size());
        for (ResolvedTableField f : fields) {
            names.add(f.getName());
        }
        return names;
    }

    /**
     * 解析 entity 类型表的可达 entityId 集合（架构基线 §2.5.2 D3 跨表 Measure/Dimension 校验范围）。
     *
     * <p>集合 = {@code {baseEntityId ∪ 该表所有 NopMetaTableJoin（按 metaTableId 加载）的 rightEntityId}}。
     * 仅直连 Join 可达，不递归 join 图（A→B→C 间接可达为 follow-up）。宽松语义：不要求 Join 的
     * {@code leftEntityId == baseEntityId}，任意该表 Join 的 rightEntityId 均视为可达。
     *
     * @param table    entity 类型目标逻辑表（非 null）；调用方契约：仅对 entity 类型表调用本方法
     *                （由 {@link #validateFieldReference} 的 entity 分支保证）
     * @param joinDao  表关联 DAO（用于按 metaTableId 加载 Join 列表）；为 null 时退化为 entity-only（仅 baseEntityId）
     * @return 可达 entityId 集合（至少含 baseEntityId，永不 null/空）
     * @throws NopException baseEntityId 为 null（entity 表必须有主实体，对齐 §2.5.2 降级铁律不静默空集）
     */
    public Set<String> resolveAllowedEntityIds(NopMetaTable table, IEntityDao<NopMetaTableJoin> joinDao) {
        String baseEntityId = table.getBaseEntityId();
        if (baseEntityId == null || baseEntityId.isEmpty()) {
            // entity 表 baseEntityId 为 null 显式失败（不静默空集、不静默存入悬空引用）
            throw new NopException(ERR_FIELD_RESOLVE_BASE_ENTITY_NULL)
                    .param("metaTableId", table.getMetaTableId());
        }
        Set<String> allowed = new LinkedHashSet<>();
        allowed.add(baseEntityId);
        if (joinDao == null) {
            // 调用方未提供 join DAO——退化为 entity-only（仅 baseEntityId），用于不需要跨表校验的场景
            return allowed;
        }
        // 加载该表直连 Join，收集 rightEntityId（宽松语义：不要求 leftEntityId == baseEntityId）
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTableJoin.PROP_NAME_metaTableId, table.getMetaTableId()));
        List<NopMetaTableJoin> joins = joinDao.findAllByQuery(q);
        for (NopMetaTableJoin join : joins) {
            String rightEntityId = join.getRightEntityId();
            if (rightEntityId != null && !rightEntityId.isEmpty()) {
                allowed.add(rightEntityId);
            }
        }
        return allowed;
    }

    /**
     * 校验字段引用合法性（架构基线 §2.5.2 D2 字段引用存储裁定 + D3 entity 跨表 PK 归属校验范围
     * + D4 sql/external name-based 可达集校验）。
     *
     * <p>语义按 tableType 重载：
     * <ul>
     *   <li><b>entity</b> 表：{@code entityFieldId} 为 {@code NopMetaEntityField.entityFieldId} 主键——
     *       按 PK 加载字段实体，校验其 {@code metaEntityId} ∈ 可达 entityId 集合 {@code allowedEntityIds}
     *       （= {@code baseEntityId ∪ join 直连可达 rightEntityId}，见 {@link #resolveAllowedEntityIds}）。
     *       跨表指标（引用 join 右实体字段）合法通过；悬空跨表引用（metaEntityId 不在集合）显式失败。</li>
     *   <li><b>external / sql</b> 表（plan 0700-1 D4 扩展）：{@code entityFieldId} 为字段名字符串——校验该名属于
     *       该表 name-based 可达列名集合 {@code reachableFieldNames}（= 该表自身列名 ∪ 其 NopMetaTableJoin
     *       各端点解析出的列名/字段名，见 {@link #resolveAllowedFieldNames}）。tableDao 用于解析 table 端点
     *       （external/sql）的列结构；joinDao 为 null 时退化为仅自身列名（无跨表可达）。</li>
     * </ul>
     *
     * @param table          目标逻辑表
     * @param entityFieldId  字段引用（entity 表为主键，external/sql 表为字段名）；为 null/空时返回 true（跳过校验，
     *                       用于 expression 型 Measure）
     * @param fieldDao       entity 字段 DAO
     * @param joinDao        表关联 DAO（entity 分支用于跨表可达 rightEntityId 解析；external/sql 分支用于
     *                       name-based 可达列名集合并集；为 null 时两分支退化为非跨表校验）
     * @param tableDao       逻辑表 DAO（仅 external/sql 分支使用，用于解析 table 端点 NopMetaTable 列结构；
     *                       entity 分支不使用，可传 null）
     * @param errOnInvalid   引用不合法时抛出的 ErrorCode（调用方按语义提供，如 measure-field-not-found）
     * @param refKind        引用类型描述（如 "measure"/"dimension"），用于错误消息
     * @return true 如果引用合法或为空（跳过）；false 由调用方决定是否忽略
     * @throws NopException 引用不合法（{@code errOnInvalid}）、或字段集合解析失败（baseEntityId null/buildSql 损坏等）
     */
    public boolean validateFieldReference(NopMetaTable table, String entityFieldId,
                                          IEntityDao<NopMetaEntityField> fieldDao,
                                          IEntityDao<NopMetaTableJoin> joinDao,
                                          IEntityDao<NopMetaTable> tableDao,
                                          ErrorCode errOnInvalid, String refKind) {
        if (entityFieldId == null || entityFieldId.isEmpty()) {
            // expression 型 Measure / 无字段引用——跳过校验（Non-Goal: expression 内容首版不校验）
            return true;
        }
        if (_NopMetadataCoreConstants.TABLE_TYPE_ENTITY.equals(table.getTableType())) {
            // entity 表：entityFieldId 为 NopMetaEntityField 主键，按 PK 加载并校验归属（跨表可达集合）
            NopMetaEntityField field = fieldDao.getEntityById(entityFieldId);
            if (field == null) {
                throw new NopException(errOnInvalid)
                        .param("metaTableId", table.getMetaTableId())
                        .param("entityFieldId", entityFieldId)
                        .param("refKind", refKind);
            }
            // 跨表校验（§2.5.2 D3）：field.metaEntityId 须 ∈ {baseEntity ∪ join 直连可达 rightEntity}
            Set<String> allowedEntityIds = resolveAllowedEntityIds(table, joinDao);
            if (!allowedEntityIds.contains(field.getMetaEntityId())) {
                // 悬空跨表引用（metaEntityId 不在可达集合）——显式失败（不静默存入悬空引用）
                throw new NopException(errOnInvalid)
                        .param("metaTableId", table.getMetaTableId())
                        .param("entityFieldId", entityFieldId)
                        .param("refKind", refKind)
                        .param("allowedEntityIds", allowedEntityIds);
            }
            return true;
        }
        // external / sql 表（§2.5.2 D4）：entityFieldId 为字段名，校验属于 name-based 可达列名集合
        Set<String> names = resolveAllowedFieldNames(table, fieldDao, joinDao, tableDao);
        if (!names.contains(entityFieldId)) {
            throw new NopException(errOnInvalid)
                    .param("metaTableId", table.getMetaTableId())
                    .param("entityFieldId", entityFieldId)
                    .param("refKind", refKind)
                    .param("availableFields", names);
        }
        return true;
    }

    /**
     * 解析 sql/external 类型表的 name-based 可达列名集合（架构基线 §2.5.2 D4，plan 0700-1 D2 落地）。
     *
     * <p>集合 = {@code 该表自身可解析列名 ∪ 该表所有 NopMetaTableJoin 各端点（left/right，entity/table）
     * 解析出的列名/字段名}。entity 端点贡献其 {@code NopMetaEntityField.fieldName} 集合；table 端点（external/sql）
     * 贡献其列名集合。**不沿用** entity 表的 {@code allowedEntityIds} PK 归属路径——sql/external 字段引用是 name-based。
     *
     * <p>宽松语义（对齐 D3 entity 直连语义）：不要求 join 的某端点 == 该表自身，任意该表 join 的两端点列名均并入可达集。
     * 仅直连 join，不递归 join 图（A→B→C 间接可达为 follow-up）。
     *
     * @param table    sql/external 类型目标逻辑表（非 null）
     * @param fieldDao entity 字段 DAO（解析 entity 端点字段名 + entity 表列名）
     * @param joinDao  表关联 DAO；为 null 时退化为仅该表自身列名（无跨表可达，用于不要求跨表校验的场景）
     * @param tableDao 逻辑表 DAO（解析 table 端点 NopMetaTable 列结构）；joinDao 非 null 时须非 null
     * @return 可达列名集合（至少含该表自身列名，永不 null/空——自身列名空由 {@link #resolveFieldNames} 显式失败）
     * @throws NopException 该表自身或某端点列集合解析失败（buildSql 损坏 / sourceSql 不可解析 /
     *                       entity baseEntityId null / table 端点表不存在）——显式失败，不静默跳过端点
     */
    public Set<String> resolveAllowedFieldNames(NopMetaTable table,
                                                IEntityDao<NopMetaEntityField> fieldDao,
                                                IEntityDao<NopMetaTableJoin> joinDao,
                                                IEntityDao<NopMetaTable> tableDao) {
        // name-based 可达列名集合（D2）：自身列名 ∪ 各 join 端点解析出的列名/字段名
        Set<String> allowed = new LinkedHashSet<>(resolveFieldNames(table, fieldDao));
        if (joinDao == null) {
            // 调用方未提供 join DAO——退化为仅自身列名（无跨表可达）
            return allowed;
        }
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTableJoin.PROP_NAME_metaTableId, table.getMetaTableId()));
        List<NopMetaTableJoin> joins = joinDao.findAllByQuery(q);
        for (NopMetaTableJoin join : joins) {
            addEndpointFieldNames(allowed, join.getLeftEntityId(), join.getLeftTableId(), fieldDao, tableDao);
            addEndpointFieldNames(allowed, join.getRightEntityId(), join.getRightTableId(), fieldDao, tableDao);
        }
        return allowed;
    }

    /**
     * 将单个 join 端点（entity 或 table）解析出的列名/字段名并入可达集。
     *
     * <p>端点解析失败（entity 无字段 / table 端点表不存在 / 列集合损坏）显式抛 ErrorCode——按降级铁律不静默跳过
     * 该端点（静默跳过会导致可达集缩小，误判合法字段为悬空）。
     */
    private void addEndpointFieldNames(Set<String> sink, String entityId, String tableId,
                                       IEntityDao<NopMetaEntityField> fieldDao,
                                       IEntityDao<NopMetaTable> tableDao) {
        boolean hasEntity = entityId != null && !entityId.isEmpty();
        boolean hasTable = tableId != null && !tableId.isEmpty();
        if (!hasEntity && !hasTable) {
            // 端点未引用任何对象——跳过（Join 自身的 mandatory/互斥校验在 NopMetaTableJoinBizModel 负责）
            return;
        }
        if (hasEntity) {
            // entity 端点贡献其字段名集合（name-based，非 PK 路径）
            for (ResolvedTableField f : resolveEntityFieldsByEntityId(entityId, fieldDao)) {
                sink.add(f.getName());
            }
            return;
        }
        // table 端点（external/sql/entity 任一 tableType 均按其可解析列名解析）
        NopMetaTable endpointTable = tableDao.getEntityById(tableId);
        if (endpointTable == null) {
            // 端点表不存在——显式失败（不静默跳过该端点、不静默缩小可达集）
            throw new NopException(ERR_FIELD_RESOLVE_TABLE_NOT_FOUND).param("metaTableId", tableId);
        }
        sink.addAll(resolveFieldNames(endpointTable, fieldDao));
    }

    // ============================================================
    // tableType 分派实现
    // ============================================================

    private List<ResolvedTableField> resolveEntityFields(NopMetaTable table,
                                                          IEntityDao<NopMetaEntityField> fieldDao) {
        String baseEntityId = table.getBaseEntityId();
        if (baseEntityId == null || baseEntityId.isEmpty()) {
            // entity 表 baseEntityId 为 null 显式失败（不静默空集、不静默存入悬空引用）
            throw new NopException(ERR_FIELD_RESOLVE_BASE_ENTITY_NULL)
                    .param("metaTableId", table.getMetaTableId());
        }
        List<NopMetaEntityField> entityFields = findEntityFields(baseEntityId, fieldDao);
        if (entityFields.isEmpty()) {
            // baseEntityId 指向的实体无字段——显式失败（不静默空集）
            throw new NopException(ERR_FIELD_RESOLVE_NO_FIELDS)
                    .param("metaTableId", table.getMetaTableId())
                    .param("tableType", _NopMetadataCoreConstants.TABLE_TYPE_ENTITY);
        }
        List<ResolvedTableField> fields = new ArrayList<>(entityFields.size());
        for (NopMetaEntityField ef : entityFields) {
            fields.add(new ResolvedTableField(ef.getFieldName(),
                    ResolvedTableField.SOURCE_ENTITY, ef.getStdSqlType()));
        }
        return fields;
    }

    @SuppressWarnings("unchecked")
    private List<ResolvedTableField> resolveExternalFields(NopMetaTable table) {
        String buildSql = table.getBuildSql();
        if (buildSql == null || buildSql.trim().isEmpty()) {
            throw new NopException(ERR_FIELD_RESOLVE_EXTERNAL_BUILD_SQL_INVALID)
                    .param("metaTableId", table.getMetaTableId());
        }
        List<Map<String, Object>> columnList;
        try {
            Object parsed = JsonTool.parse(buildSql);
            if (!(parsed instanceof List)) {
                throw new NopException(ERR_FIELD_RESOLVE_EXTERNAL_BUILD_SQL_INVALID)
                        .param("metaTableId", table.getMetaTableId());
            }
            columnList = (List<Map<String, Object>>) parsed;
        } catch (NopException e) {
            throw e;
        } catch (Exception e) {
            throw new NopException(ERR_FIELD_RESOLVE_EXTERNAL_BUILD_SQL_INVALID)
                    .param("metaTableId", table.getMetaTableId()).cause(e);
        }
        if (columnList.isEmpty()) {
            throw new NopException(ERR_FIELD_RESOLVE_NO_FIELDS)
                    .param("metaTableId", table.getMetaTableId())
                    .param("tableType", _NopMetadataCoreConstants.TABLE_TYPE_EXTERNAL);
        }
        List<ResolvedTableField> fields = new ArrayList<>(columnList.size());
        for (Map<String, Object> col : columnList) {
            Object nameObj = col.get("columnName");
            if (nameObj == null || nameObj.toString().isEmpty()) {
                // 列描述缺 columnName——显式失败（不静默跳过）
                throw new NopException(ERR_FIELD_RESOLVE_EXTERNAL_BUILD_SQL_INVALID)
                        .param("metaTableId", table.getMetaTableId());
            }
            Object typeObj = col.get("dataType");
            fields.add(new ResolvedTableField(nameObj.toString(),
                    ResolvedTableField.SOURCE_EXTERNAL,
                    typeObj == null ? null : typeObj.toString()));
        }
        return fields;
    }

    private List<ResolvedTableField> resolveSqlFields(NopMetaTable table) {
        String sourceSql = table.getSourceSql();
        // sqlFieldExtractor 对 空/不可解析/多语句/非 SELECT/通配符 显式失败抛 ErrorCode（不静默返回空）
        List<SqlViewField> sqlFields = sqlSelectFieldExtractor.extract(sourceSql);
        List<ResolvedTableField> fields = new ArrayList<>(sqlFields.size());
        for (SqlViewField f : sqlFields) {
            fields.add(new ResolvedTableField(f.getName(),
                    ResolvedTableField.SOURCE_SQL, f.getType()));
        }
        return fields;
    }

    private List<NopMetaEntityField> findEntityFields(String metaEntityId, IEntityDao<NopMetaEntityField> fieldDao) {
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaEntityField.PROP_NAME_metaEntityId, metaEntityId));
        return fieldDao.findAllByQuery(q);
    }
}
