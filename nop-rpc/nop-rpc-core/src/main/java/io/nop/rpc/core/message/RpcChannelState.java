/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rpc.core.message;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.rpc.core.RpcErrors;
import io.nop.rpc.api.exceptions.RpcTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.nop.rpc.core.RpcErrors.ARG_MAX_WAIT_REQUESTS;
import static io.nop.rpc.core.RpcErrors.ARG_REQ_ID;
import static io.nop.rpc.core.RpcErrors.ARG_RPC_CHANNEL;
import static io.nop.rpc.core.RpcErrors.ERR_RPC_CHANNEL_CLOSED;
import static io.nop.rpc.core.RpcErrors.ERR_RPC_TOO_MANY_INFLIGHT_MESSAGES;

/**
 * 负责管理request和response消息的配对，并执行超时处理逻辑。 request消息发送之前在本对象中注册。每个request都应有一个唯一id。
 */
public class RpcChannelState<S, R> implements IRpcChannelState<S, R> {
    static final Logger LOG = LoggerFactory.getLogger(RpcChannelState.class);

    // 简单的描述性信息，用于异常消息中标识具体是哪个消息channel
    private final String rpcChannel;

    private final IScheduledExecutor timer;

    private int maxWaitingRequests = 10000;

    private Map<Object, CompletableFuture<R>> waitFutures = new ConcurrentHashMap<>();

    private IRpcHook hook = new LogRpcHook();

    private volatile boolean closed;

    public RpcChannelState(String rpcChannel, IScheduledExecutor timer) {
        this.rpcChannel = Guard.notEmpty(rpcChannel, "rpcChannel");
        this.timer = Guard.notNull(timer, "timer");
    }

    public void setHook(IRpcHook hook) {
        this.hook = hook;
    }

    public IRpcHook getHook() {
        return hook;
    }

    public IScheduledExecutor getTimer() {
        return timer;
    }

    public int getMaxWaitingRequests() {
        return maxWaitingRequests;
    }

    public void setMaxWaitingRequests(int maxWaitingRequests) {
        this.maxWaitingRequests = maxWaitingRequests;
    }

    protected NopException newError(ErrorCode errorCode) {
        return new NopException(errorCode).param(ARG_RPC_CHANNEL, rpcChannel);
    }

    public CompletableFuture<R> prepareSend(Object id, S request, long timeout) {
        if (closed) {
            throw newError(ERR_RPC_CHANNEL_CLOSED);
        }

        if (waitFutures.size() >= maxWaitingRequests) {
            throw newError(ERR_RPC_TOO_MANY_INFLIGHT_MESSAGES).param(ARG_MAX_WAIT_REQUESTS, maxWaitingRequests);
        }

        whenSend(id, request, timeout);

        CompletableFuture<R> future = new CompletableFuture<>();
        CompletableFuture<R> oldFuture = waitFutures.put(id, future);

        if (oldFuture != null) {
            LOG.info("nop.async.rpc.cancel-wait-future:messageId={}", id);
            oldFuture.cancel(false);
        }

        future = scheduleTimeout(id, request, future, timeout);

        if (closed) {
            NopException e = newError(ERR_RPC_CHANNEL_CLOSED);
            future.completeExceptionally(e);
            throw e;
        }

        return future;
    }

    private CompletableFuture<R> scheduleTimeout(Object id, S request, CompletableFuture<R> future, long timeout) {
        final Future<?> scheduledFuture = timer.schedule(() -> {
            if (waitFutures.remove(id, future)) {
                future.completeExceptionally(
                        new RpcTimeoutException(RpcErrors.ERR_RPC_TIMEOUT_EXCEPTION).param(ARG_REQ_ID, id));
            }
            return null;
        }, timeout, TimeUnit.MILLISECONDS);

        return future.whenComplete((r, e) -> {
            waitFutures.remove(id, future);
            scheduledFuture.cancel(false);

            if (e != null) {
                if (FutureHelper.isTimeoutException(e)) {
                    whenTimeout(id, request, timeout);
                } else {
                    whenError(id, request, e);
                }
            } else {
                whenReceiveMatched(id, request, r);
            }
        });
    }

    public boolean onReceive(Object id, R response) {
        if (id == null)
            return false;
        CompletableFuture<R> future = waitFutures.remove(id);
        if (future != null) {
            // 这里不需要调用whenReceiveMatched, 在回调函数中会调用
            future.complete(response);
            return true;
        } else {
            if (closed) {
                LOG.info("nop.async.rpc.receive-after-closed:id={}", id);
            }
            whenReceiveUnmatched(id, response);
            return false;
        }
    }

    @Override
    public void onFailure(Object id, Throwable e) {
        if (id == null)
            return;
        CompletableFuture<?> future = waitFutures.remove(id);
        if (future != null) {
            future.completeExceptionally(e);
        }
    }

    public void onChannelOpen() {
        closed = false;
    }

    @Override
    public void onChannelClose(Throwable e) {
        closed = true;

        if (e == null) {
            e = new NopException(ERR_RPC_CHANNEL_CLOSED);
        }
        for (CompletableFuture<?> future : waitFutures.values()) {
            future.completeExceptionally(e);
        }

        waitFutures.clear();
    }

    protected void whenSend(Object id, Object req, long timeout) {
        hook.onSend(id, req, timeout);
    }

    protected void whenReceiveMatched(Object id, Object request, Object resp) {
        hook.onReceiveMatched(id, request, resp);
    }

    protected void whenReceiveUnmatched(Object id, Object resp) {
        hook.onReceiveUnmatched(id, resp);
    }

    protected void whenTimeout(Object id, Object request, long timeout) {
        hook.onTimeout(id, request, timeout);
    }

    protected void whenError(Object id, Object request, Throwable e) {
        hook.onError(id, request, e);
    }
}