/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.context;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static io.nop.api.core.ApiErrors.ARG_SEQ;
import static io.nop.api.core.ApiErrors.ERR_CONTEXT_ALREADY_CLOSED;

public class BaseContext implements IContext {
    static final AtomicLong s_seq = new AtomicLong();

    static final Logger LOG = LoggerFactory.getLogger(BaseContext.class);

    private final long seq = s_seq.incrementAndGet();

    private String traceId;
    private String tenantId;
    private String userId;
    private String userName;
    private String locale;
    private String timezone;
    private long expireTime;
    private String userRefNo;
    private String callIp;
    private Map<String, Object> propagateHeaders;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    protected final ContextTaskQueue taskQueue = new ContextTaskQueue();

    protected volatile boolean closed;

    public BaseContext() {
        LOG.trace("nop.context-new:seq={}", seq);
    }

    public long getSeq() {
        return seq;
    }

    @Override
    public String getUserRefNo() {
        if (userRefNo == null)
            return getUserName();
        return userRefNo;
    }

    public void setUserRefNo(String userRefNo) {
        checkClosed();
        this.userRefNo = userRefNo;
    }

    @Override
    public String getCallIp() {
        return callIp;
    }

    @Override
    public void setCallIp(String callIp) {
        this.callIp = callIp;
    }

    @Override
    public String getTenantId() {
        return tenantId;
    }

    @Override
    public void setTenantId(String tenantId) {
        checkClosed();
        this.tenantId = tenantId;
    }

    @Override
    public String getUserName() {
        return userName;
    }

    @Override
    public void setUserName(String userName) {
        checkClosed();
        this.userName = userName;
    }

    @Override
    public String getUserId() {
        return userId;
    }

    @Override
    public void setUserId(String userId) {
        checkClosed();
        this.userId = userId;
    }

    @Override
    public String getLocale() {
        return locale;
    }

    @Override
    public void setLocale(String locale) {
        checkClosed();
        this.locale = locale;
    }

    @Override
    public Map<String, Object> getPropagateRpcHeaders() {
        return propagateHeaders;
    }

    public void setPropagateRpcHeaders(Map<String, Object> propagateHeaders) {
        checkClosed();
        this.propagateHeaders = propagateHeaders;
    }

    @Override
    public String getTimezone() {
        return timezone;
    }

    @Override
    public void setTimezone(String timezone) {
        checkClosed();
        this.timezone = timezone;
    }

    @Override
    public long getCallExpireTime() {
        return expireTime;
    }

    @Override
    public void setCallExpireTime(long expireTime) {
        checkClosed();
        this.expireTime = expireTime;
    }

    protected ContextTaskQueue getTaskQueue() {
        return taskQueue;
    }

    @JsonAnyGetter
    public Map<String, Object> getAttrs() {
        return attributes;
    }

    @Override
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    @Override
    public void setAttribute(String name, Object value) {
        checkClosed();
        if (value == null) {
            attributes.remove(name);
        } else {
            attributes.put(name, value);
        }
    }

    @Override
    public void removeAttribute(String name) {
        checkClosed();
        attributes.remove(name);
    }

    @Override
    public boolean removeAttribute(String name, Object value) {
        checkClosed();
        return attributes.remove(name, value);
    }

    @Override
    public Object getInternalContext() {
        return null;
    }

    @Override
    public void runOnContext(Runnable task) {
        checkClosed();
        if (!taskQueue.enqueue(task)) {
            IContext oldCtx = BaseContextProvider.contextHolder().get();
            try {
                BaseContextProvider.contextHolder().set(this);
                taskQueue.flush();
            } finally {
                BaseContextProvider.contextHolder().set(oldCtx);
            }
        }
    }

    @Override
    public <T> T executeWithContext(Callable<T> task) throws Exception {
        checkClosed();
        IContext oldCtx = BaseContextProvider.contextHolder().get();
        if (oldCtx == this)
            return task.call();

        try {
            LOG.trace("nop.context-enter:seq={}",seq);
            BaseContextProvider.contextHolder().set(this);
            return task.call();
        } finally {
            LOG.trace("nop.context-leave:seq={}",seq);
            if (oldCtx != null) {
                BaseContextProvider.contextHolder().set(oldCtx);
            } else {
                BaseContextProvider.contextHolder().remove();
            }
        }
    }

    @Override
    public void execute(Runnable task) {
        checkClosed();
        if (isRunningOnContext()) {
            task.run();
            return;
        }
        runOnContext(task);
    }

    @Override
    public boolean isRunningOnContext() {
        return ContextProvider.currentContext() == this;
    }

    @Override
    public <T> CompletionStage<T> executeBlocking(Supplier<?> task, boolean ordered) {
        checkClosed();
        CompletableFuture<T> future = new CompletableFuture<>();
        runOnContext(() -> {
            FutureHelper.completeAfterTask(future, () -> task.get());
        });
        return future;
    }

    @Override
    public <T> T syncGet(CompletionStage<T> future) {
        return taskQueue.syncGet(future);
    }

    public void close() {
        if (this.closed)
            return;

        LOG.trace("nop.context-close:seq={}", seq);

        IContext context = BaseContextProvider.contextHolder().get();
        if (context == this) {
            BaseContextProvider.clear();
        }
        this.closed = true;
        this.attributes.clear();
    }

    @Override
    public String getTraceId() {
        return traceId;
    }

    @Override
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    protected void checkClosed() {
        if (closed)
            throw new NopException(ERR_CONTEXT_ALREADY_CLOSED)
                    .param(ARG_SEQ, seq);
    }
}
