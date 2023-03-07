/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.eql.ast;

import io.nop.orm.eql.ast._gen._SqlNotExpr;
import io.nop.orm.eql.enums.SqlOperator;

public class SqlNotExpr extends _SqlNotExpr {
    @Override
    public boolean requireParentheses(int lp, int rp) {
        int local = SqlOperator.NOT.getPriority();
        if (lp < local && local >= rp) {
            return false;
        }

        return true;
    }
}
