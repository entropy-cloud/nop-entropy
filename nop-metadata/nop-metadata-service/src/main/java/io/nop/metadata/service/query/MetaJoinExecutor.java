package io.nop.metadata.service.query;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IEntityDao;
import io.nop.dataset.IDataRow;
import io.nop.dataset.IDataSet;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTableFilter;
import io.nop.metadata.dao.entity.NopMetaTableJoin;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.dao.IOrmEntityDao;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 跨表 JOIN 执行器（架构基线 §4.4.1，落地 D3/D4/D5）。
 *
 * <p>按左右 entity 的 {@code querySpace} 是否相同分派（D3）：
 * <ul>
 *   <li>同库（同 querySpace）→ 原生 JOIN SQL，经 {@code orm().executeQuery}（D4）。entity 表物理表名取自
 *       {@code MetaEntity.tableName}，join 字段（属性名）解析回物理列 {@code NopMetaEntityField.columnCode}。</li>
 *   <li>跨库（不同 querySpace）→ 应用层拼接（D5）：左右各取数后内存按 join key 字符串相等合并。</li>
 * </ul>
 *
 * <p>首版 JOIN 端点仅 entity（join 模型 leftEntityId/rightEntityId → MetaEntity）；sql/external 表无 entity，
 * 不能作为 join 端点（其 join 能力为 follow-up）。{@code joinType=right} 首版显式不支持。
 *
 * <p>失败路径显式（Minimum Rules #24）：无 join / 字段不匹配 / 实体未注册 / 规模超限 / right join 显式失败。
 */
