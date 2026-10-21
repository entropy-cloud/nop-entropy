/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.commons.type.StdDataType;
import io.nop.commons.type.StdSqlType;
import io.nop.dao.dialect.IDialect;
import io.nop.dataset.binder.DataParameterBinders;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.orm.eql.ast.EqlASTNode;
import io.nop.orm.eql.ast.EqlASTVisitor;
import io.nop.orm.eql.ast.SqlAssignment;
import io.nop.orm.eql.ast.SqlBetweenExpr;
import io.nop.orm.eql.ast.SqlBinaryExpr;
import io.nop.orm.eql.ast.SqlExpr;
import io.nop.orm.eql.ast.SqlInValuesExpr;
import io.nop.orm.eql.ast.SqlParameterMarker;
import io.nop.orm.eql.ast.SqlSingleTableSource;
import io.nop.orm.eql.ast.SqlTableSource;
import io.nop.orm.eql.meta.ISqlExprMeta;
import io.nop.orm.eql.meta.SingleColumnExprMeta;
import io.nop.orm.eql.param.EntityPropParamBuilder;
import io.nop.orm.eql.param.ISqlParamBuilder;
import io.nop.orm.eql.param.SimpleParamBuilder;
import io.nop.orm.model.ExprOrmDataType;
import io.nop.orm.model.IOrmDataType;
import io.nop.orm.model.OrmDataTypeKind;

import java.util.ArrayList;
import java.util.List;

/**
 * 确定eql语句中sql参数的数据类型
 */
public class SqlParamTypeResolver extends EqlASTVisitor {
    private final IDialect dialect;
    private List<ISqlParamBuilder> params = new ArrayList<>();

    public SqlParamTypeResolver(IDialect dialect) {
        this.dialect = dialect;
    }

    public List<ISqlParamBuilder> getParams() {
        return params;
    }

    @Override
    public void visitSqlSingleTableSource(SqlSingleTableSource node) {
        super.visitSqlSingleTableSource(node);
        // prop join的ON条件(含租户/逻辑删除/固定过滤器)在SQL文本中渲染于本表之后、where之前，
        // 必须按同样顺序收集其参数，否则运行期参数个数与SQL标记个数不匹配
        visitPropJoins(node);
    }

    private void visitPropJoins(SqlTableSource source) {
        if (source.getPropJoins() == null)
            return;
        for (SqlPropJoin propJoin : source.getPropJoins().values()) {
            if (propJoin.isExplicit())
                continue;
            visit(propJoin.getCondition());
            visitPropJoins(propJoin.getRight());
        }
    }

