/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.utils;

import io.nop.commons.functional.IAsyncFunctionInvoker;
import io.nop.commons.functional.IFunctionInvoker;
import io.nop.orm.IOrmTemplate;

import javax.inject.Inject;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class SingleSessionFunctionInvoker implements IAsyncFunctionInvoker, IFunctionInvoker {
    private IOrmTemplate ormTemplate;

    @Inject
    public void setOrmTemplate(IOrmTemplate ormTemplate) {
        this.ormTemplate = ormTemplate;
    }

    @Override
    public <R, T> CompletionStage<T> invokeAsync(Function<R, CompletionStage<T>> task, R request) {
        return ormTemplate.runInSessionAsync(session -> {
            return task.apply(request);
        });
    }

    @Override
    public <R, T> T invoke(Function<R, T> fn, R request) {
        return ormTemplate.runInSession(session -> {
            return fn.apply(request);
        });
    }
}