/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.context;

import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.exceptions.NopRebuildException;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.core.lang.eval.EvalScopeImpl;
import io.nop.core.lang.eval.IEvalScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ExecutionContextImpl extends Cancellable implements IExecutionContext {
    private static final Logger LOG = LoggerFactory.getLogger(ExecutionContextImpl.class);

    private final Map<String, Object> attributes;
    private IEvalScope scope;

    private List<Runnable> beforeCompletes;
    private List<Consumer<Throwable>> afterCompletes;
    private boolean done;
    private Throwable error;
    private List<ErrorBean> errorBeans;
    private String executionId;

    public ExecutionContextImpl(Map<String, Object> attributes) {
        this.attributes = attributes == null ? new ConcurrentHashMap<>() : attributes;
        scope = new EvalScopeImpl(getAttributes());
    }

    public ExecutionContextImpl() {
        this(new ConcurrentHashMap<>());
    }

    protected ExecutionContextImpl(IEvalScope parentScope) {
        this.attributes = new ConcurrentHashMap<>();
        this.scope = parentScope != null ? parentScope.newChildScope(attributes) : new EvalScopeImpl(attributes);
    }

    public String getExecutionId() {
        return executionId;
    }

    @Override
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
    public void onBeforeComplete(Runnable callback) {
        synchronized (this) {
            if (isDone()) {
                throw new IllegalStateException("nop.err.execution-already-completed");
            }

            if (this.beforeCompletes == null)
                this.beforeCompletes = new ArrayList<>();
            beforeCompletes.add(callback);
        }
    }

    @Override
    public void onAfterComplete(Consumer<Throwable> callback) {
        synchronized (this) {
            if (isDone()) {
                throw new IllegalStateException("nop.err.execution-already-completed");
            }

            if (this.afterCompletes == null)
                this.afterCompletes = new ArrayList<>();
            afterCompletes.add(callback);
        }
    }

    @Override
    public void complete() {
        if (isDone())
            return;

        fireBeforeComplete();
        synchronized (this) {
            done = true;
        }
        fireAfterComplete(null);
    }

    @Override
    public void completeExceptionally(Throwable exception) {
        synchronized (this) {
            this.error = exception;
            this.done = true;
        }
        fireAfterComplete(exception);
    }

    @Override
    public void fireBeforeComplete() {
        List<Runnable> callbacks;
        synchronized (this) {
            callbacks = this.beforeCompletes;
            if (callbacks != null)
                this.beforeCompletes = null;
        }
        if (callbacks != null) {
            for (Runnable callback : callbacks) {
                callback.run();
            }
        }

        if (error != null)
            throw NopException.adapt(error);

        ErrorBean errorBean = getMostSevereErrorBean();
        if (errorBean != null)
            throw NopRebuildException.rebuild(errorBean);
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
    public synchronized Throwable getError() {
        return error;
    }

    @Override
    public synchronized void setError(Throwable error) {
        this.error = error;
    }

    @Override
    public synchronized List<ErrorBean> getErrorBeans() {
        return errorBeans;
    }

    public synchronized void setErrorBeans(List<ErrorBean> errorBeans) {
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