/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.ast;

import io.nop.core.lang.ast.ASTNode;
import io.nop.core.lang.sql.ISqlExpr;
import io.nop.commons.type.StdSqlType;
import io.nop.orm.eql.ast._gen._SqlSelect;
import io.nop.orm.eql.compile.ISqlSelect;
import io.nop.orm.eql.compile.SqlTableScope;
import io.nop.orm.eql.meta.ISqlExprMeta;
import io.nop.orm.eql.meta.ISqlSelectionMeta;

import java.util.List;

public abstract class SqlSelect extends _SqlSelect implements ISqlExpr, ISqlSelect {
    private StdSqlType sqlType;
    private SqlTableScope tableScope;

    private ISqlSelectionMeta resolvedTableMeta;

    public ISqlSelectionMeta getResolvedTableMeta() {
        return resolvedTableMeta;
    }

    public void setResolvedTableMeta(ISqlSelectionMeta resolvedTableMeta) {
        this.resolvedTableMeta = resolvedTableMeta;
    }

    @Override
    protected void copyExtFieldsTo(ASTNode node) {
        super.copyExtFieldsTo(node);
        SqlSelect expr = (SqlSelect) node;
        expr.sqlType = sqlType;
        expr.tableScope = tableScope;
        expr.resolvedTableMeta = resolvedTableMeta;
    }

    @Override
    public StdSqlType getStdSqlType() {
        return sqlType;
    }

    public final void setSqlType(StdSqlType sqlType) {
        this.sqlType = sqlType;
    }

    @Override
    public SqlTableScope getTableScope() {
        return tableScope;
    }

    public void setTableScope(SqlTableScope tableScope) {
        this.tableScope = tableScope;
    }

    public abstract List<SqlProjection> getProjections();

    @Override
    public SqlExprProjection getProjectionByAlias(String alias) {
        return null;
    }

    public SqlExprProjection getProjectionByExprMeta(ISqlExprMeta exprMeta) {
        for (SqlProjection proj : getProjections()) {
            SqlExprProjection exprProj = (SqlExprProjection) proj;
            if (exprProj.getExpr().getResolvedExprMeta() == exprMeta)
                return exprProj;
        }
        return null;
    }

    @Override
    public boolean isGeneratedTableAlias(String alias) {
        SqlTableSource table = getTableByAlias(alias);
        if (table == null)
            return false;
        return table.isGeneratedAlias();
    }

    @Override
    public boolean isGeneratedProjectionAlias(String alias) {
        SqlExprProjection expr = getProjectionByAlias(alias);
        if (expr == null)
            return false;
        return expr.isGeneratedAlias();
    }
}