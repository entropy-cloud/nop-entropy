/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.enc;

import io.nop.api.core.exceptions.NopException;
import io.nop.orm.OrmErrors;
import io.nop.orm.eql.IEqlAstTransformer;
import io.nop.orm.eql.ast.EqlASTNode;
import io.nop.orm.eql.ast.SqlAlias;
import io.nop.orm.eql.ast.SqlBinaryExpr;
import io.nop.orm.eql.ast.SqlColumnName;
import io.nop.orm.eql.ast.SqlCteStatement;
import io.nop.orm.eql.ast.SqlDelete;
import io.nop.orm.eql.ast.SqlExistsExpr;
import io.nop.orm.eql.ast.SqlExpr;
import io.nop.orm.eql.ast.SqlFrom;
import io.nop.orm.eql.ast.SqlInQueryExpr;
import io.nop.orm.eql.ast.SqlInValuesExpr;
import io.nop.orm.eql.ast.SqlJoinTableSource;
import io.nop.orm.eql.ast.SqlLikeExpr;
import io.nop.orm.eql.ast.SqlNullLiteral;
import io.nop.orm.eql.ast.SqlNumberLiteral;
import io.nop.orm.eql.ast.SqlParameterMarker;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.ast.SqlProjection;
import io.nop.orm.eql.ast.SqlQualifiedName;
import io.nop.orm.eql.ast.SqlQuerySelect;
import io.nop.orm.eql.ast.SqlSelect;
import io.nop.orm.eql.ast.SqlSelectWithCte;
import io.nop.orm.eql.ast.SqlSingleTableSource;
import io.nop.orm.eql.ast.SqlStringLiteral;
import io.nop.orm.eql.ast.SqlSubQueryExpr;
import io.nop.orm.eql.ast.SqlSubqueryTableSource;
import io.nop.orm.eql.ast.SqlTableName;
import io.nop.orm.eql.ast.SqlTableSource;
import io.nop.orm.eql.ast.SqlUnionSelect;
import io.nop.orm.eql.ast.SqlUpdate;
import io.nop.orm.eql.ast.SqlWindowDecl;
import io.nop.orm.eql.compile.ISqlCompileContext;
import io.nop.orm.eql.enums.SqlOperator;
import io.nop.orm.eql.meta.ISqlTableMeta;
import io.nop.orm.eql.param.ISqlParamBuilder;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityPropModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.model.OrmModelConstants;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * plan 2318 Phase 3：EQL 编译期可检索加密改写（平台首个生产 {@link IEqlAstTransformer}）。
 *
 * <p>在 before-analyze 阶段（语义分析解析列之前、参数收集固化之前）遍历语句树：</p>
 * <ul>
 * <li>等值/不等值/IN 谓词：enc 主列引用改写到 assisted 辅助列，参数 marker 注入
 * 执行期摘要 builder（经 {@code SqlParamTypeResolver} 的 marker-first 分流优先消费），
 * 字面量直接替换为摘要字面量；</li>
 * <li>LIKE/NOT LIKE/ILIKE：改写到 like 辅助列，pattern 按契约变换（通配符保留、字面字符
 * 逐字符摘要）；ILIKE 折叠为小写规范化摘要 + 普通 LIKE，不依赖方言 ILIKE 函数注册；</li>
 * <li>enc 主列出现在其他任何上下文（范围谓词、ORDER BY/GROUP BY、JOIN ON、函数参数、
 * IN 子查询、含 escape 的 LIKE、经 to-many/集合路径引用等）→
 * {@code ERR_ORM_ENC_COLUMN_NOT_SEARCHABLE} 显式失败；</li>
 * <li>未声明辅助列的 enc 列与非 enc 列零影响（维持现状）。</li>
 * </ul>
 *
 * <p>改写只依赖静态元数据（实体模型扩展属性），与参数值无关，因此与查询计划缓存兼容：
 * 编译产物中的 builder 是无状态的（执行期按 marker 的 paramIndex 读取输入再做摘要）。
 * 同名 EQL 第二次编译命中缓存时，缓存的 builder 对不同参数值各自正确摘要。</p>
 *
 * <p>接入缝约束（plan 2318 Baseline #5）：参数列表在 EqlTransformVisitor.transform 内经
 * SqlParamTypeResolver 固化，transformAfterAnalyze 阶段已无法影响参数——本类全部改写发生在
 * transformBeforeAnalyze，参数变换经 {@link SqlParameterMarker#setSqlParamBuilder(ISqlParamBuilder)}
 * 通道生效。别名解析在本阶段 AST 尚未做语义分析，故自行维护 FROM 子句的 alias→实体映射
 * （含无别名时的表名缺省与单表源裸列缺省），to-one 关系链路径一并解析。</p>
 */
public class EncAssistedEqlAstTransformer implements IEqlAstTransformer {

    private final IEqlAstTransformer delegate;

    /**
     * @param delegate 用户配置的 transformer（可为 null）。组合顺序：before-analyze 先 enc 改写
     *                 再委托用户 transformer；after-analyze 仅委托。delegate 为 null 时本类即完整
     *                 默认 transformer。
     */
    public EncAssistedEqlAstTransformer(IEqlAstTransformer delegate) {
        this.delegate = delegate;
    }

    public IEqlAstTransformer getDelegate() {
        return delegate;
    }

    @Override
    public void transformBeforeAnalyze(SqlProgram ast, String name, String sql, ISqlCompileContext context) {
        new Rewriter(context).walkProgram(ast);
        if (delegate != null)
            delegate.transformBeforeAnalyze(ast, name, sql, context);
    }

    @Override
    public void transformAfterAnalyze(SqlProgram ast, String name, String sql, ISqlCompileContext context) {
        if (delegate != null)
            delegate.transformAfterAnalyze(ast, name, sql, context);
    }

    /**
     * 表达式处理上下文：只有谓词上下文（WHERE/HAVING/UPDATE|DELETE 的 WHERE）做改写；
     * JOIN ON 与 ORDER BY/GROUP BY 中的 enc 主列显式失败；SELECT 投影属于行读取，放行。
     */
    enum ExprContext {
        PREDICATE, JOIN_ON, ORDER_GROUP, PROJECTION
    }

    /**
     * 一次列引用的解析结果：所属实体 + 目标列模型。
     */
    static class ColumnResolution {
        final IEntityModel entity;
        final IColumnModel column;

        ColumnResolution(IEntityModel entity, IColumnModel column) {
            this.entity = entity;
            this.column = column;
        }

        boolean isEnc() {
            return column.containsTag(OrmModelConstants.TAG_ENC);
        }

        boolean hasHelper() {
            return helperName(OrmModelConstants.EXT_ENC_ASSISTED) != null
                    || helperName(OrmModelConstants.EXT_ENC_LIKE) != null;
        }

        String helperName(String extName) {
            Object value = column.prop_get(extName);
            return value == null ? null : value.toString();
        }
    }

    /**
     * 别名作用域：alias/scopeName → 实体名。只登记可静态解析为实体的表源；
     * 子查询/CTE/VALUES 表源不登记（其列引用无法静态解析，跳过不处理）。
     */
    static class TableScope {
        final TableScope parent;
        final Map<String, String> aliasToEntity = new HashMap<>();
        int entitySourceCount;

        TableScope(TableScope parent) {
            this.parent = parent;
        }

        String lookupAlias(String alias) {
            TableScope scope = this;
            while (scope != null) {
                String entityName = scope.aliasToEntity.get(alias);
                if (entityName != null)
                    return entityName;
                scope = scope.parent;
            }
            return null;
        }
    }

    static class Rewriter {
        private final ISqlCompileContext context;
        private final Deque<TableScope> scopes = new ArrayDeque<>();

        Rewriter(ISqlCompileContext context) {
            this.context = context;
        }

        // ---- 语句级遍历 ----

        void walkProgram(SqlProgram program) {
            if (program.getStatements() == null)
                return;
            for (EqlASTNode stm : program.getStatements()) {
                walkStatement(stm);
            }
        }

        void walkStatement(EqlASTNode stm) {
            if (stm instanceof SqlSelectWithCte) {
                SqlSelectWithCte withCte = (SqlSelectWithCte) stm;
                if (withCte.getWithCtes() != null) {
                    for (SqlCteStatement cte : withCte.getWithCtes()) {
                        walkStatement(cte.getStatement());
                    }
                }
                walkSelect(withCte.getSelect());
            } else if (stm instanceof SqlUnionSelect) {
                walkSelect(((SqlUnionSelect) stm).getLeft());
                walkSelect(((SqlUnionSelect) stm).getRight());
            } else if (stm instanceof SqlSelect) {
                walkSelect((SqlSelect) stm);
            } else if (stm instanceof SqlUpdate) {
                SqlUpdate update = (SqlUpdate) stm;
                // SET 赋值属写路径（bulk update 边界由用户裁定归业务层校验），不遍历不报错
                walkDmlWhere(update.getTableName(), update.getAlias(),
                        update.getWhere() == null ? null : update.getWhere().getExpr());
            } else if (stm instanceof SqlDelete) {
                SqlDelete delete = (SqlDelete) stm;
                walkDmlWhere(delete.getTableName(), delete.getAlias(),
                        delete.getWhere() == null ? null : delete.getWhere().getExpr());
            }
            // INSERT/DDL 等其他语句：写路径与查询改写无关，零影响
        }

        void walkDmlWhere(SqlTableName tableName, SqlAlias alias, SqlExpr where) {
            if (tableName == null)
                return;
            TableScope scope = new TableScope(null);
            scopes.push(scope);
            try {
                registerSingleSource(scope, tableName, alias == null ? null : alias.getAlias());
                walkExpr(where, ExprContext.PREDICATE);
            } finally {
                scopes.pop();
            }
        }

        void walkSelect(SqlSelect select) {
            if (select == null)
                return;
            if (!(select instanceof SqlQuerySelect))
                return;
            SqlQuerySelect query = (SqlQuerySelect) select;

            TableScope scope = new TableScope(scopes.peek());
            scopes.push(scope);
            try {
                registerFrom(scope, query.getFrom());

                for (SqlProjection proj : query.getProjections()) {
                    walkExpr(proj, ExprContext.PROJECTION);
                }
                walkExpr(query.getWhere() == null ? null : query.getWhere().getExpr(), ExprContext.PREDICATE);
                walkFromConditions(query.getFrom());
                walkExpr(query.getGroupBy(), ExprContext.ORDER_GROUP);
                walkExpr(query.getHaving(), ExprContext.PREDICATE);
                walkWindows(query);
                walkExpr(query.getOrderBy(), ExprContext.ORDER_GROUP);
            } finally {
                scopes.pop();
            }
        }

        private void walkWindows(SqlQuerySelect query) {
            if (query.getWindowClause() == null || query.getWindowClause().getItems() == null)
                return;
            for (SqlWindowDecl decl : query.getWindowClause().getItems()) {
                walkExpr(decl.getPartitionBy(), ExprContext.ORDER_GROUP);
                walkExpr(decl.getOrderBy(), ExprContext.ORDER_GROUP);
                walkExpr(decl.getFrame(), ExprContext.ORDER_GROUP);
            }
        }

        // ---- FROM 子句：别名登记与 JOIN 条件 ----

        void registerFrom(TableScope scope, SqlFrom from) {
            if (from == null || from.getTableSources() == null)
                return;
            for (SqlTableSource source : from.getTableSources()) {
                registerSource(scope, source);
            }
        }

        void registerSource(TableScope scope, SqlTableSource source) {
            if (source instanceof SqlSingleTableSource) {
                SqlSingleTableSource single = (SqlSingleTableSource) source;
                String aliasName = single.getAlias() == null ? null : single.getAlias().getAlias();
                registerSingleSource(scope, single.getTableName(), aliasName);
            } else if (source instanceof SqlJoinTableSource) {
                SqlJoinTableSource join = (SqlJoinTableSource) source;
                registerSource(scope, join.getLeft());
                registerSource(scope, join.getRight());
            }
            // 子查询/VALUES/TUMBLE 表源：列引用无法静态解析到实体，不登记（引用时跳过）
        }

        void registerSingleSource(TableScope scope, SqlTableName tableName, String aliasName) {
            if (tableName == null)
                return;

            // 优先按实体名解析（含包名限定名与短名；与 EqlTransformVisitor 的解析顺序一致）。
            // 注意不能以 owner!=null 判定属性路径表源：`from io.nop.app.SimsExam o` 的表名 owner
            // 是包名限定符，不是别名引用。
            String fullName = tableName.getFullName();
            IEntityModel entity = resolveEntity(fullName);
            if (entity != null) {
                String scopeKey = aliasName != null ? aliasName : fullName;
                scope.aliasToEntity.put(scopeKey, entity.getName());
                scope.entitySourceCount++;
                return;
            }

            if (tableName.getOwner() != null) {
                // 属性路径表源（如 `join o.dept d`）：root 别名 + to-one 关系链解析目标实体
                List<String> segments = qualifiedSegments(tableName.getOwner());
                segments.add(tableName.getName());
                String rootEntityName = scope.lookupAlias(segments.get(0));
                if (rootEntityName == null)
                    return;
                IEntityModel pathEntity = resolveEntity(rootEntityName);
                for (int i = 1; i < segments.size() && pathEntity != null; i++) {
                    pathEntity = toOneTarget(pathEntity, segments.get(i));
                }
                if (pathEntity != null) {
                    String scopeKey = aliasName != null ? aliasName : segments.get(segments.size() - 1);
                    scope.aliasToEntity.put(scopeKey, pathEntity.getName());
                    scope.entitySourceCount++;
                }
            }
            // 非实体名（CTE/内嵌视图等）不登记（引用时跳过）
        }

        void walkFromConditions(SqlFrom from) {
            if (from == null || from.getTableSources() == null)
                return;
            for (SqlTableSource source : from.getTableSources()) {
                walkSourceConditions(source);
            }
        }

        void walkSourceConditions(SqlTableSource source) {
            if (source instanceof SqlJoinTableSource) {
                SqlJoinTableSource join = (SqlJoinTableSource) source;
                walkExpr(join.getCondition(), ExprContext.JOIN_ON);
                walkSourceConditions(join.getLeft());
                walkSourceConditions(join.getRight());
            } else if (source instanceof SqlSubqueryTableSource) {
                walkSelect(((SqlSubqueryTableSource) source).getQuery());
            }
        }

        // ---- 表达式遍历与谓词改写 ----

        void walkExpr(EqlASTNode node, ExprContext ctx) {
            if (node == null)
                return;

            if (node instanceof SqlColumnName) {
                checkColumnUsage((SqlColumnName) node, ctx);
                return;
            }
            if (node instanceof SqlParameterMarker) {
                return;
            }
            if (node instanceof SqlSelect) {
                walkSelect((SqlSelect) node);
                return;
            }
            if (node instanceof SqlSubQueryExpr) {
                walkSelect(((SqlSubQueryExpr) node).getSelect());
                return;
            }
            if (node instanceof SqlExistsExpr) {
                walkExpr(((SqlExistsExpr) node).getQuery(), ctx);
                return;
            }

            if (ctx == ExprContext.PREDICATE) {
                if (node instanceof SqlBinaryExpr) {
                    SqlBinaryExpr binary = (SqlBinaryExpr) node;
                    if (binary.getOperator() == SqlOperator.EQ || binary.getOperator() == SqlOperator.NE) {
                        rewriteEqNe(binary);
                        return;
                    }
                } else if (node instanceof SqlInValuesExpr) {
                    rewriteInValues((SqlInValuesExpr) node);
                    return;
                } else if (node instanceof SqlInQueryExpr) {
                    rewriteInQuery((SqlInQueryExpr) node);
                    return;
                } else if (node instanceof SqlLikeExpr) {
                    rewriteLike((SqlLikeExpr) node);
                    return;
                }
            }

            node.forEachChild(child -> walkExpr(child, ctx));
        }

        /**
         * 等值/不等值谓词改写：一侧为声明了 assisted 辅助列的 enc 主列、另一侧为参数或字面量
         * （或 NULL 字面量——主列置 null 时辅助列同步派生为 null，仅改列即保持语义）。
         */
        void rewriteEqNe(SqlBinaryExpr binary) {
            SqlExpr left = binary.getLeft();
            SqlExpr right = binary.getRight();

            ColumnResolution master = asEqMaster(left);
            boolean leftIsMaster = master != null;
            if (!leftIsMaster)
                master = asEqMaster(right);
            if (master == null) {
                // 两侧都不是 enc 主列：普通表达式按默认方式遍历
                walkExpr(left, ExprContext.PREDICATE);
                walkExpr(right, ExprContext.PREDICATE);
                return;
            }

            SqlColumnName masterCol = (SqlColumnName) (leftIsMaster ? left : right);
            SqlExpr other = leftIsMaster ? right : left;
            masterCol.setName(master.helperName(OrmModelConstants.EXT_ENC_ASSISTED));

            EncAssistedHelper helper = EncAssistedHelper.instance();
            if (other instanceof SqlParameterMarker) {
                ((SqlParameterMarker) other)
                        .setSqlParamBuilder(new DigestParamBuilder((SqlParameterMarker) other, false));
            } else if (other instanceof SqlStringLiteral) {
                ((SqlStringLiteral) other).setValue(helper.assistedDigest(((SqlStringLiteral) other).getValue()));
            } else if (other instanceof SqlNumberLiteral) {
                ((SqlNumberLiteral) other).setValue(helper.assistedDigest(((SqlNumberLiteral) other).getValue()));
            } else if (other instanceof SqlNullLiteral) {
                // 仅改列，NULL 比较语义不变
            } else {
                throw notSearchable(master, "eq-operand-not-literal-or-param");
            }
        }

        /**
         * IN 值列表改写：主列改写到 assisted 列，值列表逐项摘要（参数注入 builder、字面量替换）。
         */
        void rewriteInValues(SqlInValuesExpr in) {
            ColumnResolution master = asEqMaster(in.getExpr());
            if (master == null) {
                walkExpr(in.getExpr(), ExprContext.PREDICATE);
                if (in.getValues() != null) {
                    for (SqlExpr value : in.getValues()) {
                        walkExpr(value, ExprContext.PREDICATE);
                    }
                }
                return;
            }

            ((SqlColumnName) in.getExpr()).setName(master.helperName(OrmModelConstants.EXT_ENC_ASSISTED));
            if (in.getValues() == null)
                return;
            EncAssistedHelper helper = EncAssistedHelper.instance();
            for (SqlExpr value : in.getValues()) {
                if (value instanceof SqlParameterMarker) {
                    ((SqlParameterMarker) value)
                            .setSqlParamBuilder(new DigestParamBuilder((SqlParameterMarker) value, false));
                } else if (value instanceof SqlStringLiteral) {
                    ((SqlStringLiteral) value).setValue(helper.assistedDigest(((SqlStringLiteral) value).getValue()));
                } else if (value instanceof SqlNumberLiteral) {
                    ((SqlNumberLiteral) value).setValue(helper.assistedDigest(((SqlNumberLiteral) value).getValue()));
                } else if (value instanceof SqlNullLiteral) {
                    // IN (NULL) 恒不命中，与主列语义一致
                } else {
                    throw notSearchable(master, "in-value-not-literal-or-param");
                }
            }
        }

        /**
         * IN 子查询形态：确定性摘要列无法表达半连接语义，显式失败（设计矩阵裁定）。
         */
        void rewriteInQuery(SqlInQueryExpr in) {
            ColumnResolution master = asEqMaster(in.getExpr());
            if (master != null)
                throw notSearchable(master, "in-subquery-not-supported");

            walkExpr(in.getExpr(), ExprContext.PREDICATE);
            walkExpr(in.getQuery(), ExprContext.PREDICATE);
        }

        /**
         * LIKE/NOT LIKE/ILIKE 改写：主列改写到 like 辅助列；pattern 字面量按契约变换
         * （%/_ 保留、字面字符逐字符摘要）；参数注入 pattern 摘要 builder。
         * ILIKE（ignoreCase）折叠为小写规范化摘要 + 普通 LIKE；NOT 旗标对称保留。
         */
        void rewriteLike(SqlLikeExpr like) {
            SqlExpr expr = like.getExpr();
            if (!(expr instanceof SqlColumnName)) {
                like.forEachChild(child -> walkExpr(child, ExprContext.PREDICATE));
                return;
            }

            SqlColumnName col = (SqlColumnName) expr;
            ColumnResolution resolution = resolveColumn(col);
            if (resolution == null) {
                // 引用无法静态解析（外部作用域/CTE/投影别名等）：交由既有语义分析处理
                walkExpr(like.getValue(), ExprContext.PREDICATE);
                return;
            }
            if (resolution.isEnc() && resolution.hasHelper()
                    && resolution.helperName(OrmModelConstants.EXT_ENC_LIKE) == null) {
                // enc 主列声明了辅助列但没有 like 辅助列：LIKE 不可服务，显式失败而非静默空结果
                throw notSearchable(resolution, "like-needs-enc-like-helper");
            }
            if (!resolution.isEnc() || !resolution.hasHelper()) {
                walkExpr(like.getValue(), ExprContext.PREDICATE);
                return;
            }

            if (like.getEscape() != null)
                throw notSearchable(resolution, "like-escape-not-supported");

            SqlExpr value = like.getValue();
            EncAssistedHelper helper = EncAssistedHelper.instance();
            if (value instanceof SqlParameterMarker) {
                ((SqlParameterMarker) value)
                        .setSqlParamBuilder(new DigestParamBuilder((SqlParameterMarker) value, true));
            } else if (value instanceof SqlStringLiteral) {
                ((SqlStringLiteral) value)
                        .setValue(helper.likePatternDigest(((SqlStringLiteral) value).getValue()));
            } else if (value instanceof SqlNumberLiteral) {
                ((SqlNumberLiteral) value)
                        .setValue(helper.likePatternDigest(((SqlNumberLiteral) value).getValue()));
            } else {
                throw notSearchable(resolution, "like-pattern-not-literal-or-param");
            }

            col.setName(resolution.helperName(OrmModelConstants.EXT_ENC_LIKE));
            // ILIKE 语义并入摘要（大小写折叠），统一输出普通 LIKE，不依赖方言 ILIKE 注册
            like.setIgnoreCase(false);
        }

        /**
         * 非（可改写）谓词位置上的列引用检查：enc 主列出现在范围比较、函数参数、JOIN ON、
         * ORDER BY/GROUP BY、BETWEEN、IS NULL 等上下文时显式失败。
         * SELECT 投影属于行读取，放行。
         */
        void checkColumnUsage(SqlColumnName col, ExprContext ctx) {
            if (ctx == ExprContext.PROJECTION)
                return;

            ColumnResolution resolution = resolveColumn(col);
            if (resolution == null && col.getOwner() != null) {
                // to-one 改写解析失败时，做宽松关系链检测：经 to-many 关系路径触及
                // enc 主列同样不可静默（否则退化为密文比较的空结果）
                ColumnResolution detection = detectThroughRelations(col);
                if (detection != null && detection.isEnc() && detection.hasHelper())
                    throw notSearchable(detection, "path-through-relation-not-rewritable");
                return;
            }
            if (resolution == null)
                return;
            if (!resolution.isEnc() || !resolution.hasHelper())
                return;

            switch (ctx) {
                case JOIN_ON:
                    throw notSearchable(resolution, "join-on-not-searchable");
                case ORDER_GROUP:
                    throw notSearchable(resolution, "order-by-or-group-by-not-searchable");
                default:
                    throw notSearchable(resolution, "unsupported-predicate-context");
            }
        }

        // ---- 列解析 ----

        /**
         * 等值侧主列判定：解析为声明了辅助列的 enc 列 → 返回解析结果（要求 assisted 存在，
         * 缺失则显式失败）；解析为未声明辅助列的 enc 列或普通列 → 返回 null（维持现状/普通处理）。
         */
        ColumnResolution asEqMaster(SqlExpr expr) {
            if (!(expr instanceof SqlColumnName))
                return null;
            ColumnResolution resolution = resolveColumn((SqlColumnName) expr);
            if (resolution == null)
                return null;
            if (!resolution.isEnc() || !resolution.hasHelper())
                return null;
            if (resolution.helperName(OrmModelConstants.EXT_ENC_ASSISTED) == null)
                // 声明了辅助列但没有 assisted：等值语义不可服务，显式失败而非静默空结果
                throw notSearchable(resolution, "eq-needs-enc-assisted-helper");
            return resolution;
        }

        /**
         * 严格改写解析：owner 首段为别名（沿作用域链查找），其余段必须是 to-one 关系链，
         * 末段为最终实体上的列。可解析 ⇒ 末段列名可直接替换为辅助列属性名
         * （语义分析将沿同一关系链把引用解析到关联实体的辅助列上）。
         */
        ColumnResolution resolveColumn(SqlColumnName col) {
            List<String> segments = qualifiedSegments(col.getOwner());
            IEntityModel entity;
            if (segments.isEmpty()) {
                entity = singleEntityOfCurrentScope();
                if (entity == null)
                    return null;
            } else {
                String entityName = currentScope().lookupAlias(segments.get(0));
                if (entityName == null)
                    return null;
                entity = resolveEntity(entityName);
                for (int i = 1; i < segments.size() && entity != null; i++) {
                    entity = toOneTarget(entity, segments.get(i));
                }
                if (entity == null)
                    return null;
            }

            IEntityPropModel prop = findProp(entity, col.getName());
            if (!(prop instanceof IColumnModel))
                return null;
            return new ColumnResolution(entity, (IColumnModel) prop);
        }

        /**
         * 宽松检测解析（仅用于显式失败判定）：关系链允许 to-many，
         * 用于发现"经关系路径触及 enc 主列"的不可改写引用。
         */
        ColumnResolution detectThroughRelations(SqlColumnName col) {
            List<String> segments = qualifiedSegments(col.getOwner());
            if (segments.isEmpty())
                return null;
            String entityName = currentScope().lookupAlias(segments.get(0));
            if (entityName == null)
                return null;
            IEntityModel entity = resolveEntity(entityName);
            for (int i = 1; i < segments.size() && entity != null; i++) {
                entity = anyRelationTarget(entity, segments.get(i));
            }
            if (entity == null)
                return null;
            IEntityPropModel prop = findProp(entity, col.getName());
            if (!(prop instanceof IColumnModel))
                return null;
            return new ColumnResolution(entity, (IColumnModel) prop);
        }

        IEntityModel singleEntityOfCurrentScope() {
            TableScope scope = currentScope();
            if (scope.entitySourceCount != 1)
                return null;
            for (String entityName : scope.aliasToEntity.values()) {
                return resolveEntity(entityName);
            }
            return null;
        }

        IEntityModel toOneTarget(IEntityModel entity, String propName) {
            IEntityPropModel prop = findProp(entity, propName);
            if (!(prop instanceof IEntityRelationModel) || !prop.isToOneRelation())
                return null;
            return resolveEntity(((IEntityRelationModel) prop).getRefEntityName());
        }

        IEntityModel anyRelationTarget(IEntityModel entity, String propName) {
            IEntityPropModel prop = findProp(entity, propName);
            if (!(prop instanceof IEntityRelationModel))
                return null;
            return resolveEntity(((IEntityRelationModel) prop).getRefEntityName());
        }

        IEntityPropModel findProp(IEntityModel entity, String propName) {
            IEntityPropModel prop = entity.getProp(propName, true);
            if (prop == null && context.isAllowUnderscoreName())
                prop = entity.getPropByUnderscoreName(propName);
            return prop;
        }

        IEntityModel resolveEntity(String entityName) {
            ISqlTableMeta tableMeta = context.resolveEntityTableMeta(entityName);
            return tableMeta == null ? null : tableMeta.getEntityModel();
        }

        TableScope currentScope() {
            return scopes.peek();
        }

        static List<String> qualifiedSegments(SqlQualifiedName owner) {
            List<String> segments = new ArrayList<>(2);
            SqlQualifiedName cur = owner;
            while (cur != null) {
                segments.add(cur.getName());
                cur = cur.getNext();
            }
            return segments;
        }

        NopException notSearchable(ColumnResolution resolution, String reason) {
            return new NopException(OrmErrors.ERR_ORM_ENC_COLUMN_NOT_SEARCHABLE)
                    .param(OrmErrors.ARG_ENTITY_NAME, resolution.entity.getName())
                    .param(OrmErrors.ARG_PROP_NAME, resolution.column.getName())
                    .param(OrmErrors.ARG_REASON, reason);
        }
    }

    /**
     * 执行期参数摘要 builder：编译期注入到 {@link SqlParameterMarker}，由
     * SqlParamTypeResolver 的 marker-first 分流优先消费。持有 marker 自身引用：
     * paramIndex 在编译后期（EqlTransformVisitor.collectNames）统一编号，执行期读取——
     * 与查询计划缓存兼容（plan 2318 Phase 3 Decision：同一缓存计划对不同参数各自正确摘要）。
     */
    static class DigestParamBuilder implements ISqlParamBuilder {
        private final SqlParameterMarker marker;
        private final boolean likePattern;

        DigestParamBuilder(SqlParameterMarker marker, boolean likePattern) {
            this.marker = marker;
            this.likePattern = likePattern;
        }

        @Override
        public void buildParams(List<Object> input, List<Object> params) {
            Object value = input.get(marker.getParamIndex());
            if (value == null) {
                params.add(null);
                return;
            }
            EncAssistedHelper helper = EncAssistedHelper.instance();
            String text = value.toString();
            params.add(likePattern ? helper.likePatternDigest(text) : helper.assistedDigest(text));
        }
    }
}