    @Override
    public void visitSqlParameterMarker(SqlParameterMarker node) {
        // 如果是自动加入的租户过滤条件
        if (node.getSqlParamBuilder() != null) {
            params.add(node.getSqlParamBuilder());
            return;
        }

        EqlASTNode parent = node.getASTParent();
        ISqlExprMeta resolvedMeta = null;
        // IN/BETWEEN 的对照列元数据只用于确定绑定类型，不写入参数节点的 resolvedExprMeta
        // （保持 ANY 语义，避免改变 EntityPropParamBuilder / SqlExpr.getStdSqlType 的下游行为）
        ISqlExprMeta bindMeta = null;
        // 仅比较谓词与 UPDATE SET 允许把 String 参数按对照列的 SQL 类型转换；LIKE 必须保持 String
        boolean coerceToSqlType = false;

        switch (parent.getASTKind()) {
            case SqlAssignment: {
                SqlAssignment assign = (SqlAssignment) parent;
                resolvedMeta = assign.getColumnName().getResolvedExprMeta();
                node.setMasked(assign.getColumnName().isMasked());
                coerceToSqlType = true;
                break;
            }
            case SqlInValuesExpr: {
                SqlInValuesExpr in = (SqlInValuesExpr) parent;
                bindMeta = in.getExpr().getResolvedExprMeta();
                resolvedMeta = new SingleColumnExprMeta("?", DataParameterBinders.ANY,
                        ExprOrmDataType.fromSqlType(StdSqlType.ANY));
                break;
            }
            case SqlBetweenExpr: {
                SqlBetweenExpr between = (SqlBetweenExpr) parent;
                bindMeta = between.getTest().getResolvedExprMeta();
                resolvedMeta = new SingleColumnExprMeta("?", DataParameterBinders.ANY,
                        ExprOrmDataType.fromSqlType(StdSqlType.ANY));
                break;
            }
            case SqlBinaryExpr: {
                SqlBinaryExpr binaryExpr = (SqlBinaryExpr) parent;
                StdSqlType sqlType;
                SqlExpr otherExpr;
                if (binaryExpr.getLeft() == node) {
                    otherExpr = binaryExpr.getRight();
                    resolvedMeta = binaryExpr.getRight().getResolvedExprMeta();
                    sqlType = binaryExpr.getOperator().getLeftSqlType();
                } else {
                    otherExpr = binaryExpr.getLeft();
                    resolvedMeta = binaryExpr.getLeft().getResolvedExprMeta();
                    sqlType = binaryExpr.getOperator().getRightSqlType();
                }

                // 比较算符要求相同的类型
                if (resolvedMeta == null && binaryExpr.getOperator().isCompareOp()) {
                    resolvedMeta = new ExprTypeResolver(dialect).resolveExprMeta(otherExpr);
                }

                if (resolvedMeta == null) {
                    IDataParameterBinder binder = dialect.getDataParameterBinder(sqlType.getStdDataType(), sqlType);
                    if (binder == null)
                        binder = DataParameterBinders.ANY;
                    resolvedMeta = new SingleColumnExprMeta("?", binder, ExprOrmDataType.fromSqlType(sqlType));
                }
                coerceToSqlType = binaryExpr.getOperator().isCompareOp();
                break;
            }
            default: {
                // 无法判定具体类型，则认为是any
                resolvedMeta = new SingleColumnExprMeta("?", DataParameterBinders.ANY,
                        ExprOrmDataType.fromSqlType(StdSqlType.ANY));
            }
        }
        node.setResolvedExprMeta(resolvedMeta);

        IOrmDataType dataType = resolvedMeta.getOrmDataType();
        if (dataType.getKind() == OrmDataTypeKind.COMPONENT || dataType.getKind() == OrmDataTypeKind.TO_ONE_RELATION) {
            params.add(new EntityPropParamBuilder(node.getParamIndex(), resolvedMeta));
        } else {
            StdDataType bindType;
            if (bindMeta != null && (bindMeta.getOrmDataType().getKind() == OrmDataTypeKind.COLUMN
                    || bindMeta.getOrmDataType().getKind() == OrmDataTypeKind.EXPR)) {
                bindType = resolveInBetweenBindType(bindMeta.getOrmDataType());
            } else {
                bindType = resolveBindType(dataType, coerceToSqlType);
            }
            params.add(new SimpleParamBuilder(bindType, node.getParamIndex()));
        }
    }

    /**
     * 比较谓词/UPDATE SET：当列声明的 Java 类型为 STRING 而 SQL 类型为数值时（orm-model-design.md
     * 方案 B：BIGINT 列 + stdDataType=string），按列的 SQL 类型转换绑定参数。与列 binder 的
     * AutoConvertDataParameterBinder 语义对齐：PostgreSQL 等不提供 bigint = varchar 隐式算子的
     * 数据库上，String 绑定会报 42883（operator does not exist）。
     *
     * <p>LIKE 等字符串谓词传入 coerceToSqlType=false，保持 String，避免 %foo% 被转成数字。</p>
     */
    static StdDataType resolveBindType(IOrmDataType dataType, boolean coerceToSqlType) {
        StdDataType stdType = dataType.getStdDataType();
        if (!coerceToSqlType)
            return stdType;
        StdSqlType sqlType = dataType.getStdSqlType();
        if (stdType == StdDataType.STRING && sqlType != null && sqlType.getStdDataType().isNumericType()) {
            return sqlType.getStdDataType();
        }
        return stdType;
    }

    /**
     * IN/BETWEEN：仅当对照列为「数值 SQL 类型 + STRING Java 类型」时按列类型转换；
     * 其余情况回退 ANY（保持原透传语义，不因本修复改变非目标场景的绑定行为）。
     */
    static StdDataType resolveInBetweenBindType(IOrmDataType dataType) {
        StdDataType stdType = dataType.getStdDataType();
        StdSqlType sqlType = dataType.getStdSqlType();
        if (stdType == StdDataType.STRING && sqlType != null && sqlType.getStdDataType().isNumericType()) {
            return sqlType.getStdDataType();
        }
        return StdDataType.ANY;
    }
}
