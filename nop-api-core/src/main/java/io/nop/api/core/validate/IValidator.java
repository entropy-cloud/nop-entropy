/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.validate;

import io.nop.api.core.util.FutureHelper;

import java.util.concurrent.CompletionStage;

public interface IValidator<T> {
    IValidator<Object> ALWAYS_VALID = new IValidator<Object>() {
        @Override
        public void validate(Object message, IValidationErrorCollector collector) {
        }

        @Override
        public CompletionStage<Void> validateAsync(Object message, IValidationErrorCollector collector) {
            return FutureHelper.success(null);
        }
    };

    static <T> IValidator<T> alwaysValid() {
        return (IValidator<T>) ALWAYS_VALID;
    }

    default void validate(T message, IValidationErrorCollector collector) {
        FutureHelper.syncGet(validateAsync(message, collector));
    }

    default CompletionStage<Void> validateAsync(T message, IValidationErrorCollector collector) {
        return FutureHelper.futureRun(() -> validate(message, collector));
    }

    default CompletionStage<Void> validateAsync(T message) {
        return validateAsync(message, IValidationErrorCollector.THROW_ERROR);
    }
}
