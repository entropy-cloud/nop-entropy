/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.metadata.service.query;

import io.nop.api.core.beans.FilterBeanConstants;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTableFilter;
import io.nop.metadata.dao.entity.NopMetaTableJoin;
import io.nop.metadata.service.field.ExpressionMeasureValidator;
import io.nop.metadata.service.NopMetadataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 指标/维度聚合查询执行器（架构基线 §4.4.2，落地 D6/D7）。
 *
 * <p>按 tableType 路由聚合执行（D6）：
 * <ul>
 *   <li>{@code external}/{@code sql}：经 ExternalAggregationProcessor / SqlAggregationProcessor 跑原生聚合 SQL。</li>
 *   <li>{@code entity}：经 EntityAggregationProcessor 跑聚合 SQL。</li>
 *   <li>JOIN 聚合：经对应 Join Processor 按端点组合路由。</li>
 * </ul>
 *
 * <p>aggFunc 翻译：sum/count/avg/min/max/countDistinct。时间维度 granularity 按 {@link GranularityBucketing}
 * 翻译为 SQL 分桶表达式（D7，仅 external/sql 路径完整支持）。
 *
 * <p>失败路径显式（Minimum Rules #24）：无 measure/dimension / 字段无法解析 / aggFunc 不支持 /
 * granularity 不约定 / 方言不支持 / expression unparseable/unsafe/dialect-unsupported/too-long / 实体未注册。
 */
