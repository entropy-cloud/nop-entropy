/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.commons.text.marker.Marker;
import io.nop.core.lang.sql.SQL;
import io.nop.core.lang.sql.SyntaxMarker;
import io.nop.orm.eql.ast.SqlAndExpr;
import io.nop.orm.eql.ast.SqlBinaryExpr;
import io.nop.orm.eql.ast.SqlDelete;
import io.nop.orm.eql.ast.SqlExpr;
import io.nop.orm.eql.ast.SqlFrom;
import io.nop.orm.eql.ast.SqlJoinTableSource;
import io.nop.orm.eql.ast.SqlColumnName;
import io.nop.orm.eql.ast.SqlNumberLiteral;
import io.nop.orm.eql.ast.SqlParameterMarker;
import io.nop.orm.eql.ast.SqlQuerySelect;
import io.nop.orm.eql.ast.SqlSelectWithCte;
import io.nop.orm.eql.ast.SqlSingleTableSource;
import io.nop.orm.eql.ast.SqlStatement;
import io.nop.orm.eql.ast.SqlStringLiteral;
import io.nop.orm.eql.ast.SqlTableSource;
import io.nop.orm.eql.ast.SqlUpdate;
import io.nop.orm.eql.enums.SqlOperator;
import io.nop.orm.eql.sql.AstToSqlGenerator;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityPropModel;
import io.nop.orm.model.IOrmDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * plan 2320 Phase 2：查询侧分片值写入。
 *
 * <p><b>注入时机（审查核定的执行约束1）</b>：输出器按select→from→where单遍输出，FROM期
 * markTable时WHERE参数marker尚未产生，「构造期传入」不可行。因此本类在<b>SQL生成完成后</b>
 * 基于已解析AST定位shardProp等值条件，用SyntaxMarker现有7参构造器<b>新建marker替换</b>
 * builder marker列表中的TABLE marker（final字段不改，nop-core零改动）。</p>
 *
 * <p><b>缓存安全（约束2）</b>：CompiledSql经QueryPlanCache跨执行共享，marker只承载静态信息——
 * shardParamIndex为执行期paramValues的<b>序号</b>（该参数节点输出的?在全部ValueMarker中的序数，
 * 与{@code CompiledSql.buildParams}产物的下标一一对应），shardValue仅承载编译期字面量。
 * 执行期值由消费方GenSqlTransformer从paramValues按下标取。</p>
 *
 * <p>条件定位范围（v1边界）：语句顶层WHERE与显式JOIN的ON条件中，AND连通的等值条件；
 * OR/NOT分支与子查询内部不下探（无法推出单分片值，交由运行期missing-shard-policy裁决）。
 * 同表重复绑定到不同值源时按歧义处理（撤销绑定，同样交由运行期裁决）。</p>
 */
public class ShardMarkerInjector {

    private ShardMarkerInjector() {
    }

    /**
     * 单个useShard表别名上的分片等值绑定。paramNode与literalValue二选一。
     */
    static class ShardEq {
        final String entityName;
        final SqlParameterMarker paramNode;
        final Object literalValue;

        ShardEq(String entityName, SqlParameterMarker paramNode, Object literalValue) {
            this.entityName = entityName;
            this.paramNode = paramNode;
            this.literalValue = literalValue;
        }

        boolean isSameSource(ShardEq other) {
            if (paramNode != null)
                return other.paramNode == paramNode;
            return other.paramNode == null
                    && String.valueOf(literalValue).equals(String.valueOf(other.literalValue));
        }
    }

    public static void inject(AstToSqlGenerator gen, SqlStatement stm) {
        SqlStatement target = stm instanceof SqlSelectWithCte ? ((SqlSelectWithCte) stm).getSelect() : stm;

        Map<String, ShardEq> bindings = new HashMap<>();
        List<String[]> propagationEdges = new ArrayList<>();

        collectFromStatement(target, bindings, propagationEdges);
        resolvePropagation(bindings, propagationEdges);

        if (bindings.isEmpty())
            return;

        replaceTableMarkers(gen, bindings);
    }

