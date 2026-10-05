/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.sql.SQL;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.dataset.impl.BaseDataFieldMeta;
import io.nop.dataset.impl.BaseDataSetMeta;
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.eql.IEqlAstTransformer;
import io.nop.orm.eql.OrmEqlConstants;
import io.nop.orm.eql.SqlOrderByOrdinal;
import io.nop.orm.eql.ast.SqlAggregateFunction;
import io.nop.orm.eql.ast.SqlCompareWithQueryExpr;
import io.nop.orm.eql.ast.SqlExpr;
import io.nop.orm.eql.ast.SqlExprProjection;
import io.nop.orm.eql.ast.SqlInQueryExpr;
import io.nop.orm.eql.ast.SqlOrderByItem;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.ast.SqlProjection;
import io.nop.orm.eql.ast.SqlQuerySelect;
import io.nop.orm.eql.ast.SqlSelect;
import io.nop.orm.eql.ast.SqlSelectWithCte;
import io.nop.orm.eql.ast.SqlStatement;
import io.nop.orm.eql.ast.SqlTransactionStatement;
import io.nop.orm.eql.ast.SqlStatementKind;
import io.nop.orm.eql.ast.SqlSubQueryExpr;
import io.nop.orm.eql.ast.SqlUnionSelect;
import io.nop.orm.eql.meta.ISqlExprMeta;
import io.nop.orm.eql.parse.EqlASTParser;
import io.nop.orm.eql.sql.AstToSqlGenerator;
import io.nop.orm.eql.utils.EqlHelper;
import io.nop.orm.model.IEntityPropModel;
import io.nop.orm.model.IOrmDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static io.nop.orm.eql.OrmEqlErrors.ARG_AST_NODE;
import static io.nop.orm.eql.OrmEqlErrors.ARG_SQL;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_NOT_SUPPORT_MULTIPLE_STATEMENT;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_NOT_SUPPORT_TRANSACTION_STATEMENT;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_UNSUPPORTED_PROJECTION_TYPE;

public class EqlCompiler implements ISqlCompiler {

