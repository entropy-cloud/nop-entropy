/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.validate;

import io.nop.api.core.beans.ErrorBean;

import java.util.ArrayList;
import java.util.List;

public class ListValidationErrorCollector implements IValidationErrorCollector {
    private final List<ErrorBean> errors = new ArrayList<>();

    public boolean isEmpty() {
        return errors.isEmpty();
    }

    public void clear() {
        errors.clear();
    }

    @Override
    public void addError(ErrorBean error) {
        errors.add(error);
    }

    public List<ErrorBean> getErrors() {
        return errors;
    }

    public void addTo(IValidationErrorCollector collector) {
        for (ErrorBean error : errors) {
            collector.addError(error);
        }
    }
}