    // ---------------- 条件收集 ----------------

    private static void collectFromStatement(SqlStatement stm, Map<String, ShardEq> bindings,
                                             List<String[]> propagationEdges) {
        if (stm instanceof SqlQuerySelect) {
            // union等复合select不下探（无法推出语句级单分片值，交由运行期missing-shard-policy裁决）
            SqlQuerySelect select = (SqlQuerySelect) stm;
            collectJoinConditions(select.getFrom(), propagationEdges);
            if (select.getWhere() != null)
                collectExpr(select.getWhere().getExpr(), bindings, propagationEdges);
        } else if (stm instanceof SqlUpdate) {
            SqlUpdate update = (SqlUpdate) stm;
            if (update.getWhere() != null)
                collectDmlExpr(update, update.getWhere().getExpr(), bindings, propagationEdges);
        } else if (stm instanceof SqlDelete) {
            SqlDelete delete = (SqlDelete) stm;
            if (delete.getWhere() != null)
                collectDmlExpr(delete, delete.getWhere().getExpr(), bindings, propagationEdges);
        }
    }

    /**
     * DML目标表无输出别名（TABLE marker alias为null），其分片绑定额外注册到空别名键上。
     */
    private static void collectDmlExpr(SqlStatement stm, SqlExpr expr, Map<String, ShardEq> bindings,
                                       List<String[]> propagationEdges) {
        collectExpr(expr, bindings, propagationEdges);

        SqlTableSource targetSource = stm instanceof ISqlTableSourceSupport
                ? ((ISqlTableSourceSupport) stm).getResolvedTableSource() : null;
        if (targetSource == null)
            return;

        // 把目标表别名上的绑定复制到空别名键（DML TABLE marker的alias为null）
        String targetAlias = targetSource instanceof SqlSingleTableSource
                ? ((SqlSingleTableSource) targetSource).getAliasName() : null;
        ShardEq eq = targetAlias == null ? null : bindings.get(targetAlias);
        if (eq != null && !bindings.containsKey(""))
            bindings.put("", eq);
    }

    private static void collectJoinConditions(SqlFrom from, List<String[]> propagationEdges) {
        if (from == null)
            return;
        for (SqlTableSource source : from.getTableSources()) {
            collectJoinConditions(source, propagationEdges);
        }
    }

    private static void collectJoinConditions(SqlTableSource source, List<String[]> propagationEdges) {
        if (source instanceof SqlJoinTableSource) {
            SqlJoinTableSource join = (SqlJoinTableSource) source;
            // ON条件只收两分片列间的等值（用于绑定表语义的同值传播）
            collectJoinExpr(join.getCondition(), propagationEdges);
            collectJoinConditions(join.getLeft(), propagationEdges);
            collectJoinConditions(join.getRight(), propagationEdges);
        }
    }

    /**
     * ON条件只考虑 col = col 形态（ON中引入外部参数不是合法的连接语义）。
     */
    private static void collectJoinExpr(SqlExpr expr, List<String[]> propagationEdges) {
        if (!(expr instanceof SqlAndExpr)) {
            if (expr instanceof SqlBinaryExpr) {
                SqlBinaryExpr binary = (SqlBinaryExpr) expr;
                if (binary.getOperator() == SqlOperator.EQ) {
                    ColumnSide leftCol = shardColumn(binary.getLeft());
                    ColumnSide rightCol = shardColumn(binary.getRight());
                    if (leftCol != null && rightCol != null
                            && !leftCol.aliasKey.equals(rightCol.aliasKey))
                        propagationEdges.add(new String[]{leftCol.aliasKey, leftCol.entityName,
                                rightCol.aliasKey, rightCol.entityName});
                }
            }
            return;
        }
        SqlAndExpr and = (SqlAndExpr) expr;
        collectJoinExpr(and.getLeft(), propagationEdges);
        collectJoinExpr(and.getRight(), propagationEdges);
    }

