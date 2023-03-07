/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.context;

import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.exceptions.NopRebuildException;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.core.CoreConstants;
import io.nop.core.lang.eval.EvalScopeImpl;
import io.nop.core.lang.eval.IEvalScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public class ExecutionContextImpl extends Cancellable implements IExecutionContext {
    private static final Logger LOG = LoggerFactory.getLogger(ExecutionContextImpl.class);

    private final Map<String, Object> attributes;
    private IEvalScope scope;

    private List<Future<Consumer<? extends IExecutionContext>>> asyncResults;
    private List<Consumer<Throwable>> beforeCompletes;
    private List<Consumer<Throwable>> afterCompletes;
    private boolean done;
    private Throwable error;
    private List<ErrorBean> errorBeans;
    private String executionId;

    public ExecutionContextImpl(Map<String, Object> attributes) {
        this.attributes = attributes == null ? new ConcurrentHashMap<>() : attributes;
        scope = new EvalScopeImpl(getAttributes());
        scope.setLocalValue(null, CoreConstants.VAR_SVC_CTX, this);
    }

    public ExecutionContextImpl() {
        this(new ConcurrentHashMap<>());
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    @Override
    public IEvalScope getEvalScope() {
        return scope;
    }

    public void setEvalScope(IEvalScope scope) {
        this.scope = scope;
    }

    @Override
    public synchronized boolean hasAsyncResult() {
        return asyncResults != null && !asyncResults.isEmpty();
    }

    @Override
    public synchronized void registerAsyncResult(Future<Consumer<? extends IExecutionContext>> asyncResult) {
        if (isDone()) {
            throw new IllegalStateException("nop.err.execution-already-completed");
        }

        if (this.asyncResults == null)
            this.asyncResults = new ArrayList<>();
        this.asyncResults.add(asyncResult);
    }

    @Override
    public void awaitAsyncResults() {
        List<Future<Consumer<? extends IExecutionContext>>> results;
        synchronized (this) {
            results = this.asyncResults;
            this.asyncResults = null;
        }

        if (results == null)
            return;

        for (Future<Consumer<? extends IExecutionContext>> future : results) {
            try {
                Consumer consumer = future.get();
                if (consumer != null)
                    consumer.accept(this);
            } catch (ExecutionException e) {
                cancelAll(results);
                throw NopException.adapt(e.getCause());
            } catch (Exception e) {
                cancelAll(results);
                throw NopException.adapt(e);
            }
        }
    }

    @Override
    public void cancelAsyncResults() {
        List<Future<Consumer<? extends IExecutionContext>>> results;
        synchronized (this) {
            results = this.asyncResults;
            this.asyncResults = null;
        }
        this.cancelAll(results);
    }

    void cancelAll(List<Future<Consumer<? extends IExecutionContext>>> futures) {
        if (futures == null)
            return;

        for (Future f : futures) {
            try {
                f.cancel(false);
            } catch (Exception e) {
                LOG.info("nop.err.core.context.cancel-async-result-fail", e);
            }
        }
    }

    @Override
    public void addBeforeComplete(Consumer<Throwable> callback) {
        if (isDone()) {
            throw new IllegalStateException("nop.err.execution-already-completed");
        }

        synchronized (this) {
            if (this.beforeCompletes == null)
                this.beforeCompletes = new ArrayList<>();
            beforeCompletes.add(callback);
        }
    }

    @Override
    public void addAfterComplete(Consumer<Throwable> callback) {
        if (isDone()) {
            throw new IllegalStateException("nop.err.execution-already-completed");
        }

        synchronized (this) {
            if (this.afterCompletes == null)
                this.afterCompletes = new ArrayList<>();
            afterCompletes.add(callback);
        }
    }

    @Override
    public void complete() {
        awaitAsyncResults();
        if (isDone())
            return;

        fireBeforeComplete(null);
        synchronized (this) {
            done = true;
        }
        fireAfterComplete(null);
    }

    @Override
    public void completeExceptionally(Throwable exception) {
        cancelAsyncResults();
        fireBeforeComplete(exception);
        synchronized (this) {
            this.error = exception;
            this.done = true;
        }
        fireAfterComplete(exception);
    }

    @Override
    public void fireBeforeComplete(Throwable e) {
        List<Consumer<Throwable>> callbacks = null;
        synchronized (this) {
            callbacks = this.beforeCompletes;
            if (callbacks != null)
                this.beforeCompletes = null;
        }
        if (callbacks != null) {
            for (Consumer<Throwable> callback : callbacks) {
                if (e != null) {
                    // 如果已经是异常处理阶段，则不抛出异常
                    try {
                        callback.accept(e);
                    } catch (Exception err) {
                        LOG.error("nop.err.core.execution-before-complete-callback-fail", err);
                    }
                } else {
                    callback.accept(null);
                }
            }
        }

        if (e == null) {
            if (error != null)
                throw NopException.adapt(error);

            ErrorBean errorBean = getMostSevereErrorBean();
            if (errorBean != null)
                throw NopRebuildException.rebuild(errorBean);
        }
    }

    void fireAfterComplete(Throwable e) {
        List<Consumer<Throwable>> callbacks = null;
        synchronized (this) {
            callbacks = this.afterCompletes;
            if (callbacks != null)
                this.afterCompletes = null;
        }
        if (callbacks != null) {
            for (Consumer<Throwable> callback : callbacks) {
                try {
                    callback.accept(e);
                } catch (Exception err) {
                    LOG.error("nop.err.core.execution-after-complete-callback-fail", err);
                }
            }
        }
    }

    @Override
    public Throwable getError() {
        return error;
    }

    @Override
    public synchronized void setError(Throwable error) {
        this.error = error;
    }

    @Override
    public List<ErrorBean> getErrorBeans() {
        return errorBeans;
    }

    public void setErrorBeans(List<ErrorBean> errorBeans) {
        this.errorBeans = errorBeans;
    }

    @Override
    public synchronized void addErrorBean(ErrorBean error) {
        if (errorBeans == null)
            errorBeans = new ArrayList<>();
        errorBeans.add(error);
    }

    @Override
    public synchronized ErrorBean getMostSevereErrorBean() {
        if (errorBeans == null || errorBeans.isEmpty())
            return null;

        Collections.sort(errorBeans);
        return errorBeans.get(0);
    }

    @Override
    public synchronized boolean isDone() {
        return done;
    }
}