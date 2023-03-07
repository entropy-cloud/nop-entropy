/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.eql.ast;

import io.nop.orm.eql.ast._gen._SqlUnionSelect;

import java.util.List;

public class SqlUnionSelect extends _SqlUnionSelect {

    @Override
    public SqlStatementKind getStatementKind() {
        return SqlStatementKind.SELECT;
    }

    public SqlQuerySelect getFirstSelect() {
        if (left instanceof SqlQuerySelect)
            return (SqlQuerySelect) left;

        SqlUnionSelect select = (SqlUnionSelect) left;
        return select.getFirstSelect();
    }

    @Override
    public List<SqlProjection> getProjections() {
        return getFirstSelect().getProjections();
    }
}
