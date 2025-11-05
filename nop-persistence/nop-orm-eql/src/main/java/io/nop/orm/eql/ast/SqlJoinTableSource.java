/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.ast;

import io.nop.orm.eql.ast._gen._SqlJoinTableSource;
import io.nop.orm.eql.enums.SqlOperator;
import io.nop.orm.eql.meta.ISqlSelectionMeta;

public class SqlJoinTableSource extends _SqlJoinTableSource {
    @Override
    public ISqlSelectionMeta getResolvedTableMeta() {
        return getLeft().getResolvedTableMeta();
    }

    @Override
    public SqlSelect getSourceSelect() {
        return null;
    }

    public void addConditionFilter(SqlExpr filter) {
        if (filter == null)
            return;

        if (this.getCondition() == null) {
            this.setCondition(filter);
        } else {
            SqlBinaryExpr and = new SqlBinaryExpr();
            and.setOperator(SqlOperator.AND);
            SqlExpr cond = getCondition();
            cond.detach();
            and.setLeft(cond);
            and.setRight(filter);
            setCondition(and);
        }
    }
}
