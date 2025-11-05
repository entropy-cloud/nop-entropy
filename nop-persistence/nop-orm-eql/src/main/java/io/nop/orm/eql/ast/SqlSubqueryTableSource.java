/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.ast;

import io.nop.orm.eql.ast._gen._SqlSubqueryTableSource;
import io.nop.orm.eql.meta.ISqlSelectionMeta;

public class SqlSubqueryTableSource extends _SqlSubqueryTableSource {
    private SqlSelect withClauseSelect;

    public SqlSelect getWithClauseSelect() {
        return withClauseSelect;
    }

    public void setWithClauseSelect(SqlSelect withClauseSelect) {
        this.withClauseSelect = withClauseSelect;
    }

    public boolean isSameWithClause(SqlTableSource table){
        if(withClauseSelect == null)
            return false;

        if(table instanceof SqlSubqueryTableSource)
            return withClauseSelect == ((SqlSubqueryTableSource) table).getWithClauseSelect();
        return false;
    }

    public boolean isGeneratedAlias() {
        return alias != null && alias.isGenerated();
    }

    @Override
    public SqlSelect getSourceSelect() {
        return getQuery();
    }

    @Override
    public ISqlSelectionMeta getResolvedTableMeta() {
        return getQuery().getResolvedTableMeta();
    }
}
