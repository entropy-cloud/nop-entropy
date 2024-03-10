/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.ast;

import io.nop.orm.eql.ast._gen._SqlSelectWithCte;

import java.util.List;

public class SqlSelectWithCte extends _SqlSelectWithCte {

    @Override
    public SqlStatementKind getStatementKind() {
        return SqlStatementKind.SELECT;
    }

    public SqlSelect getCte(String name) {
        List<SqlCteStatement> stms = getWithCtes();
        if (stms == null || stms.isEmpty())
            return null;

        for (SqlCteStatement stm : stms) {
            if (stm.getName().equals(name))
                return stm.getStatement();
        }
        return null;
    }
}