    /**
     * 只沿AND分支下探。OR/NOT/子查询分支不下探：无法从其中单个等值条件推出
     * 语句级单分片值，缺失绑定由运行期missing-shard-policy裁决。
     */
    private static void collectExpr(SqlExpr expr, Map<String, ShardEq> bindings,
                                    List<String[]> propagationEdges) {
        if (expr instanceof SqlAndExpr) {
            SqlAndExpr and = (SqlAndExpr) expr;
            collectExpr(and.getLeft(), bindings, propagationEdges);
            collectExpr(and.getRight(), bindings, propagationEdges);
            return;
        }
        if (!(expr instanceof SqlBinaryExpr))
            return;

        SqlBinaryExpr binary = (SqlBinaryExpr) expr;
        if (binary.getOperator() != SqlOperator.EQ)
            return;

        SqlExpr left = binary.getLeft();
        SqlExpr right = binary.getRight();

        ColumnSide leftCol = shardColumn(left);
        ColumnSide rightCol = shardColumn(right);

        if (leftCol != null && rightCol != null) {
            // 两分片列等值：绑定表语义的同值传播边
            if (!leftCol.aliasKey.equals(rightCol.aliasKey))
                propagationEdges.add(new String[]{leftCol.aliasKey, leftCol.entityName,
                        rightCol.aliasKey, rightCol.entityName});
            return;
        }

        if (leftCol != null) {
            Object literal = literalValue(right);
            SqlParameterMarker param = paramMarker(right);
            bind(leftCol, literal, param, bindings);
        } else if (rightCol != null) {
            Object literal = literalValue(left);
            SqlParameterMarker param = paramMarker(left);
            bind(rightCol, literal, param, bindings);
        }
    }

    private static void bind(ColumnSide col, Object literal, SqlParameterMarker param,
                             Map<String, ShardEq> bindings) {
        if (literal == null && param == null)
            return; // 非参数也非简单字面量：不绑定，交由运行期裁决
        if (param != null && param.isMasked())
            return; // masked参数不参与路由值提取

        ShardEq eq = new ShardEq(col.entityName, param, literal);
        ShardEq prev = bindings.get(col.aliasKey);
        if (prev == null) {
            bindings.put(col.aliasKey, eq);
        } else if (!prev.isSameSource(eq)) {
            // 同表绑定到不同值源：歧义，撤销绑定（运行期missing-shard-policy裁决）
            bindings.remove(col.aliasKey);
        }
    }

