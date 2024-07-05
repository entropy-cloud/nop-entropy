/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.context;

import io.nop.api.core.annotations.core.Internal;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.util.ICancellable;
import io.nop.commons.lang.IAttributeSet;
import io.nop.core.lang.eval.IEvalScope;

import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public interface IExecutionContext extends IEvalContext, IAttributeSet, ICancellable {

    String getExecutionId();

    void setExecutionId(String executionId);

    void setEvalScope(IEvalScope scope);

    /**
     * 这里使用了一个技巧，通过Future<Consumer<ExecutionContext>>这种方式注册一个回调函数， 当awaitAsyncResults返回时会等待所有future结束并执行回调。
     *
     * @param asyncFuture 异步调用对应的future对象
     */
    void registerAsyncResult(Future<Consumer<? extends IExecutionContext>> asyncFuture);

    boolean hasAsyncResult();

    void awaitAsyncResults();

    void cancelAsyncResults();

    /**
     * 执行complete操作或者completeExceptionally操作会先触发beforeComplete回调函数，然后再迁移状态。
     *
     * @param callback 如果是complete调用，入口参数为null。如果回调函数抛出异常，将导致后续的beforeComplete回调被跳过，complete调用失败。
     *                 如果是completeExceptionally调用，入口参数为异常对象，回调函数抛出的异常将被自动忽略
     */
    void addBeforeComplete(Consumer<Throwable> callback);

    /**
     * 执行complete或者completeExceptionally操作会将ExecutionContext的内部状态标记为完成状态。 此时会回调afterComplete回调函数。回调函数抛出的异常将被自动忽略
     */
    void addAfterComplete(Consumer<Throwable> callback);

    /**
     * complete和completeExceptionally会自动调用fireBeforeComplete来触发回调函数。也可以直接调用。
     *
     * @param exception 通过completeExceptionally函数触发时会传入异常对象
     */
    @Internal
    void fireBeforeComplete(Throwable exception);

    /**
     * complete操作会先触发所有beforeComplete回调函数，当所有回调函数都成功执行之后才会迁移到完成状态。 然后再调用所有的afterComplete回调。
     */
    @Internal
    void complete();

    @Internal
    void completeExceptionally(Throwable exception);

    Throwable getError();

    void setError(Throwable error);

    List<ErrorBean> getErrorBeans();

    void addErrorBean(ErrorBean error);

    ErrorBean getMostSevereErrorBean();

    boolean isDone();

    default boolean isSuccess() {
        return isDone() && getError() != null;
    }
}