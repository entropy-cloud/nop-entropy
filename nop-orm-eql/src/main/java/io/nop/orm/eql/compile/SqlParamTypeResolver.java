/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.eql.compile;

import io.nop.commons.type.StdSqlType;
import io.nop.dataset.binder.DataParameterBinders;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.dao.dialect.IDialect;
import io.nop.orm.eql.ast.EqlASTNode;
import io.nop.orm.eql.ast.EqlASTVisitor;
import io.nop.orm.eql.ast.SqlAssignment;
import io.nop.orm.eql.ast.SqlBinaryExpr;
import io.nop.orm.eql.ast.SqlParameterMarker;
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
    public void visitSqlParameterMarker(SqlParameterMarker node) {
        EqlASTNode parent = node.getASTParent();
        ISqlExprMeta resolvedMeta = null;

        switch (parent.getASTKind()) {
            case SqlAssignment: {
                SqlAssignment assign = (SqlAssignment) parent;
                resolvedMeta = assign.getColumnName().getResolvedExprMeta();
                break;
            }
            case SqlBinaryExpr: {
                SqlBinaryExpr binaryExpr = (SqlBinaryExpr) parent;
                StdSqlType sqlType;
                if (binaryExpr.getLeft() == node) {
                    resolvedMeta = binaryExpr.getRight().getResolvedExprMeta();
                    sqlType = binaryExpr.getOperator().getLeftSqlType();
                } else {
                    resolvedMeta = binaryExpr.getLeft().getResolvedExprMeta();
                    sqlType = binaryExpr.getOperator().getRightSqlType();
                }
                if (resolvedMeta == null) {
                    IDataParameterBinder binder = dialect.getDataParameterBinder(sqlType.getStdDataType(), sqlType);
                    resolvedMeta = new SingleColumnExprMeta("?", binder, ExprOrmDataType.fromSqlType(sqlType));
                }
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
        } else if (dataType.getKind() == OrmDataTypeKind.COLUMN || dataType.getKind() == OrmDataTypeKind.EXPR) {
            params.add(new SimpleParamBuilder(node.getParamIndex()));
        } else {
            // 这里暂时简化处理，不进行类型判断
            params.add(new SimpleParamBuilder(node.getParamIndex()));
        }
    }
}