public class MetaJoinExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(MetaJoinExecutor.class);

    /** 跨库拼接单侧结果集行数上限（防 OOM，超限显式失败，D5）。 */
    static final int MAX_CROSS_DB_ROWS = 10000;

    static final ErrorCode ERR_JOIN_NOT_FOUND =
            ErrorCode.define("metadata.join-not-found",
                    "NopMetaTableJoin not found or not owned by table: {metaTableId} joinId={joinId}",
                    "metaTableId", "joinId");
    static final ErrorCode ERR_JOIN_TYPE_RIGHT_UNSUPPORTED =
            ErrorCode.define("metadata.join-type-right-unsupported",
                    "joinType=right is explicitly unsupported in first version (same-DB and cross-DB): {joinId}",
                    "joinId");
    static final ErrorCode ERR_JOIN_TYPE_UNKNOWN =
            ErrorCode.define("metadata.join-type-unknown",
                    "Unknown joinType (expected inner/left/right): {joinType} joinId={joinId}",
                    "joinType", "joinId");
    static final ErrorCode ERR_JOIN_ENTITY_DANGLING =
            ErrorCode.define("metadata.join-entity-dangling",
                    "Join references a dangling entity (leftEntityId/rightEntityId not found): "
                            + "{joinId} side={side} entityId={entityId}", "joinId", "side", "entityId");
    static final ErrorCode ERR_JOIN_ENTITY_NOT_REGISTERED =
            ErrorCode.define("metadata.join-entity-not-registered",
                    "Join entity not registered in runtime IOrmSessionFactory: {joinId} side={side} "
                            + "entityName={entityName}", "joinId", "side", "entityName");
    static final ErrorCode ERR_JOIN_FIELD_NOT_RESOLVED =
            ErrorCode.define("metadata.join-field-not-resolved",
                    "Join field could not be resolved to a physical column: {joinId} side={side} "
                            + "entityId={entityId} field={field}", "joinId", "side", "entityId", "field");
    static final ErrorCode ERR_JOIN_CROSS_DB_SIZE_LIMIT =
            ErrorCode.define("metadata.join-cross-db-size-limit",
                    "Cross-DB join result set exceeds size limit ({limit}) on {side} side, abort to avoid OOM: "
                            + "{joinId} rows={rows}", "joinId", "side", "rows", "limit");

    /**
     * 执行跨表 JOIN。
     *
     * @param leftTable  join 所属逻辑表（左表）
     * @param joinId     NopMetaTableJoin 主键
     * @param userFilter 用户 filter（TreeBean，左表属性名字段；可为 null）
     * @param limit      分页/截断上限（跨库为合并后截断提示；可为 null）
     * @param offset     分页偏移（可为 null）
     * @param ctx        共享依赖上下文
     * @return {@code Map{items:[{行数据}]}}
     */
    public Map<String, Object> executeJoin(NopMetaTable leftTable, String joinId, TreeBean userFilter,
                                           Long limit, Long offset, MetaQueryContext ctx) {
        // 1. 加载 join 并校验归属
        IEntityDao<NopMetaTableJoin> joinDao = ctx.daoProvider().daoFor(NopMetaTableJoin.class);
        NopMetaTableJoin join = joinDao.getEntityById(joinId);
        if (join == null || !equalsStr(leftTable.getMetaTableId(), join.getMetaTableId())) {
            throw new NopException(ERR_JOIN_NOT_FOUND)
                    .param("metaTableId", leftTable.getMetaTableId())
                    .param("joinId", String.valueOf(joinId));
        }

        // 2. joinType 校验：right 显式不支持；未知显式失败
        String joinType = join.getJoinType();
        if (_NopMetadataCoreConstants.JOIN_TYPE_RIGHT.equals(joinType)) {
            throw new NopException(ERR_JOIN_TYPE_RIGHT_UNSUPPORTED).param("joinId", joinId);
        }
        if (!_NopMetadataCoreConstants.JOIN_TYPE_INNER.equals(joinType)
                && !_NopMetadataCoreConstants.JOIN_TYPE_LEFT.equals(joinType)) {
            throw new NopException(ERR_JOIN_TYPE_UNKNOWN)
                    .param("joinType", String.valueOf(joinType)).param("joinId", joinId);
        }

        // 3. 默认过滤器自动应用（§4.4.1，收口 0700-2 follow-up）
        IEntityDao<NopMetaTableFilter> filterDao = ctx.daoProvider().daoFor(NopMetaTableFilter.class);
        TreeBean mergedFilter = DefaultFilterApplicator.applyDefaults(leftTable, userFilter, filterDao);

        // 4. 解析左右 entity
        NopMetaEntity leftEntity = resolveEntityOrThrow(join.getLeftEntityId(), "left", joinId, ctx);
        NopMetaEntity rightEntity = resolveEntityOrThrow(join.getRightEntityId(), "right", joinId, ctx);
        requireRegistered(leftEntity, "left", joinId, ctx);
        requireRegistered(rightEntity, "right", joinId, ctx);

        // 5. D3 路由：按 querySpace 是否相同分派
        String leftQs = leftEntity.getQuerySpace();
        String rightQs = rightEntity.getQuerySpace();
        if (equalsStr(leftQs, rightQs)) {
            return buildResult(executeSameDbJoin(leftTable, join, leftEntity, rightEntity,
                    mergedFilter, limit, offset, ctx));
        }
        return buildResult(executeCrossDbMerge(join, leftEntity, rightEntity, mergedFilter, limit, offset, ctx));
    }

    // ============================ 同库 JOIN（D4）============================

    private List<Map<String, Object>> executeSameDbJoin(NopMetaTable leftTable, NopMetaTableJoin join,
                                                        NopMetaEntity leftEntity, NopMetaEntity rightEntity,
                                                        TreeBean filter, Long limit, Long offset,
                                                        MetaQueryContext ctx) {
        String leftTablePhysical = requirePhysicalTable(leftEntity);
        String rightTablePhysical = requirePhysicalTable(rightEntity);

        // 物理列集合 + join 字段解析（属性名 → columnCode）
        Map<String, String> leftPropToCol = resolveEntityColumns(leftEntity, ctx);
        Map<String, String> rightPropToCol = resolveEntityColumns(rightEntity, ctx);
        String leftJoinCol = resolveFieldToColumn(leftPropToCol, join.getLeftField(), leftEntity, "left", join.getJoinId());
        String rightJoinCol = resolveFieldToColumn(rightPropToCol, join.getRightField(), rightEntity, "right", join.getJoinId());

        // 标识符白名单校验（物理表名/列名）
        FilterToSqlTranslator.validateIdentifier(leftTablePhysical);
        FilterToSqlTranslator.validateIdentifier(rightTablePhysical);
        FilterToSqlTranslator.validateIdentifier(leftJoinCol);
        FilterToSqlTranslator.validateIdentifier(rightJoinCol);

        // 构造 JOIN SQL：仅投影 join key 列（左 key + 右 key，alias 前缀防冲突）。
        // 说明：不投影全部物理列——部分列名为 SQL/EQL 保留字（如 PRECISION/SCALE/NUMBER），裸拼接会被
        // EQL 编译器拒绝（parse-fail）。join key 列足以证明关联关系且列名安全。跨库路径返回全部属性列。
        // EQL 经 orm().executeQuery 编译：table-qualified 列须用 FROM 中声明的别名（t1/t2），不可直接用表名。
        String alias = aliasOf(join);
        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append("t1.").append(leftJoinCol).append(" AS ").append(leftJoinCol);
        FilterToSqlTranslator.validateIdentifier(leftJoinCol);
        String rightOutName = alias + "_" + rightJoinCol;
        FilterToSqlTranslator.validateIdentifier(rightOutName);
        sql.append(",t2.").append(rightJoinCol).append(" AS ").append(rightOutName);
        sql.append(" FROM ").append(leftTablePhysical).append(" t1");
        String joinKeyword = _NopMetadataCoreConstants.JOIN_TYPE_INNER.equals(join.getJoinType())
                ? " INNER JOIN " : " LEFT JOIN ";
        sql.append(joinKeyword).append(rightTablePhysical).append(" t2")
                .append(" ON t1.").append(leftJoinCol)
                .append(" = t2.").append(rightJoinCol);

        // filter → WHERE（属性名解析为左表物理列）
        List<Object> params = new ArrayList<>();
        if (filter != null) {
            TreeBean colFilter = rewriteFilterToColumns(filter, leftPropToCol);
            FilterToSqlTranslator.TranslatedFilter tf = ctx.filterTranslator().translate(colFilter);
            if (tf.getSql() != null && !tf.getSql().isEmpty()) {
                sql.append(" WHERE ").append(tf.getSql());
                params.addAll(tf.getParams());
            }
        }
        if (limit != null) {
            sql.append(" LIMIT ?");
            params.add(limit);
        }
        if (offset != null && offset > 0) {
            sql.append(" OFFSET ?");
            params.add(offset);
        }

        String sqlText = sql.toString();
        LOG.info("queryJoinData same-DB SQL: {}", sqlText);
        // entity 表 querySpace 由 ORM 管理 → 经 orm().executeQuery（物理表+物理列，allowUnderscoreName）
        SQL sqlObj = SQL.begin().allowUnderscoreName(true).sql(sqlText, params.toArray()).end();
        return ctx.orm().executeQuery(sqlObj, null, this::collectRows);
    }

    // ============================ 跨库拼接（D5）============================

    private List<Map<String, Object>> executeCrossDbMerge(NopMetaTableJoin join, NopMetaEntity leftEntity,
                                                          NopMetaEntity rightEntity, TreeBean filter,
                                                          Long limit, Long offset, MetaQueryContext ctx) {
        // 左/右各取数（entity 走 ORM；首版 join 端点仅 entity）
        List<Map<String, Object>> leftRows = fetchEntityRows(leftEntity, filter, ctx, "left", join.getJoinId());
        List<Map<String, Object>> rightRows = fetchEntityRows(rightEntity, null, ctx, "right", join.getJoinId());

        // 规模上限（防 OOM）
        checkSizeLimit(leftRows.size(), "left", join.getJoinId());
        checkSizeLimit(rightRows.size(), "right", join.getJoinId());

        String leftField = join.getLeftField();
        String rightField = join.getRightField();
        String alias = aliasOf(join);
        String joinType = join.getJoinType();

        // 右表按 join key 建索引（字符串相等匹配）
        Map<String, List<Map<String, Object>>> rightIndex = new HashMap<>();
        for (Map<String, Object> r : rightRows) {
            String key = stringKey(r.get(rightField));
            rightIndex.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }

        // 左列集合（用于冲突检测）
        Set<String> leftKeys = leftRows.isEmpty() ? Collections.emptySet() : new HashSet<>(leftRows.get(0).keySet());

        List<Map<String, Object>> merged = new ArrayList<>();
        for (Map<String, Object> l : leftRows) {
            String key = stringKey(l.get(leftField));
            List<Map<String, Object>> matches = rightIndex.get(key);
            if (matches != null && !matches.isEmpty()) {
                for (Map<String, Object> r : matches) {
                    merged.add(mergeRow(l, r, alias, leftKeys));
                }
            } else if (_NopMetadataCoreConstants.JOIN_TYPE_LEFT.equals(joinType)) {
                // left join：保留左行，右列填 null
                merged.add(mergeRow(l, null, alias, leftKeys));
            }
            // inner join：无匹配则丢弃
        }

        // 跨库无全局序，limit/offset 仅作合并后截断提示（D5 已知限制）
        return truncate(merged, limit, offset);
    }

    // ============================ 取数/合并 helpers ============================

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<Map<String, Object>> fetchEntityRows(NopMetaEntity entity, TreeBean filter, MetaQueryContext ctx,
                                                      String side, String joinId) {
        String entityName = entity.getEntityName();
        IOrmEntityDao<IOrmEntity> dao = (IOrmEntityDao<IOrmEntity>) (IOrmEntityDao) ctx.daoProvider().dao(entityName);
        QueryBean q = new QueryBean();
        if (filter != null) {
            q.setFilter(filter);
        }
        q.setLimit(MAX_CROSS_DB_ROWS + 1);
        List<IOrmEntity> entities = dao.findAllByQuery(q);
        IEntityModel entityModel = dao.getEntityModel();
        List<String> propNames = new ArrayList<>();
        for (IColumnModel col : entityModel.getColumns()) {
            propNames.add(col.getName());
        }
        List<Map<String, Object>> rows = new ArrayList<>(entities.size());
        for (IOrmEntity row : entities) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (String prop : propNames) {
                map.put(prop, row.orm_propValueByName(prop));
            }
            rows.add(map);
        }
        return rows;
    }

    private Map<String, Object> mergeRow(Map<String, Object> left, Map<String, Object> right,
                                         String alias, Set<String> leftKeys) {
        Map<String, Object> row = new LinkedHashMap<>();
        if (left != null) {
            row.putAll(left);
        }
        if (right != null) {
            for (Map.Entry<String, Object> e : right.entrySet()) {
                String k = e.getKey();
                // 列名冲突时加 alias_ 前缀（D5）
                if (leftKeys != null && leftKeys.contains(k)) {
                    k = alias + "_" + k;
                }
                row.put(k, e.getValue());
            }
        }
        return row;
    }

    // ============================ 解析 helpers ============================

    private NopMetaEntity resolveEntityOrThrow(String entityId, String side, String joinId, MetaQueryContext ctx) {
        if (entityId == null || entityId.isEmpty()) {
            throw new NopException(ERR_JOIN_ENTITY_DANGLING)
                    .param("joinId", joinId).param("side", side).param("entityId", String.valueOf(entityId));
        }
        IEntityDao<NopMetaEntity> dao = ctx.daoProvider().daoFor(NopMetaEntity.class);
        NopMetaEntity entity = dao.getEntityById(entityId);
        if (entity == null) {
            throw new NopException(ERR_JOIN_ENTITY_DANGLING)
                    .param("joinId", joinId).param("side", side).param("entityId", entityId);
        }
        return entity;
    }

    private void requireRegistered(NopMetaEntity entity, String side, String joinId, MetaQueryContext ctx) {
        String name = entity.getEntityName();
        if (name == null || name.isEmpty() || !ctx.orm().isValidEntityName(name)) {
            throw new NopException(ERR_JOIN_ENTITY_NOT_REGISTERED)
                    .param("joinId", joinId).param("side", side).param("entityName", String.valueOf(name));
        }
    }

    private String requirePhysicalTable(NopMetaEntity entity) {
        String t = entity.getTableName();
        if (t == null || t.trim().isEmpty()) {
            throw new NopException(ERR_JOIN_FIELD_NOT_RESOLVED)
                    .param("side", "table").param("entityId", entity.getMetaEntityId())
                    .param("field", "tableName").param("joinId", "");
        }
        return t;
    }

    /** 解析 entity 的属性名→物理列名映射（按 metaEntityId 查 NopMetaEntityField）。 */
    private Map<String, String> resolveEntityColumns(NopMetaEntity entity, MetaQueryContext ctx) {
        IEntityDao<NopMetaEntityField> fieldDao = ctx.daoProvider().daoFor(NopMetaEntityField.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaEntityField.PROP_NAME_metaEntityId, entity.getMetaEntityId()));
        List<NopMetaEntityField> fields = fieldDao.findAllByQuery(q);
        Map<String, String> propToCol = new LinkedHashMap<>();
        for (NopMetaEntityField f : fields) {
            if (f.getFieldName() != null && f.getColumnCode() != null) {
                propToCol.put(f.getFieldName(), f.getColumnCode());
            }
        }
        return propToCol;
    }

    private String resolveFieldToColumn(Map<String, String> propToCol, String field, NopMetaEntity entity,
                                        String side, String joinId) {
        if (field == null) {
            throw new NopException(ERR_JOIN_FIELD_NOT_RESOLVED)
                    .param("joinId", joinId).param("side", side)
                    .param("entityId", entity.getMetaEntityId()).param("field", String.valueOf(field));
        }
        String col = propToCol.get(field);
        if (col == null) {
            throw new NopException(ERR_JOIN_FIELD_NOT_RESOLVED)
                    .param("joinId", joinId).param("side", side)
                    .param("entityId", entity.getMetaEntityId()).param("field", field);
        }
        return col;
    }

    /** 把 filter 叶子的字段名（左表属性名）重写为物理列名。未命中映射的原样保留（后续白名单校验会显式失败）。 */
    private TreeBean rewriteFilterToColumns(TreeBean filter, Map<String, String> propToCol) {
        if (filter == null) {
            return null;
        }
        TreeBean copy = new TreeBean(filter.getTagName());
        if (filter.getAttrs() != null) {
            copy.setAttrs(new LinkedHashMap<>(filter.getAttrs()));
        }
        // 叶子条件：有 name 属性 → 重写
        Object name = copy.getAttr("name");
        if (name != null && propToCol.containsKey(name.toString())) {
            copy.setAttr("name", propToCol.get(name.toString()));
        }
        if (filter.getChildren() != null) {
            List<TreeBean> children = new ArrayList<>();
            for (TreeBean child : filter.getChildren()) {
                children.add(rewriteFilterToColumns(child, propToCol));
            }
            copy.setChildren(children);
        }
        return copy;
    }

    private void checkSizeLimit(int rows, String side, String joinId) {
        if (rows > MAX_CROSS_DB_ROWS) {
            throw new NopException(ERR_JOIN_CROSS_DB_SIZE_LIMIT)
                    .param("joinId", joinId).param("side", side).param("rows", rows)
                    .param("limit", MAX_CROSS_DB_ROWS);
        }
    }

    private List<Map<String, Object>> truncate(List<Map<String, Object>> rows, Long limit, Long offset) {
        int from = (offset != null && offset > 0) ? Math.toIntExact(offset) : 0;
        if (from > rows.size()) {
            from = rows.size();
        }
        int to = rows.size();
        if (limit != null) {
            to = Math.min(rows.size(), from + Math.toIntExact(limit));
        }
        return new ArrayList<>(rows.subList(from, to));
    }

    private List<Map<String, Object>> collectRows(IDataSet ds) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (IDataRow row : ds) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < row.getFieldCount(); i++) {
                map.put(row.getMeta().getFieldName(i), row.getObject(i));
            }
            rows.add(map);
        }
        return rows;
    }

    private static String aliasOf(NopMetaTableJoin join) {
        String a = join.getAlias();
        return (a != null && !a.trim().isEmpty()) ? a : "right";
    }

    private static String stringKey(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static boolean equalsStr(String a, String b) {
        return (a == null) ? b == null : a.equals(b);
    }

    private static Map<String, Object> buildResult(List<Map<String, Object>> items) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items != null ? items : new ArrayList<>());
        return result;
    }
}
