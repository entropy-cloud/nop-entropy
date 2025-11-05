/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.ast;

import io.nop.orm.eql.ast._gen._SqlQuerySelect;

public class SqlQuerySelect extends _SqlQuerySelect {
    @Override
    public SqlStatementKind getStatementKind() {
        return SqlStatementKind.SELECT;
    }

    public SqlWhere makeWhere() {
        SqlWhere where = getWhere();
        if (where == null) {
            where = new SqlWhere();
            setWhere(where);
        }
        return where;
    }
}
