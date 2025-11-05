/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.ast;

import io.nop.orm.eql.ast._gen._SqlAndExpr;
import io.nop.orm.eql.enums.SqlOperator;

public class SqlAndExpr extends _SqlAndExpr {
    @Override
    public boolean requireParentheses(int lp, int rp) {
        int local = SqlOperator.AND.getPriority();
        if (local == lp && lp == rp)
            return false;
        if (lp < local && local >= rp) {
            return false;
        }

        return true;
    }
}
