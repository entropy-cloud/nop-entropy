/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.ast;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.model.query.FilterOp;
import io.nop.xlang.ast._gen._FilterOpExpression;

import static io.nop.xlang.XLangErrors.ARG_OP;
import static io.nop.xlang.XLangErrors.ERR_XPL_UNKNOWN_FILTER_OP;

public abstract class FilterOpExpression extends _FilterOpExpression {

    @JsonIgnore
    protected FilterOp getFilterOp() {
        FilterOp filterOp = FilterOp.fromName(getOp());

        if (filterOp == null)
            throw new NopEvalException(ERR_XPL_UNKNOWN_FILTER_OP).param(ARG_OP, getOp()).loc(getLocation());
        return filterOp;
    }

    protected boolean handleResult(boolean b, IEvalScope scope) {
        return b;
    }
}