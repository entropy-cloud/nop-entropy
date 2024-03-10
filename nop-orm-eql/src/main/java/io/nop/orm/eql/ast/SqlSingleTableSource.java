/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.ast;

import io.nop.core.lang.ast.ASTNode;
import io.nop.orm.eql.ast._gen._SqlSingleTableSource;
import io.nop.orm.eql.meta.ISqlSelectionMeta;

public class SqlSingleTableSource extends _SqlSingleTableSource {
    private boolean forPropJoin;
    private boolean filterAlreadyAdded;

    public boolean isFilterAlreadyAdded() {
        return filterAlreadyAdded;
    }

    public void setFilterAlreadyAdded(boolean filterAlreadyAdded) {
        this.filterAlreadyAdded = filterAlreadyAdded;
    }

    public boolean isForPropJoin() {
        return forPropJoin;
    }

    public void setForPropJoin(boolean forPropJoin) {
        this.forPropJoin = forPropJoin;
    }

    public boolean isGeneratedAlias() {
        return alias != null && alias.isGenerated();
    }

    @Override
    public SqlSelect getSourceSelect() {
        return getTableName().getResolvedCte();
    }

    @Override
    protected void copyExtFieldsTo(ASTNode node) {
        super.copyExtFieldsTo(node);
        SqlSingleTableSource source = (SqlSingleTableSource) node;
        source.forPropJoin = forPropJoin;
    }

    public ISqlSelectionMeta getResolvedTableMeta() {
        return getTableName().getResolvedTableMeta();
    }

    public String getScopeName() {
        SqlAlias alias = getAlias();
        if (alias != null)
            return alias.getAlias();
        return getTableName().getName();
    }
}
