/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.eql.ast;

import io.nop.orm.eql.ast._gen._SqlUpdate;
import io.nop.orm.eql.compile.ISqlTableSourceSupport;

public class SqlUpdate extends _SqlUpdate implements ISqlTableSourceSupport {
    private SqlSingleTableSource resolvedTableSource;

    @Override
    public SqlStatementKind getStatementKind() {
        return SqlStatementKind.UPDATE;
    }

    public SqlSingleTableSource getResolvedTableSource() {
        return resolvedTableSource;
    }

    public void setResolvedTableSource(SqlSingleTableSource resolvedTableSource) {
        this.resolvedTableSource = resolvedTableSource;
    }
}
