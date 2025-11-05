/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.ast;

import io.nop.orm.eql.ast._gen._SqlWhere;

public class SqlWhere extends _SqlWhere {
    public void appendFilter(SqlExpr expr) {
        SqlExpr filter = this.getExpr();
        if (filter == null) {
            setExpr(expr);
        } else {
            SqlAndExpr and = new SqlAndExpr();
            filter.setASTParent(null);
            and.setLeft(filter);
            and.setRight(expr);
            setExpr(and);
        }
    }
}