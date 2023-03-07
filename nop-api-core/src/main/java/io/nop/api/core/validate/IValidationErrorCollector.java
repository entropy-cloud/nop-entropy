/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.validate;

import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.exceptions.NopRebuildException;

public interface IValidationErrorCollector {
    IValidationErrorCollector THROW_ERROR = new IValidationErrorCollector() {
        @Override
        public void addError(ErrorBean error) {
            throw NopRebuildException.rebuild(error);
        }
    };

    void addError(ErrorBean error);

    default void addException(Throwable e) {
        throw NopException.adapt(e);
    }

    default ErrorBean buildError(String errorCode) {
        return new ErrorBean(errorCode);
    }

    default ErrorBean buildError(ErrorCode error) {
        ErrorBean bean = buildError(error.getErrorCode())
                .description(error.getDescription());
        bean.setStatus(error.getStatus());
        return bean;
    }
}