public class MetaAggregationExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(MetaAggregationExecutor.class);

    private final MetaJoinExecutor joinExecutor;

    public MetaAggregationExecutor() {
        this(new MetaJoinExecutor());
    }

    public MetaAggregationExecutor(MetaJoinExecutor joinExecutor) {
        this.joinExecutor = Objects.requireNonNull(joinExecutor, "joinExecutor");
    }

    static final Set<String> SUPPORTED_DIALECTS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList("H2", "MySQL", "PostgreSQL")));

    /* All ErrorCode constants migrated to NopMetadataErrors */

    /**
     * 执行聚合查询（分派入口）。
     */
    public Map<String, Object> executeAggregation(NopMetaTable table, List<String> measureNames,
                                                   List<String> dimensionNames, TreeBean userFilter, String joinId,
                                                   Long limit, Long offset, TreeBean having,
                                                   List<OrderFieldBean> orderBy, MetaQueryContext ctx) {
        if (measureNames == null || measureNames.isEmpty()) {
            throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_NO_MEASURE).param("metaTableId", table.getMetaTableId());
        }
        if (dimensionNames == null || dimensionNames.isEmpty()) {
            throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_NO_DIMENSION).param("metaTableId", table.getMetaTableId());
        }
        IEntityDao<NopMetaTableFilter> filterDao = ctx.daoProvider().daoFor(NopMetaTableFilter.class);
        TreeBean mergedFilter = DefaultFilterApplicator.applyDefaults(table, userFilter, filterDao);

        // Build shared AggregationContext
        AggregationContext aggrCtx = new AggregationContext(ctx, joinExecutor);
        aggrCtx.setTable(table);
        aggrCtx.setMeasureNames(measureNames);
        aggrCtx.setDimensionNames(dimensionNames);
        aggrCtx.setFilter(mergedFilter);
        aggrCtx.setJoinId(joinId);
        aggrCtx.setLimit(limit);
        aggrCtx.setOffset(offset);
        aggrCtx.setHaving(having);
        aggrCtx.setOrderBy(orderBy);

        if (joinId != null && !joinId.isEmpty()) {
            return AggregationHelper.buildResult(executeJoinAggregation(aggrCtx, table, measureNames, dimensionNames,
                    mergedFilter, joinId, limit, offset, having, orderBy, ctx));
        }

        String tableType = table.getTableType();
        AggregationProcessor processor;
        if (_NopMetadataCoreConstants.TABLE_TYPE_ENTITY.equals(tableType)) {
            processor = new EntityAggregationProcessor();
        } else if (_NopMetadataCoreConstants.TABLE_TYPE_EXTERNAL.equals(tableType)) {
            processor = new ExternalAggregationProcessor();
        } else if (_NopMetadataCoreConstants.TABLE_TYPE_SQL.equals(tableType)) {
            processor = new SqlAggregationProcessor();
        } else {
            throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_EXEC_FAILED)
                    .param("metaTableId", table.getMetaTableId())
                    .param("error", "unsupported tableType: " + tableType);
        }
        return AggregationHelper.buildResult(processor.execute(aggrCtx));
    }

    /**
     * JOIN 聚合执行入口（端点组合路由）：加载/校验 join + 解析端点后，按端点组合分派到对应 Processor。
     */
    private List<Map<String, Object>> executeJoinAggregation(AggregationContext aggrCtx, NopMetaTable table,
                                                              List<String> measureNames, List<String> dimensionNames,
                                                              TreeBean filter, String joinId, Long limit, Long offset,
                                                              TreeBean having, List<OrderFieldBean> orderBy,
                                                              MetaQueryContext ctx) {
        NopMetaTableJoin join = joinExecutor.loadValidatedJoin(table, joinId, ctx);

        MetaJoinExecutor.Endpoint leftEp = joinExecutor.resolveEndpoint(join, "left",
                join.getLeftEntityId(), join.getLeftTableId(), ctx);
        MetaJoinExecutor.Endpoint rightEp = joinExecutor.resolveEndpoint(join, "right",
                join.getRightEntityId(), join.getRightTableId(), ctx);

        aggrCtx.setJoin(join);
        aggrCtx.setLeftEndpoint(leftEp);
        aggrCtx.setRightEndpoint(rightEp);

        if (leftEp.isEntity() && rightEp.isEntity()) {
            String leftQs = leftEp.entity.getQuerySpace();
            String rightQs = rightEp.entity.getQuerySpace();
            if (!AggregationHelper.equalsStr(leftQs, rightQs)) {
                return new CrossDbInMemoryAggregationProcessor().execute(aggrCtx);
            }
            return new EntityEntityJoinAggregationProcessor().execute(aggrCtx);
        }
        if (!leftEp.isEntity() && !rightEp.isEntity()) {
            String leftQs = leftEp.table.getQuerySpace();
            String rightQs = rightEp.table.getQuerySpace();
            if (!AggregationHelper.equalsStr(leftQs, rightQs)) {
                return new CrossDbInMemoryAggregationProcessor().execute(aggrCtx);
            }
            return new ExternalExternalJoinAggregationProcessor().execute(aggrCtx);
        }
        return new MixedSameDbJoinAggregationProcessor().execute(aggrCtx);
    }

    // ============================ 多列算术 having 预处理（plan 2026-07-18-1500-2）============================

    private static final Pattern HAVING_EXPR_NAME_TOKEN =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public static void preprocessHavingArithmetic(TreeBean having, Map<String, String> nameToExpr,
                                                   NopMetaTable table, List<String> measureNames,
                                                   List<String> dimensionNames) {
        if (having == null) {
            return;
        }
        Object exprAttr = having.getAttr(HAVING_EXPR_ATTR);
        if (exprAttr != null && !exprAttr.toString().isEmpty()) {
            String userExpr = exprAttr.toString();
            String finalSql = substituteAndValidateHavingExpr(userExpr, nameToExpr, table,
                    measureNames, dimensionNames);
            having.setAttr(FilterBeanConstants.FILTER_ATTR_NAME, finalSql);
            return;
        }
        List<TreeBean> children = having.getChildren();
        if (children == null || children.isEmpty()) {
            return;
        }
        for (TreeBean child : children) {
            preprocessHavingArithmetic(child, nameToExpr, table, measureNames, dimensionNames);
        }
    }

    public static final String HAVING_EXPR_ATTR = "expr";

    static String substituteAndValidateHavingExpr(String userExpr, Map<String, String> nameToExpr,
                                                    NopMetaTable table, List<String> measureNames,
                                                    List<String> dimensionNames) {
        Matcher m = HAVING_EXPR_NAME_TOKEN.matcher(userExpr);
        StringBuilder out = new StringBuilder(userExpr.length() + 32);
        int last = 0;
        while (m.find()) {
            out.append(userExpr, last, m.start());
            String token = m.group();
            String aggSql = nameToExpr.get(token);
            if (aggSql == null) {
                throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_HAVING_UNKNOWN_NAME)
                        .param("metaTableId", table.getMetaTableId())
                        .param("name", token)
                        .param("selectedMeasures", String.valueOf(measureNames))
                        .param("selectedDimensions", String.valueOf(dimensionNames));
            }
            if (aggSql.indexOf('?') >= 0) {
                throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_EXPRESSION_HAVING_ORDER_BY_UNSUPPORTED)
                        .param("metaTableId", table.getMetaTableId())
                        .param("measureName", token)
                        .param("clause", "HAVING");
            }
            out.append(aggSql);
            last = m.end();
        }
        out.append(userExpr, last, userExpr.length());
        String finalSql = out.toString();

        ExpressionMeasureValidator.ValidatedExpression ve;
        try {
            ve = ExpressionMeasureValidator.validateStatic(finalSql,
                    ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(),
                    table.getMetaTableId(), "<having-arithmetic>");
        } catch (NopException e) {
            if (NopMetadataErrors.ERR_AGGR_EXPRESSION_UNPARSEABLE.getErrorCode().equals(e.getErrorCode())) {
                throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_HAVING_EXPR_UNPARSEABLE, e)
                        .param("metaTableId", table.getMetaTableId())
                        .param("expr", userExpr)
                        .param("error", String.valueOf(e.getParam("error")));
            }
            throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_HAVING_EXPR_UNSAFE, e)
                    .param("metaTableId", table.getMetaTableId())
                    .param("expr", userExpr)
                    .param("reason", String.valueOf(e.getParam("reason")));
        }
        if (ve.params != null && !ve.params.isEmpty()) {
            throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_HAVING_EXPR_UNSAFE)
                    .param("metaTableId", table.getMetaTableId())
                    .param("expr", userExpr)
                    .param("reason", "literals are not allowed in having arithmetic expression "
                            + "(Phase 1 only allows measure-name arithmetic combination): "
                            + "params=" + ve.params);
        }
        return finalSql;
    }

    static boolean containsHavingArithmeticLeaf(TreeBean having) {
        if (having == null) {
            return false;
        }
        Object exprAttr = having.getAttr(HAVING_EXPR_ATTR);
        if (exprAttr != null && !exprAttr.toString().isEmpty()) {
            return true;
        }
        List<TreeBean> children = having.getChildren();
        if (children == null || children.isEmpty()) {
            return false;
        }
        for (TreeBean child : children) {
            if (containsHavingArithmeticLeaf(child)) {
                return true;
            }
        }
        return false;
    }
}