    @Override
    public ICompiledSql compile(String name, String eql, ISqlCompileContext context) {
        SqlProgram program = new EqlASTParser().parseFromText(null, eql);
        if (program.getStatements().size() > 1)
            throw new NopException(ERR_EQL_NOT_SUPPORT_MULTIPLE_STATEMENT).param(ARG_SQL, eql);

        SqlStatement stm = program.getStatements().get(0);

        // Plan 2307 V4: 事务控制语句显式失败。
        // 修复前 commit/rollback 能一路走到输出阶段，而两个输出器都没有它们的 printer，
        // 于是产出**空字符串** SQL 且不报任何错误——一条静默无操作。
        // 事务边界归属 ITransactionTemplate / 连接层（见 design §3.5），
        // 让 EQL 编译器输出裸 COMMIT 也不携带任何事务语义，故在此处 fail-fast。
        // 判定放在 transform 之前：尽早失败，且不产生任何半成品 SQL。
        if (stm instanceof SqlTransactionStatement) {
            throw new NopException(ERR_EQL_NOT_SUPPORT_TRANSACTION_STATEMENT)
                    .source(stm)
                    .param("statementKind", String.valueOf(stm.getStatementKind()));
        }

        boolean dump = stm.hasDecorator(OrmEqlConstants.DECORATOR_DUMP);
        if (dump) {
            stm.dump("parsed_eql");
        }

        boolean enableFilter = context.isEnableFilter() || stm.hasDecorator(OrmEqlConstants.DECORATOR_ENABLE_FILTER);

        IEqlAstTransformer astTransformer = context.getAstTransformer();

        if (astTransformer != null) {
            astTransformer.transformBeforeAnalyze(program, name, eql, context);
        }

        EqlTransformVisitor transformer = new EqlTransformVisitor(context, name);
        transformer.transform(program);

        if (astTransformer != null) {
            astTransformer.transformAfterAnalyze(program, name, eql, context);
        }

        if (dump) {
            stm.dump("transformed-eql");
        }

        AstToSqlGenerator genSql = new AstToSqlGenerator(transformer.getDialect(), enableFilter);
        genSql.setPretty(true);
        genSql.visit(stm);

        // plan 2320 Phase 2：SQL生成完成后基于已解析AST定位shardProp等值条件，
        // 用7参SyntaxMarker构造器替换TABLE marker（静态信息：参数序号/字面量），
        // 执行期由GenSqlTransformer从paramValues取值并路由
        ShardMarkerInjector.inject(genSql, stm);

        String querySpace = transformer.getQuerySpace();
        SQL sql = genSql.getSql().querySpace(querySpace).name("jdbc:" + name).end();

        CompiledSql compiledSql = new CompiledSql();
        compiledSql.setSql(sql);
        compiledSql.setQuerySpace(transformer.getQuerySpace());
        compiledSql.setStatementKind(stm.getStatementKind());
        compiledSql.setName(name);
        compiledSql.setParamBuilders(transformer.getParams());
        compiledSql.setReadEntityModels(transformer.getReadEntityModels());
        compiledSql.setWriteEntityModel(transformer.getWriteEntityModel());
        compiledSql.setUseTenantModel(transformer.isUseTenantModel());

        if (stm.getStatementKind() == SqlStatementKind.SELECT) {
            SqlSelect select = stm instanceof SqlSelectWithCte ? ((SqlSelectWithCte) stm).getSelect() : (SqlSelect) stm;

            // plan 2321：编译期结构信息填充（设计 §3.1）。AST 在此完整可得，填充是纯静态投影，
            // 不改变任何既有编译产物语义；标志为编译产物静态属性，随 SQL 文本一起进 QueryPlanCache。
            compiledSql.setHasSetOp(select instanceof SqlUnionSelect);
            SqlQuerySelect querySelect = select instanceof SqlUnionSelect
                    ? ((SqlUnionSelect) select).getFirstSelect()
                    : (select instanceof SqlQuerySelect ? (SqlQuerySelect) select : null);
            if (querySelect != null) {
                compiledSql.setHasGroupBy(querySelect.getGroupBy() != null);
                compiledSql.setHasHaving(querySelect.getHaving() != null);
                compiledSql.setHasDistinct(querySelect.getDistinct());
                compiledSql.setHasForUpdate(querySelect.getForUpdate());
            }

            List<SqlProjection> projections = select.getProjections();
            List<ISqlExprMeta> fieldMetas = new ArrayList<>(projections.size());
            List<BaseDataFieldMeta> fields = new ArrayList<>(projections.size());
            for (SqlProjection proj : projections) {
                // Plan 2307 V6: 顶层 SELECT 的投影此前是无条件强转。实测常规路径
                // （select * / owner.* / union）都会先被展开为 SqlExprProjection，
                // 故该分支当前不可达；但作为公开编译入口，无条件强转一旦遇到未展开的
                // 投影就会抛 ClassCastException（无错误码）。改为带错误码的显式失败。
                if (!(proj instanceof SqlExprProjection)) {
                    throw new NopException(ERR_EQL_UNSUPPORTED_PROJECTION_TYPE)
                            .source(proj)
                            .param(ARG_AST_NODE, String.valueOf(proj.getASTKind()));
                }
                SqlExprProjection exprProj = (SqlExprProjection) proj;
                String fieldName = EqlHelper.getFieldName(exprProj);

                ISqlExprMeta exprMeta = exprProj.getExpr() != null
                        ? exprProj.getExpr().getResolvedExprMeta() : null;
                if (exprMeta == null) {
                    // Guard.notNull 抛的是无错误码的 IllegalStateException/CCE，
                    // 这里换成带错误码的显式失败，便于上层定位到具体投影。
                    throw new NopException(ERR_EQL_UNSUPPORTED_PROJECTION_TYPE)
                            .source(exprProj)
                            .param(ARG_AST_NODE, String.valueOf(exprProj.getASTKind()));
                }
                fieldMetas.add(exprMeta);

                BaseDataFieldMeta field = getBaseDataFieldMeta(exprMeta, fieldName);
                fields.add(field);
            }
            BaseDataSetMeta meta = new BaseDataSetMeta(fields);
            compiledSql.setDataSetMeta(meta);
            compiledSql.setColumnBinders(collectColumnBinders(fieldMetas));
            compiledSql.setFieldMetas(fieldMetas);

            // plan 2321：裸 count 判定与 orderBy→投影序数映射（依赖上方投影循环建立的序数对齐）
            compiledSql.setBareCount(isBareCountProjection(projections, compiledSql.hasGroupBy(),
                    compiledSql.hasHaving()));
            compiledSql.setHasAggregate(hasAggregateProjection(projections));
            compiledSql.setOrderByOrdinals(buildOrderByOrdinals(querySelect, projections));
        }
        return compiledSql;
    }

    /**
     * 投影是否包含聚合函数（plan 2321 实施期补充标志）：顶层投影表达式树深度优先检测
     * SqlAggregateFunction；不进入子查询表达式（子查询聚合在每分片内独立求值，不属顶层
     * 聚合投影）。窗口表达式内聚合一并视为聚合投影（跨分片拼接不可归并）。
     */
    static boolean hasAggregateProjection(List<SqlProjection> projections) {
        if (projections == null)
            return false;
        for (SqlProjection proj : projections) {
            if (!(proj instanceof SqlExprProjection))
                continue;
            if (containsAggregate(((SqlExprProjection) proj).getExpr()))
                return true;
        }
        return false;
    }

