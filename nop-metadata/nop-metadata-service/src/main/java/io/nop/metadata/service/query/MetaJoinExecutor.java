/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.metadata.service.query;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IEntityDao;
import io.nop.dataset.IDataRow;
import io.nop.dataset.IDataSet;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTableFilter;
import io.nop.metadata.dao.entity.NopMetaTableJoin;
import io.nop.metadata.service.field.ResolvedTableField;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.dao.IOrmEntityDao;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.metadata.service.NopMetadataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 跨表 JOIN 执行器（架构基线 §4.4.1，落地 D3/D4/D5 + plan 0700-2 D1 端点组合扩展）。
 *
 * <p>端点组合路由（plan 0700-2 D1.2）：
 * <ul>
 *   <li><b>entity ↔ entity</b>：同 querySpace → 原生 JOIN SQL（{@code orm().executeQuery}，D4 既有，不重写为 withConnection）；
 *       不同 querySpace → 应用层拼接（D5，各侧 {@link #fetchEntityRows}）。</li>
 *   <li><b>external/sql ↔ external/sql</b>：同 querySpace → {@code withConnection} 原生 JOIN SQL（D4 新增，共享 NopMetaDataSource）；
 *       不同 querySpace → 应用层拼接（D5，各侧 {@link #fetchTableRows}）。</li>
 *   <li><b>混合端点（entity ↔ external/sql）</b>：**统一走应用层拼接（D5）**——两类端点连接机制本质不同，
 *       即便 querySpace 字符串相同也无法单连接跑原生 JOIN。entity 侧 {@link #fetchEntityRows}（ORM DAO，
 *       不要求 entity querySpace 注册 NopMetaDataSource）+ table 侧 {@link #fetchTableRows}（{@code withConnection}）。</li>
 * </ul>
 *
 * <p>跨库拼接 key 命名空间规范化（D1.4，Anti-Hollow）：entity 行 key 为 camelCase 属性名，
 * external/sql 行 key 为物理列名（H2 常大写）。合并前显式校验 {@code leftField}/{@code rightField}
 * 在各侧 row Map 的 keySet 中（非空集时），命名空间错配**显式失败抛 inline ErrorCode**（不静默空集）。
 *
 * <p>{@code joinType=right} 首版全局显式不支持（同库 + 跨库）。
 *
 * <p>失败路径显式（Minimum Rules #24）：无 join / 字段不匹配 / 实体未注册 / 规模超限 / right join /
 * 命名空间错配 / querySpace 无数据源 均显式失败。
 */
public class MetaJoinExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(MetaJoinExecutor.class);

    private final CrossDbJoinMerger crossDbMerger;

    public MetaJoinExecutor() {
        this.crossDbMerger = new CrossDbJoinMerger(CrossDbConfigHolder.maxCrossDbRows);
    }

    MetaJoinExecutor(CrossDbJoinMerger crossDbMerger) {
        this.crossDbMerger = crossDbMerger;
    }

    /* All ErrorCode constants migrated to NopMetadataErrors */

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
        // 1. 加载 join 并校验归属 + joinType（抽取为共享方法 loadValidatedJoin，供聚合执行器复用，
        //    显式选定「抽取共享」而非「复刻」，避免去重 debt，见 plan 0852-1 Phase 1 Decision）
        NopMetaTableJoin join = loadValidatedJoin(leftTable, joinId, ctx);

        // 3. 默认过滤器自动应用（§4.4.1，收口 0700-2 follow-up）
        IEntityDao<NopMetaTableFilter> filterDao = ctx.daoProvider().daoFor(NopMetaTableFilter.class);
        TreeBean mergedFilter = DefaultFilterApplicator.applyDefaults(leftTable, userFilter, filterDao);

        // 4. 解析左右端点（plan 0700-2 D1：entity 端点或 external/sql table 端点）
        Endpoint leftEp = resolveEndpoint(join, "left", join.getLeftEntityId(), join.getLeftTableId(), ctx);
        Endpoint rightEp = resolveEndpoint(join, "right", join.getRightEntityId(), join.getRightTableId(), ctx);

        // 5. 按端点组合路由（D1.2）
        if (leftEp.isEntity() && rightEp.isEntity()) {
            requireRegistered(leftEp.entity, "left", joinId, ctx);
            requireRegistered(rightEp.entity, "right", joinId, ctx);
            String leftQs = leftEp.entity.getQuerySpace();
            String rightQs = rightEp.entity.getQuerySpace();
            if (equalsStr(leftQs, rightQs)) {
                return buildResult(executeSameDbJoin(leftTable, join, leftEp.entity, rightEp.entity,
                        mergedFilter, limit, offset, ctx));
            }
            return buildResult(doCrossDbMergeEntityEntity(join, leftEp.entity, rightEp.entity,
                    mergedFilter, limit, offset, ctx));
        }
        if (!leftEp.isEntity() && !rightEp.isEntity()) {
            String leftQs = leftEp.table.getQuerySpace();
            String rightQs = rightEp.table.getQuerySpace();
            if (equalsStr(leftQs, rightQs)) {
                return buildResult(executeSameDbTableJoin(leftTable, join, leftEp, rightEp,
                        mergedFilter, limit, offset, ctx));
            }
            return buildResult(doCrossDbMergeTableEndpoint(join, leftEp, rightEp,
                    mergedFilter, limit, offset, ctx));
        }
        if (leftEp.isEntity) {
            requireRegistered(leftEp.entity, "left", joinId, ctx);
        }
        if (rightEp.isEntity) {
            requireRegistered(rightEp.entity, "right", joinId, ctx);
        }
        return buildResult(doCrossDbMergeMixed(join, leftEp, rightEp, mergedFilter, limit, offset, ctx));
    }

    // ============================ 共享 join 加载/校验（plan 0852-1 抽取，供聚合执行器复用）============================

    /**
     * 加载 {@link NopMetaTableJoin} 并校验归属 + joinType（共享方法，JOIN 执行与 JOIN 聚合共用）。
     *
     * <p>校验语义：
     * <ul>
     *   <li>join 不存在或不归属 leftTable → {@link #NopMetadataErrors.ERR_JOIN_NOT_FOUND}</li>
     *   <li>joinType=right → {@link #NopMetadataErrors.ERR_JOIN_TYPE_RIGHT_UNSUPPORTED}（首版显式不支持，不静默降级）</li>
     *   <li>joinType 未知（非 inner/left/right）→ {@link #NopMetadataErrors.ERR_JOIN_TYPE_UNKNOWN}</li>
     * </ul>
     *
     * @param leftTable join 所属逻辑表（左表）
     * @param joinId    NopMetaTableJoin 主键
     * @param ctx       共享依赖上下文
     * @return 已校验的 join
     */
    public NopMetaTableJoin loadValidatedJoin(NopMetaTable leftTable, String joinId, MetaQueryContext ctx) {
        IEntityDao<NopMetaTableJoin> joinDao = ctx.daoProvider().daoFor(NopMetaTableJoin.class);
        NopMetaTableJoin join = joinDao.getEntityById(joinId);
        if (join == null || !equalsStr(leftTable.getMetaTableId(), join.getMetaTableId())) {
            throw new NopMetadataException(NopMetadataErrors.ERR_JOIN_NOT_FOUND)
                    .param("metaTableId", leftTable.getMetaTableId())
                    .param("joinId", String.valueOf(joinId));
        }
        String joinType = join.getJoinType();
        if (_NopMetadataCoreConstants.JOIN_TYPE_RIGHT.equals(joinType)) {
            throw new NopMetadataException(NopMetadataErrors.ERR_JOIN_TYPE_RIGHT_UNSUPPORTED).param("joinId", joinId);
        }
        if (!_NopMetadataCoreConstants.JOIN_TYPE_INNER.equals(joinType)
                && !_NopMetadataCoreConstants.JOIN_TYPE_LEFT.equals(joinType)) {
            throw new NopMetadataException(NopMetadataErrors.ERR_JOIN_TYPE_UNKNOWN)
                    .param("joinType", String.valueOf(joinType)).param("joinId", joinId);
        }
        return join;
    }

    // ============================ 端点解析（D1）============================

    /** 解析单个端点：entity 端点（entityId 非空）或 table 端点（tableId 非空，须 external/sql）。 */
    Endpoint resolveEndpoint(NopMetaTableJoin join, String side, String entityId, String tableId,
                                     MetaQueryContext ctx) {
        boolean hasEntity = entityId != null && !entityId.isEmpty();
        boolean hasTable = tableId != null && !tableId.isEmpty();
        String joinId = join.getJoinId();
        if (hasEntity && hasTable) {
            // 互斥校验在 save 路径已做（0700-1）；executor 防御性显式失败
            throw new NopMetadataException(NopMetadataErrors.ERR_JOIN_NO_ENDPOINT).param("joinId", joinId).param("side", side);
        }
        if (!hasEntity && !hasTable) {
            throw new NopMetadataException(NopMetadataErrors.ERR_JOIN_NO_ENDPOINT).param("joinId", joinId).param("side", side);
        }
        if (hasEntity) {
            return Endpoint.entity(resolveEntityOrThrow(entityId, side, joinId, ctx));
        }
        return Endpoint.table(resolveTableEndpointOrThrow(tableId, side, joinId, ctx));
    }

    /** 解析 table 端点：表存在 + tableType ∈ {external, sql}（entity-type 逻辑表应走 entityId 路径）。 */
    private NopMetaTable resolveTableEndpointOrThrow(String tableId, String side, String joinId, MetaQueryContext ctx) {
        IEntityDao<NopMetaTable> tableDao = ctx.daoProvider().daoFor(NopMetaTable.class);
        NopMetaTable table = tableDao.getEntityById(tableId);
        if (table == null) {
            throw new NopMetadataException(NopMetadataErrors.ERR_JOIN_TABLE_DANGLING)
                    .param("joinId", joinId).param("side", side).param("tableId", tableId);
        }
        String tableType = table.getTableType();
        if (!_NopMetadataCoreConstants.TABLE_TYPE_EXTERNAL.equals(tableType)
                && !_NopMetadataCoreConstants.TABLE_TYPE_SQL.equals(tableType)) {
            throw new NopMetadataException(NopMetadataErrors.ERR_JOIN_TABLE_TYPE_NOT_ALLOWED)
                    .param("joinId", joinId).param("side", side)
                    .param("tableId", tableId).param("tableType", String.valueOf(tableType));
        }
        return table;
    }

    /** JOIN 端点：entity 端点或 external/sql table 端点（二选一）。package-private 以便聚合执行器复用端点解析。 */
    static final class Endpoint {
        final boolean isEntity;
        final NopMetaEntity entity;
        final NopMetaTable table;

        private Endpoint(boolean isEntity, NopMetaEntity entity, NopMetaTable table) {
            this.isEntity = isEntity;
            this.entity = entity;
            this.table = table;
        }

        static Endpoint entity(NopMetaEntity e) {
            return new Endpoint(true, e, null);
        }

        static Endpoint table(NopMetaTable t) {
            return new Endpoint(false, null, t);
        }

        boolean isEntity() {
            return isEntity;
        }
    }

    // ============================ 同库 entity-entity JOIN（D4，保持不变）============================

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
        // AR-04: 按方言拼接 LIMIT/OFFSET（entity-entity JOIN EQL 路径走平台默认方言 H2，offset-only 合法）
        SqlPagination.appendLimitOffset(sql, limit, offset, null);
        if (limit != null) {
            params.add(limit);
        }
        if (offset != null && offset > 0) {
            params.add(offset);
        }

        String sqlText = sql.toString();
        LOG.info("queryJoinData same-DB entity-entity SQL: {}", sqlText);
        // entity 表 querySpace 由 ORM 管理 → 经 orm().executeQuery（物理表+物理列，allowUnderscoreName）
        SQL sqlObj = SQL.begin().allowUnderscoreName(true).sql(sqlText, params.toArray()).end();
        return ctx.orm().executeQuery(sqlObj, null, this::collectRows);
    }

    // ============================ 同库 external/sql ↔ external/sql JOIN（D4，plan 0700-2 新增）============================

    /** 同库 table 端点 JOIN：两端点同 querySpace → 单次 withConnection 跑原生 JOIN SQL（共享数据源）。 */
    private List<Map<String, Object>> executeSameDbTableJoin(NopMetaTable leftTable, NopMetaTableJoin join,
                                                              Endpoint leftEp, Endpoint rightEp,
                                                              TreeBean filter, Long limit, Long offset,
                                                              MetaQueryContext ctx) {
        // 共享数据源（两端点同 querySpace）
        NopMetaDataSource dataSource = resolveTableDataSourceOrThrow(leftEp.table, ctx, join.getJoinId(), "left");

        // 解析两端点列集合 + join 字段（table 端点字段即物理列名，校验属于该表解析列集合）
        Set<String> leftCols = resolveTableColumnNames(leftEp.table, ctx);
        Set<String> rightCols = resolveTableColumnNames(rightEp.table, ctx);
        String leftJoinCol = resolveTableFieldOrThrow(leftCols, join.getLeftField(), leftEp.table, "left", join.getJoinId());
        String rightJoinCol = resolveTableFieldOrThrow(rightCols, join.getRightField(), rightEp.table, "right", join.getJoinId());

        FilterToSqlTranslator.validateIdentifier(leftJoinCol);
        FilterToSqlTranslator.validateIdentifier(rightJoinCol);

        // FROM 子句按 tableType 构造（external→tableName alias；sql→(<sourceSql>) alias）
        String leftFrom = tableFromForJoin(leftEp.table, "t1");
        String rightFrom = tableFromForJoin(rightEp.table, "t2");

        String alias = aliasOf(join);
        String rightOutName = alias + "_" + rightJoinCol;
        FilterToSqlTranslator.validateIdentifier(rightOutName);

        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append("t1.").append(leftJoinCol).append(" AS ").append(leftJoinCol);
        sql.append(",t2.").append(rightJoinCol).append(" AS ").append(rightOutName);
        sql.append(" FROM ").append(leftFrom);
        String joinKeyword = _NopMetadataCoreConstants.JOIN_TYPE_INNER.equals(join.getJoinType())
                ? " INNER JOIN " : " LEFT JOIN ";
        sql.append(joinKeyword).append(rightFrom)
                .append(" ON t1.").append(leftJoinCol)
                .append(" = t2.").append(rightJoinCol);

        List<Object> params = new ArrayList<>();
        if (filter != null) {
            // 左表为 table 端点：filter 字段名即左表物理列名，直接翻译（不再做属性→列名重写）
            FilterToSqlTranslator.TranslatedFilter tf = ctx.filterTranslator().translate(filter);
            if (tf.getSql() != null && !tf.getSql().isEmpty()) {
                sql.append(" WHERE ").append(tf.getSql());
                params.addAll(tf.getParams());
            }
        }
        // AR-04: SQL build 完成在 callback 外（filter/SELECT/FROM 部分），dialect-aware LIMIT/OFFSET
        // 在 callback 内补入（避免 callback 内重建 SQL，保持与 entity-entity 路径一致的代码组织）。
        // 默认按 H2 语义（offset-only 合法），如果 productName==MySQL 则需"无限大 LIMIT"占位。
        // 这里在 callback 外预先拼好 LIMIT/OFFSET 占位符（带 dialect 适配）；占位符参数由 executeJdbcQuery 绑定。
        // 由于 withConnection callback 内才知 dialect，但同库 table-table 路径 limit/offset 通常由合并后内存截断
        // （参考 crossDbMerge），此处保守按 H2 方言（与 NopMetaTableBizModel 外层 SQL 构造一致）。
        SqlPagination.appendLimitOffset(sql, limit, offset, null);
        if (limit != null) {
            params.add(limit);
        }
        if (offset != null && offset > 0) {
            params.add(offset);
        }

        final String sqlText = sql.toString();
        LOG.info("queryJoinData same-DB table-table SQL: {}", sqlText);
        final List<Map<String, Object>>[] holder = newArrayHolder();
        ctx.connectionService().withConnection(dataSource.getDatasourceType(), dataSource.getConnectionConfig(),
                (Connection conn, DatabaseMetaData metaData) -> {
                    holder[0] = executeJdbcQuery(conn, sqlText, params, limit, offset, join.getJoinId(), "same-db-table-join");
                });
        return holder[0] == null ? new ArrayList<>() : holder[0];
    }

    // ============================ 跨库拼接（D5，委派 CrossDbJoinMerger）============================

    private List<Map<String, Object>> doCrossDbMergeEntityEntity(NopMetaTableJoin join, NopMetaEntity leftEntity,
                                                                  NopMetaEntity rightEntity, TreeBean filter,
                                                                  Long limit, Long offset, MetaQueryContext ctx) {
        List<Map<String, Object>> leftRows = fetchEntityRows(leftEntity, filter, ctx, "left", join.getJoinId());
        List<Map<String, Object>> rightRows = fetchEntityRows(rightEntity, null, ctx, "right", join.getJoinId());
        return crossDbMerger.crossDbMerge(join, leftRows, rightRows, limit, offset);
    }

    private List<Map<String, Object>> doCrossDbMergeTableEndpoint(NopMetaTableJoin join, Endpoint leftEp,
                                                                   Endpoint rightEp, TreeBean filter,
                                                                   Long limit, Long offset, MetaQueryContext ctx) {
        List<Map<String, Object>> leftRows = fetchTableRows(leftEp.table, filter, ctx, "left", join.getJoinId());
        List<Map<String, Object>> rightRows = fetchTableRows(rightEp.table, null, ctx, "right", join.getJoinId());
        return crossDbMerger.crossDbMerge(join, leftRows, rightRows, limit, offset);
    }

    private List<Map<String, Object>> doCrossDbMergeMixed(NopMetaTableJoin join, Endpoint leftEp, Endpoint rightEp,
                                                          TreeBean filter, Long limit, Long offset,
                                                          MetaQueryContext ctx) {
        List<Map<String, Object>> leftRows = fetchEndpointRows(leftEp, filter, ctx, "left", join.getJoinId());
        List<Map<String, Object>> rightRows = fetchEndpointRows(rightEp, null, ctx, "right", join.getJoinId());
        return crossDbMerger.crossDbMerge(join, leftRows, rightRows, limit, offset);
    }

    private List<Map<String, Object>> fetchEndpointRows(Endpoint ep, TreeBean filter, MetaQueryContext ctx,
                                                        String side, String joinId) {
        if (ep.isEntity) {
            return fetchEntityRows(ep.entity, filter, ctx, side, joinId);
        }
        return fetchTableRows(ep.table, filter, ctx, side, joinId);
    }

    // ============================ 取数 helpers ============================

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<Map<String, Object>> fetchEntityRows(NopMetaEntity entity, TreeBean filter, MetaQueryContext ctx,
                                                       String side, String joinId) {
        String entityName = entity.getEntityName();
        IOrmEntityDao<IOrmEntity> dao = castToOrmEntityDao(ctx.daoProvider().dao(entityName));
        QueryBean q = new QueryBean();
        if (filter != null) {
            q.setFilter(filter);
        }
        q.setLimit(CrossDbConfigHolder.maxCrossDbRows + 1);
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

    private List<Map<String, Object>> fetchTableRows(NopMetaTable table, TreeBean filter, MetaQueryContext ctx,
                                                     String side, String joinId) {
        NopMetaDataSource dataSource = resolveTableDataSourceOrThrow(table, ctx, joinId, side);
        IEntityDao<NopMetaEntityField> fieldDao = ctx.daoProvider().daoFor(NopMetaEntityField.class);
        List<ResolvedTableField> fields = ctx.fieldResolver().resolve(table, fieldDao);
        List<String> columns = new ArrayList<>(fields.size());
        for (ResolvedTableField f : fields) {
            columns.add(f.getName());
        }

        final List<Map<String, Object>>[] holder = newArrayHolder();
        ctx.connectionService().withConnection(dataSource.getDatasourceType(), dataSource.getConnectionConfig(),
                (Connection conn, DatabaseMetaData metaData) -> {
                    FilterToSqlTranslator.TranslatedFilter tf = ctx.filterTranslator().translate(filter);
                    Long fetchLimit = (long) CrossDbConfigHolder.maxCrossDbRows + 1;
                    String sql = buildTableSelectSql(table, columns, tf.getSql(), fetchLimit, null);
                    holder[0] = executeJdbcQuery(conn, sql, tf.getParams(), fetchLimit, null, joinId, side);
                });
        return holder[0] == null ? new ArrayList<>() : holder[0];
    }

    // ============================ 解析 helpers ============================

    private NopMetaEntity resolveEntityOrThrow(String entityId, String side, String joinId, MetaQueryContext ctx) {
        if (entityId == null || entityId.isEmpty()) {
            throw new NopMetadataException(NopMetadataErrors.ERR_JOIN_ENTITY_DANGLING)
                    .param("joinId", joinId).param("side", side).param("entityId", String.valueOf(entityId));
        }
        IEntityDao<NopMetaEntity> dao = ctx.daoProvider().daoFor(NopMetaEntity.class);
        NopMetaEntity entity = dao.getEntityById(entityId);
        if (entity == null) {
            throw new NopMetadataException(NopMetadataErrors.ERR_JOIN_ENTITY_DANGLING)
                    .param("joinId", joinId).param("side", side).param("entityId", entityId);
        }
        return entity;
    }

    /** 校验 entity 已注册于运行时 IOrmSessionFactory（package-private 以便聚合执行器复用）。 */
    void requireRegistered(NopMetaEntity entity, String side, String joinId, MetaQueryContext ctx) {
        if (entity == null) {
            return;
        }
        String name = entity.getEntityName();
        if (name == null || name.isEmpty() || !ctx.orm().isValidEntityName(name)) {
            throw new NopMetadataException(NopMetadataErrors.ERR_JOIN_ENTITY_NOT_REGISTERED)
                    .param("joinId", joinId).param("side", side).param("entityName", String.valueOf(name));
        }
    }

    private String requirePhysicalTable(NopMetaEntity entity) {
        String t = entity.getTableName();
        if (t == null || t.trim().isEmpty()) {
            throw new NopMetadataException(NopMetadataErrors.ERR_JOIN_FIELD_NOT_RESOLVED)
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

    /** 属性名 → 物理列名解析（join field 解析复用，package-private 以便聚合执行器复用）。 */
    String resolveFieldToColumn(Map<String, String> propToCol, String field, NopMetaEntity entity,
                                        String side, String joinId) {
        if (field == null) {
            throw new NopMetadataException(NopMetadataErrors.ERR_JOIN_FIELD_NOT_RESOLVED)
                    .param("joinId", joinId).param("side", side)
                    .param("entityId", entity.getMetaEntityId()).param("field", String.valueOf(field));
        }
        String col = propToCol.get(field);
        if (col == null) {
            throw new NopMetadataException(NopMetadataErrors.ERR_JOIN_FIELD_NOT_RESOLVED)
                    .param("joinId", joinId).param("side", side)
                    .param("entityId", entity.getMetaEntityId()).param("field", field);
        }
        return col;
    }

    /** 解析 table 端点的列名集合（external→buildSql columnName；sql→SELECT 解析列）。 */
    private Set<String> resolveTableColumnNames(NopMetaTable table, MetaQueryContext ctx) {
        IEntityDao<NopMetaEntityField> fieldDao = ctx.daoProvider().daoFor(NopMetaEntityField.class);
        List<ResolvedTableField> fields = ctx.fieldResolver().resolve(table, fieldDao);
        Set<String> names = newLinkedHashSet(fields.size());
        for (ResolvedTableField f : fields) {
            names.add(f.getName());
        }
        return names;
    }

    /** table 端点 join 字段解析：field 须属于该表可解析列集合（防御性校验，save 路径 0700-1 已校验）。 */
    private String resolveTableFieldOrThrow(Set<String> columns, String field, NopMetaTable table,
                                            String side, String joinId) {
        if (field == null || !columns.contains(field)) {
            throw new NopMetadataException(NopMetadataErrors.ERR_JOIN_TABLE_FIELD_NOT_RESOLVED)
                    .param("joinId", joinId).param("side", side)
                    .param("tableId", table.getMetaTableId()).param("field", String.valueOf(field));
        }
        return field;
    }

    /** querySpace→数据源解析（external/sql 端点共用），失败时附加 joinId/side 上下文。 */
    private NopMetaDataSource resolveTableDataSourceOrThrow(NopMetaTable table, MetaQueryContext ctx,
                                                            String joinId, String side) {
        IEntityDao<NopMetaDataSource> dsDao = ctx.daoProvider().daoFor(NopMetaDataSource.class);
        try {
            return ctx.dataSourceResolver().resolveActiveOrThrow(dsDao, table.getQuerySpace());
        } catch (NopException e) {
            if (e.getParam("joinId") == null) {
                e.param("joinId", joinId).param("side", side).param("tableId", table.getMetaTableId());
            }
            throw e;
        }
    }

    /** table 端点 FROM 子句（单表 SELECT 用）：external→{@code <tableName>}；sql→{@code (<sourceSql>) _t}。标识符/表名白名单校验。 */
    private String buildTableFromClause(NopMetaTable table) {
        if (_NopMetadataCoreConstants.TABLE_TYPE_SQL.equals(table.getTableType())) {
            String sourceSql = table.getSourceSql();
            if (sourceSql == null || sourceSql.trim().isEmpty()) {
                throw new NopMetadataException(NopMetadataErrors.ERR_JOIN_TABLE_EXEC_FAILED)
                        .param("joinId", "").param("side", "from")
                        .param("error", "sql table sourceSql is empty: " + table.getMetaTableId());
            }
            // sourceSql 为用户显式提供（与 custom_sql 同已知显式风险），不解析不改写
            return "(" + sourceSql + ") _t";
        }
        String tableName = table.getTableName();
        FilterToSqlTranslator.validateIdentifier(tableName);
        return tableName;
    }

    /**
     * table 端点 JOIN FROM 子句（带显式别名）：external→{@code <tableName> <alias>}；
     * sql→{@code (<sourceSql>) <alias>}。JOIN 场景必须显式别名（t1/t2），用于 table-qualified 列引用。
     */
    private String tableFromForJoin(NopMetaTable table, String alias) {
        FilterToSqlTranslator.validateIdentifier(alias);
        if (_NopMetadataCoreConstants.TABLE_TYPE_SQL.equals(table.getTableType())) {
            String sourceSql = table.getSourceSql();
            if (sourceSql == null || sourceSql.trim().isEmpty()) {
                throw new NopMetadataException(NopMetadataErrors.ERR_JOIN_TABLE_EXEC_FAILED)
                        .param("joinId", "").param("side", "from")
                        .param("error", "sql table sourceSql is empty: " + table.getMetaTableId());
            }
            return "(" + sourceSql + ") " + alias;
        }
        String tableName = table.getTableName();
        FilterToSqlTranslator.validateIdentifier(tableName);
        return tableName + " " + alias;
    }

    /** 构建 table 端点 SELECT SQL：{@code SELECT col1,col2 FROM <fromClause> [WHERE <filter>] [LIMIT ?]}。 */
    private String buildTableSelectSql(NopMetaTable table, List<String> columns, String filterSql,
                                       Long limit, Long offset) {
        StringBuilder sb = new StringBuilder("SELECT ");
        if (columns.isEmpty()) {
            sb.append("*");
        } else {
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    sb.append(",");
                }
                FilterToSqlTranslator.validateIdentifier(columns.get(i));
                sb.append(columns.get(i));
            }
        }
        sb.append(" FROM ").append(buildTableFromClause(table));
        if (filterSql != null && !filterSql.isEmpty()) {
            sb.append(" WHERE ").append(filterSql);
        }
        // AR-04: 按方言拼接 LIMIT/OFFSET（fetchTableRows 单侧取数调用方传 fetchLimit + offset=null，
        // 不触发 offset-only 路径；为保持 10 处拼接语义统一仍走 helper，dialect=null 走 H2 默认）
        SqlPagination.appendLimitOffset(sb, limit, offset, null);
        return sb.toString();
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

    /** 执行 JDBC 查询（filter 参数 + limit/offset 按序绑定），返回行列表（每行为列名→值 Map）。 */
    private List<Map<String, Object>> executeJdbcQuery(Connection conn, String sql, List<Object> filterParams,
                                                       Long limit, Long offset, String joinId, String side) {
        LOG.info("queryJoinData table-endpoint SQL [side={}]: {}", side, sql);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement st = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Object p : filterParams) {
                st.setObject(idx++, p);
            }
            if (limit != null) {
                st.setObject(idx++, limit);
            }
            if (offset != null && offset > 0) {
                st.setObject(idx++, offset);
            }
            try (ResultSet rs = st.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int c = 1; c <= columnCount; c++) {
                        String label = meta.getColumnLabel(c);
                        if (label == null || label.isEmpty()) {
                            label = meta.getColumnName(c);
                        }
                        row.put(label, rs.getObject(c));
                    }
                    rows.add(row);
                }
            }
            return rows;
        } catch (SQLException e) {
            throw new NopMetadataException(NopMetadataErrors.ERR_JOIN_TABLE_EXEC_FAILED, e)
                    .param("joinId", joinId).param("side", side)
                    .param("error", messageOf(e));
        }
    }

    private static String aliasOf(NopMetaTableJoin join) {
        String a = join.getAlias();
        return (a != null && !a.trim().isEmpty()) ? a : "right";
    }

    private static boolean equalsStr(String a, String b) {
        return (a == null) ? b == null : a.equals(b);
    }

    @SuppressWarnings("unchecked")
    private static IOrmEntityDao<IOrmEntity> castToOrmEntityDao(IEntityDao<?> dao) {
        return (IOrmEntityDao<IOrmEntity>) dao;
    }

    private static String messageOf(Throwable t) {
        String m = t.getMessage();
        return m != null ? m : t.getClass().getName();
    }

    private static List<Map<String, Object>>[] newArrayHolder() {
        return ArrayHolderUtils.newArrayHolder();
    }

    private static Map<String, Object> buildResult(List<Map<String, Object>> items) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items != null ? items : new ArrayList<>());
        return result;
    }

    /** 保持插入顺序的 Set 工厂（基于 LinkedHashMap）。 */
    private static Set<String> newLinkedHashSet(int expectedSize) {
        return Collections.newSetFromMap(new LinkedHashMap<>(expectedSize));
    }
}
