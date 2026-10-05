/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical_entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.api.core.exceptions.NopException;
import io.nop.dao.dialect.IDialect;
import io.nop.orm.eql.IEqlAstTransformer;
import io.nop.orm.eql.ast.EqlASTNode;
import io.nop.orm.eql.ast.EqlASTOptimizer;
import io.nop.orm.eql.ast.SqlAndExpr;
import io.nop.orm.eql.ast.SqlBinaryExpr;
import io.nop.orm.eql.ast.SqlExpr;
import io.nop.orm.eql.ast.SqlIsNullExpr;
import io.nop.orm.eql.ast.SqlNotExpr;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.ast.SqlSingleTableSource;
import io.nop.orm.eql.ast.SqlStatement;
import io.nop.orm.eql.enums.SqlOperator;
import io.nop.orm.eql.meta.ISqlTableMeta;
import io.nop.orm.eql.parse.EqlASTBuildVisitor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static io.nop.orm.eql.OrmEqlErrors.ARG_ENTITY_NAME;
import static io.nop.orm.eql.OrmEqlErrors.ARG_EXPR;
import static io.nop.orm.eql.OrmEqlErrors.ARG_QUERY_SPACE_MAP;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_NOT_ALLOW_MULTIPLE_QUERY_SPACE;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_NULL_PREDICATE_OPERAND_HAS_PARAM_MARKER;

/**
 * WI8 Phase 3（plan 27；裁定 ai-dev/design/eql-cross-db-compat/2026-10-04-wi8-phase0-adjudications.md
 * §四.2）：WI4 AST 改写管线的**第一个真实生产消费者**——Oracle 族方言上
 * {@code IS [NOT] DISTINCT FROM} 的三节点展开。
 *
 * <p>职责边界（裁定钉死）：仅做 SAFE_EQ 谓词的方言展开；{@code <=>}（mysql 族）与
 * {@code IS [NOT] DISTINCT FROM} 文本（native 集）渲染归 {@code AstToSqlGenerator.printSafeEq}
 * 三分支；{@code IS [NOT] TRUE/FALSE} 已由构建期真值表展开全覆盖，本类不处理。</p>
 *
 * <p>槽位与方言来源（WI4 §二 2c 契约）：{@code transformAfterAnalyze}——展开只重排/包装既有
 * 操作数节点（参数保持型纯语法改写），方言经已解析表源 → querySpace →
 * {@code context.getDialectForQuerySpace(querySpace)} 确定（与渲染侧同源；EQL 管线已强制
 * 单 querySpace，多 querySpace 为防御性响亮）。</p>
 *
 * <p>展开公式（裁定 §二.1 钉死，3VL 逐行验算=验收不变式：单 NULL 与双 NULL 行均不得产生
 * UNKNOWN）：{@code a IS NOT DISTINCT FROM b ≡ (a IS NULL AND b IS NULL) OR (a IS NOT NULL
 * AND b IS NOT NULL AND a=b)}（两支式）；{@code a IS DISTINCT FROM b ≡ (a IS NOT NULL AND
 * b IS NOT NULL AND a<>b) OR (a IS NULL AND b IS NOT NULL) OR (a IS NOT NULL AND b IS NULL)}
 * （三支式）。禁止 naive NOT 包装（单/双 NULL 行 NOT(NULL)=UNKNOWN → WHERE 丢行）。</p>
 *
 * <p>操作数复制与参数标记：guard 式把操作数复制为 2~3 份（每支一份，deepClone 独立实例——AST
 * 框架禁止双亲）。操作数含参数标记（?）时复制改变参数契约 ⇒ 响亮拒绝
 * {@link ERR_EQL_NULL_PREDICATE_OPERAND_HAS_PARAM_MARKER}（与构建期 IS TRUE/FALSE 展开同一
 * 错误码）。</p>
 *
 * <p>配置方式：经 {@code SessionFactoryConfig.eqlAstTransformer} 默认注入（beans.xml 显式
 * {@code <bean>}），禁止 per-call 传入（WI4 契约：非默认实例绕过计划缓存）。</p>
 */
public class OracleEqlAstTransformer implements IEqlAstTransformer {

    /**
     * oracle 族名称集合（裁定 §四.2；oracle-reverse 继承 oracle 的展开需求）。
     * db2/dm/tdengine 不在集合内——其 SAFE_EQ 由渲染三分支响亮报错（unverified 纪律）。
     */
    public static final Set<String> ORACLE_FAMILY = Set.of("oracle", "oracle-reverse");

