/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.validate;

import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.exceptions.NopValidateException;

import static io.nop.api.core.ApiErrors.ARG_ERRORS;

public class LimitListValidationErrorCollector extends ListValidationErrorCollector {
    private final int maxCount;

    public LimitListValidationErrorCollector(int maxCount) {
        this.maxCount = maxCount;
    }

    @Override
    public void addError(ErrorBean error) {
        super.addError(error);
        if (getErrors().size() >= maxCount)
            throw new NopValidateException()
                    .param(ARG_ERRORS, getErrors());
    }
}
