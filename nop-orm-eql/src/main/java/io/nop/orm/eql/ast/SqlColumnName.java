/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.ast;

import io.nop.core.lang.ast.ASTNode;
import io.nop.orm.eql.ast._gen._SqlColumnName;
import io.nop.orm.model.IEntityPropModel;
import io.nop.orm.model.OrmModelConstants;

public class SqlColumnName extends _SqlColumnName {
    private SqlTableSource tableSource;
    private IEntityPropModel propModel;

    private SqlProjection projection;

    public SqlProjection getProjection() {
        return projection;
    }

    public void setProjection(SqlProjection projection) {
        this.projection = projection;
    }

    public SqlTableSource getTableSource() {
        return tableSource;
    }

    public void setTableSource(SqlTableSource tableSource) {
        this.tableSource = tableSource;
    }

    @Override
    protected void copyExtFieldsTo(ASTNode node) {
        super.copyExtFieldsTo(node);
        SqlColumnName col = (SqlColumnName) node;
        col.tableSource = tableSource;
        col.propModel = propModel;
    }

    public boolean isMasked(){
        if(propModel == null)
            return false;
        return propModel.containsTag(OrmModelConstants.TAG_MASKED);
    }

    @Override
    public String getResolvedOwner() {
        if(tableSource == null)
            return null;
        return tableSource.getAliasName();
    }

    public IEntityPropModel getPropModel() {
        return propModel;
    }

    public void setPropModel(IEntityPropModel propModel) {
        this.propModel = propModel;
    }
}