    @Override
    public void transformBeforeAnalyze(SqlProgram ast, String name, String sql, ISqlCompileContext context) {
        // 本 transformer 的改写需要方言判定，方言只能经已解析表源推导（WI4 §二 2c：
        // before 槽位拿不到可信 querySpace）⇒ before 槽位按契约不做任何改写；
        // 全部展开在 transformAfterAnalyze。非空实现由该契约注释承载，非静默跳过——
        // after 槽位无法处理的改写类型不在本类职责内（职责边界见类 javadoc）。
    }

    @Override
    public void transformAfterAnalyze(SqlProgram ast, String name, String sql, ISqlCompileContext context) {
        String dialectName = resolveDialectName(ast, context);
        if (dialectName == null || !ORACLE_FAMILY.contains(dialectName))
            return; // 非 oracle 族直通不改写（AST 原样；SAFE_EQ 由渲染三分支按方言处理）

        // inplaceUpdate=true：编译期 AST 为本次编译私有（EqlTransformVisitor 同为原地改写），
        // 原地替换；默认 clone 模式下优化结果经返回值传递，丢弃返回值 = 静默丢弃改写（实测踩坑）
        Expander expander = new Expander();
        expander.setInplaceUpdate(true);
        for (SqlStatement stm : ast.getStatements()) {
            expander.optimize(stm, null);
        }
    }

    /**
     * 方言来源（与渲染侧同源）：已解析表源 → querySpace → context.getDialectForQuerySpace。
     * 多 querySpace 在 analyze 阶段已被管线拒绝（ERR_EQL_NOT_ALLOW_MULTIPLE_QUERY_SPACE），
     * 此处为防御性重复校验（响亮，不静默取主表方言）；无已解析表源（无 FROM 语句）不可能含
     * SAFE_EQ 实体谓词，返回 null 直通。
     */
    String resolveDialectName(SqlProgram ast, ISqlCompileContext context) {
        Map<String, String> querySpaceToEntity = new LinkedHashMap<>();
        collectQuerySpaces(ast, querySpaceToEntity);
        if (querySpaceToEntity.isEmpty())
            return null;
        if (querySpaceToEntity.size() > 1)
            throw new NopException(ERR_EQL_NOT_ALLOW_MULTIPLE_QUERY_SPACE)
                    .param(ARG_ENTITY_NAME, querySpaceToEntity.values().iterator().next())
                    .param(ARG_QUERY_SPACE_MAP, querySpaceToEntity);
        String querySpace = querySpaceToEntity.keySet().iterator().next();
        IDialect dialect = context.getDialectForQuerySpace(querySpace);
        return dialect == null ? null : dialect.getName();
    }

    private void collectQuerySpaces(EqlASTNode node, Map<String, String> querySpaceToEntity) {
        if (node instanceof SqlSingleTableSource) {
            Object meta = ((SqlSingleTableSource) node).getResolvedTableMeta();
            if (meta instanceof ISqlTableMeta) {
                String qs = ((ISqlTableMeta) meta).getQuerySpace();
                if (qs != null && !querySpaceToEntity.containsKey(qs))
                    querySpaceToEntity.put(qs, ((ISqlTableMeta) meta).getEntityName());
            }
        }
        node.forEachChild(child -> collectQuerySpaces(child, querySpaceToEntity));
    }

    static void rejectParamMarkerOperand(SqlExpr operand) {
        if (EqlASTBuildVisitor.containsParameterMarker(operand)) {
            String echo = operand.toSqlString().replaceAll("\\s+", " ").trim();
            throw new NopException(ERR_EQL_NULL_PREDICATE_OPERAND_HAS_PARAM_MARKER).param(ARG_EXPR, echo);
        }
    }
    /**
     * SAFE_EQ 谓词展开器：复用生成 {@link EqlASTOptimizer} 的子树递归与重挂载机制
     * （optimizeXxx 默认实现递归子节点并经 setter 重挂——ASTParent 由 setter 维护），
     * 只覆写两个目标节点类型。
     */
    class Expander extends EqlASTOptimizer<Void> {

        @Override
        public EqlASTNode optimizeSqlNotExpr(SqlNotExpr node, Void context) {
            // IS DISTINCT FROM 方向（SqlNotExpr(SAFE_EQ) 规范形态）→ 三支式**替换**整节点
            //（禁止 NOT 包装——双 NULL 行 NOT(NULL)=UNKNOWN 丢行，裁定禁止形态②）
            if (node.getExpr() instanceof SqlBinaryExpr
                    && ((SqlBinaryExpr) node.getExpr()).getOperator() == SqlOperator.SAFE_EQ) {
                return expandDistinctFrom((SqlBinaryExpr) node.getExpr(), context);
            }
            return super.optimizeSqlNotExpr(node, context);
        }