    private static void resolvePropagation(Map<String, ShardEq> bindings, List<String[]> edges) {
        // 同值传播：一侧有绑定则传播到另一侧（值源相同、实体名取目标侧）；小规模边集迭代至不动点
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String[] edge : edges) {
                String aliasA = edge[0], entityA = edge[1], aliasB = edge[2], entityB = edge[3];
                ShardEq a = bindings.get(aliasA);
                ShardEq b = bindings.get(aliasB);
                if (a != null && b == null) {
                    bindings.put(aliasB, new ShardEq(entityB, a.paramNode, a.literalValue));
                    changed = true;
                } else if (b != null && a == null) {
                    bindings.put(aliasA, new ShardEq(entityA, b.paramNode, b.literalValue));
                    changed = true;
                }
            }
        }
    }

    // ---------------- 判定辅助 ----------------

    static class ColumnSide {
        final String entityName;
        final String aliasKey;

        ColumnSide(String entityName, String aliasKey) {
            this.entityName = entityName;
            this.aliasKey = aliasKey;
        }
    }

    /**
     * 判断表达式是否为某useShard实体的shardProp列。
     */
    static ColumnSide shardColumn(SqlExpr expr) {
        if (!(expr instanceof SqlColumnName))
            return null;
        SqlColumnName col = (SqlColumnName) expr;
        if (col.getResolvedExprMeta() == null)
            return null;
        io.nop.orm.model.IOrmDataType dataType = col.getResolvedExprMeta().getOrmDataType();
        if (!(dataType instanceof IEntityPropModel))
            return null;
        IEntityPropModel propModel = (IEntityPropModel) dataType;
        if (!propModel.isColumnModel())
            return null;
        IEntityModel entityModel = propModel.getOwnerEntityModel();
        if (entityModel == null || !entityModel.isUseShard() || entityModel.getShardColumn() == null)
            return null;
        if (!entityModel.getShardColumn().getName().equals(propModel.getName()))
            return null;

        String alias = col.getTableSource() == null ? null : col.getTableSource().getAliasName();
        return new ColumnSide(entityModel.getName(), alias == null ? "" : alias);
    }

    private static String shardColAlias(SqlExpr expr) {
        ColumnSide col = shardColumn(expr);
        return col == null ? null : col.aliasKey;
    }

    static SqlParameterMarker paramMarker(SqlExpr expr) {
        return expr instanceof SqlParameterMarker ? (SqlParameterMarker) expr : null;
    }

    /**
     * v1仅提取字符串/数值/布尔字面量（数值以原始文本传递，由算法负责类型转换）。
     */
    static Object literalValue(SqlExpr expr) {
        if (expr instanceof SqlStringLiteral)
            return ((SqlStringLiteral) expr).getValue();
        if (expr instanceof SqlNumberLiteral)
            return ((SqlNumberLiteral) expr).getValue();
        return null;
    }

    // ---------------- marker替换 ----------------

    /**
     * 用携带shard静态信息的新SyntaxMarker替换builder marker列表中的TABLE marker。
     * 只改marker列表，不改SQL文本（表名文本在执行期由GenSqlTransformer按shardName改写）。
     */
    static void replaceTableMarkers(AstToSqlGenerator gen, Map<String, ShardEq> bindings) {
        SQL.SqlBuilder sb = gen.getSql();
        List<Marker> markers = sb.getMarkers();
        if (markers == null)
            return;

        for (int i = 0, n = markers.size(); i < n; i++) {
            Marker marker = markers.get(i);
            if (!(marker instanceof SyntaxMarker))
                continue;
            SyntaxMarker sm = (SyntaxMarker) marker;
            if (sm.getType() != SyntaxMarker.SyntaxMarkerType.TABLE || sm.getEntityName() == null)
                continue;

            String aliasKey = sm.getAlias() == null ? "" : sm.getAlias();
            ShardEq eq = bindings.get(aliasKey);
            if (eq == null || !eq.entityName.equals(sm.getEntityName()))
                continue;

            SyntaxMarker replacement;
            if (eq.paramNode != null) {
                replacement = new SyntaxMarker(sm.getBegin(), sm.getEnd(), SyntaxMarker.SyntaxMarkerType.TABLE,
                        sm.getEntityName(), sm.getAlias(), gen.getParamValueOrdinal(eq.paramNode), null);
            } else {
                replacement = new SyntaxMarker(sm.getBegin(), sm.getEnd(), SyntaxMarker.SyntaxMarkerType.TABLE,
                        sm.getEntityName(), sm.getAlias(), -1, eq.literalValue);
            }
            markers.set(i, replacement);
        }
    }

    /**
     * 供测试观察绑定解析结果。
     */
    public static Map<String, ShardEq> resolveBindings(SqlStatement stm) {
        SqlStatement target = stm instanceof SqlSelectWithCte ? ((SqlSelectWithCte) stm).getSelect() : stm;
        Map<String, ShardEq> bindings = new HashMap<>();
        List<String[]> edges = new ArrayList<>();
        collectFromStatement(target, bindings, edges);
        resolvePropagation(bindings, edges);
        return bindings;
    }
}
