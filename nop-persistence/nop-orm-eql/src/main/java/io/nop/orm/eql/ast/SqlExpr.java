/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.ast;

import io.nop.core.lang.ast.ASTNode;
import io.nop.commons.type.StdSqlType;
import io.nop.orm.eql.ast._gen._SqlExpr;
import io.nop.orm.eql.meta.ISqlExprMeta;

public abstract class SqlExpr extends _SqlExpr {
    private ISqlExprMeta resolvedExprMeta;

    @Override
    public StdSqlType getStdSqlType() {
        return resolvedExprMeta.getStdSqlType();
    }

    public String getResolvedOwner() {
        return null;
    }

    public ISqlExprMeta getResolvedExprMeta() {
        return resolvedExprMeta;
    }

    public void setResolvedExprMeta(ISqlExprMeta resolvedExprMeta) {
        this.resolvedExprMeta = resolvedExprMeta;
    }

    @Override
    protected void copyExtFieldsTo(ASTNode node) {
        SqlExpr expr = (SqlExpr) node;
        expr.resolvedExprMeta = resolvedExprMeta;
    }
}