        @Override
        public EqlASTNode optimizeSqlBinaryExpr(SqlBinaryExpr node, Void context) {
            // IS NOT DISTINCT FROM 方向 → 两支式替换整节点
            if (node.getOperator() == SqlOperator.SAFE_EQ) {
                return expandIsNotDistinctFrom(node, context);
            }
            return super.optimizeSqlBinaryExpr(node, context);
        }

        /**
         * {@code a IS NOT DISTINCT FROM b} ⇒ 两支式：
         * {@code (a IS NULL AND b IS NULL) OR (a IS NOT NULL AND b IS NOT NULL AND a=b)}
         * （全部行二值：双 NULL=TRUE、单 NULL=FALSE、非空按等值）。
         */
        EqlASTNode expandIsNotDistinctFrom(SqlBinaryExpr node, Void context) {
            // 操作数先经 optimize 递归（嵌套 SAFE_EQ 谓词先展开），再按 guard 支复制（deepClone 独立实例）
            SqlExpr a = (SqlExpr) optimize(node.getLeft(), context);
            SqlExpr b = (SqlExpr) optimize(node.getRight(), context);
            rejectParamMarkerOperand(a);
            rejectParamMarkerOperand(b);

            // 支 1：a IS NULL AND b IS NULL
            SqlExpr branch1 = and(isNull(cloneOf(a), false), isNull(cloneOf(b), false));
            // 支 2：a IS NOT NULL AND (b IS NOT NULL AND a=b)
            SqlExpr branch2 = and(isNull(cloneOf(a), true),
                    and(isNull(cloneOf(b), true), eq(cloneOf(a), cloneOf(b))));

            return or(branch1, branch2);
        }

        /**
         * {@code a IS DISTINCT FROM b} ⇒ 三支式：
         * {@code (a IS NOT NULL AND b IS NOT NULL AND a<>b) OR (a IS NULL AND b IS NOT NULL)
         * OR (a IS NOT NULL AND b IS NULL)}
         * （全部行二值：双 NULL=FALSE、单 NULL=TRUE、非空按不等值）。
         */
        EqlASTNode expandDistinctFrom(SqlBinaryExpr inner, Void context) {
            SqlExpr a = (SqlExpr) optimize(inner.getLeft(), context);
            SqlExpr b = (SqlExpr) optimize(inner.getRight(), context);
            rejectParamMarkerOperand(a);
            rejectParamMarkerOperand(b);

            // 支 1：a IS NOT NULL AND (b IS NOT NULL AND a<>b)
            SqlExpr branch1 = and(isNull(cloneOf(a), true),
                    and(isNull(cloneOf(b), true), ne(cloneOf(a), cloneOf(b))));
            // 支 2：a IS NULL AND b IS NOT NULL
            SqlExpr branch2 = and(isNull(cloneOf(a), false), isNull(cloneOf(b), true));
            // 支 3：a IS NOT NULL AND b IS NULL
            SqlExpr branch3 = and(isNull(cloneOf(a), true), isNull(cloneOf(b), false));

            return or(branch1, or(branch2, branch3));
        }
    }


    static SqlExpr cloneOf(SqlExpr expr) {
        return (SqlExpr) expr.deepClone();
    }

    static SqlIsNullExpr isNull(SqlExpr operand, boolean not) {
        SqlIsNullExpr ret = new SqlIsNullExpr();
        ret.setLocation(operand.getLocation());
        ret.setExpr(operand);
        ret.setNot(not);
        ret.normalize();
        ret.validate();
        return ret;
    }

    static SqlBinaryExpr binary(SqlExpr left, SqlOperator op, SqlExpr right) {
        SqlBinaryExpr ret = new SqlBinaryExpr();
        ret.setLocation(left.getLocation());
        ret.setLeft(left);
        ret.setOperator(op);
        ret.setRight(right);
        ret.normalize();
        ret.validate();
        return ret;
    }

    static SqlExpr eq(SqlExpr left, SqlExpr right) {
        return binary(left, SqlOperator.EQ, right);
    }

    static SqlExpr ne(SqlExpr left, SqlExpr right) {
        return binary(left, SqlOperator.NE, right);
    }

    static SqlExpr and(SqlExpr left, SqlExpr right) {
        SqlAndExpr ret = new SqlAndExpr();
        ret.setLocation(left.getLocation());
        ret.setLeft(left);
        ret.setRight(right);
        ret.normalize();
        ret.validate();
        return ret;
    }

    static SqlExpr or(SqlExpr left, SqlExpr right) {
        io.nop.orm.eql.ast.SqlOrExpr ret = new io.nop.orm.eql.ast.SqlOrExpr();
        ret.setLocation(left.getLocation());
        ret.setLeft(left);
        ret.setRight(right);
        ret.normalize();
        ret.validate();
        return ret;
    }
}