    private static boolean containsAggregate(SqlExpr expr) {
        if (expr == null)
            return false;
        if (expr instanceof SqlAggregateFunction)
            return true;
        // 子查询内的聚合独立于顶层投影求值，停止下探
        if (expr instanceof SqlSubQueryExpr || expr instanceof SqlCompareWithQueryExpr
                || expr instanceof SqlInQueryExpr)
            return false;
        boolean[] found = new boolean[1];
        expr.forEachChild(child -> {
            if (!found[0] && child instanceof SqlExpr && containsAggregate((SqlExpr) child))
                found[0] = true;
        });
        return found[0];
    }

    /**
     * 裸 count 判定（plan 2321，设计 §3.4）：单投影 count(*)/count(expr) 且无 GROUP BY/HAVING。
     * count(distinct x) 不算裸 count（各分片求和不等于全局去重数）。
     */
    static boolean isBareCountProjection(List<SqlProjection> projections, boolean hasGroupBy, boolean hasHaving) {
        if (hasGroupBy || hasHaving || projections == null || projections.size() != 1)
            return false;
        if (!(projections.get(0) instanceof SqlExprProjection))
            return false;
        SqlExpr expr = ((SqlExprProjection) projections.get(0)).getExpr();
        if (!(expr instanceof SqlAggregateFunction))
            return false;
        SqlAggregateFunction agg = (SqlAggregateFunction) expr;
        return "count".equalsIgnoreCase(agg.getName()) && !agg.getDistinct();
    }

    /**
     * orderBy→投影序数映射（plan 2321，设计 §3.1）：每个顶层排序项对应一个条目；
     * 排序键未投影（含别名不可解析形态）的条目序数为 {@link SqlOrderByOrdinal#ORDINAL_NOT_PROJECTED}。
     * 序数与 fieldMetas 下标对齐（同一投影循环建立）。别名引用经 resolveSelectExpr 解析后
     * 与投影表达式共享同一 resolvedExprMeta 实例，identity 匹配成立。
     */
    static List<SqlOrderByOrdinal> buildOrderByOrdinals(SqlQuerySelect querySelect, List<SqlProjection> projections) {
        if (querySelect == null || querySelect.getOrderBy() == null)
            return Collections.emptyList();
        List<SqlOrderByItem> items = querySelect.getOrderBy().getItems();
        List<SqlOrderByOrdinal> ret = new ArrayList<>(items.size());
        for (SqlOrderByItem item : items) {
            ret.add(new SqlOrderByOrdinal(resolveProjectionOrdinal(item, projections), item.getAsc(),
                    item.getNullsFirst()));
        }
        return ret;
    }

    private static int resolveProjectionOrdinal(SqlOrderByItem item, List<SqlProjection> projections) {
        if (item.getExpr() == null)
            return SqlOrderByOrdinal.ORDINAL_NOT_PROJECTED;
        ISqlExprMeta orderMeta = item.getExpr().getResolvedExprMeta();
        if (orderMeta == null)
            return SqlOrderByOrdinal.ORDINAL_NOT_PROJECTED;
        for (int i = 0, n = projections.size(); i < n; i++) {
            SqlProjection proj = projections.get(i);
            if (proj instanceof SqlExprProjection) {
                SqlExpr projExpr = ((SqlExprProjection) proj).getExpr();
                if (projExpr != null && projExpr.getResolvedExprMeta() == orderMeta)
                    return i;
            }
        }
        return SqlOrderByOrdinal.ORDINAL_NOT_PROJECTED;
    }

    private static BaseDataFieldMeta getBaseDataFieldMeta(ISqlExprMeta exprMeta, String fieldName) {
        IOrmDataType dataType = exprMeta.getOrmDataType();
        String sourceFieldName = null;
        String ownerEntityName = null;
        if (dataType instanceof IEntityPropModel) {
            IEntityPropModel propModel = (IEntityPropModel) dataType;
            sourceFieldName = propModel.getName();
            ownerEntityName = propModel.getOwnerEntityModel().getName();
        }
        BaseDataFieldMeta field = new BaseDataFieldMeta(fieldName, sourceFieldName, ownerEntityName,
                exprMeta.getStdDataType(), exprMeta.getStdSqlType(), false);
        return field;
    }

    List<IDataParameterBinder> collectColumnBinders(List<ISqlExprMeta> exprs) {
        return exprs.stream().flatMap(expr -> expr.getColumnBinders().stream()).collect(Collectors.toList());
    }
}
