/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.netty.handlers;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.exceptions.NopTimeoutException;
import io.nop.api.core.time.CoreMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static io.nop.netty.NopNettyErrors.ERR_CHANNEL_NOT_ACTIVE;
import static io.nop.netty.NopNettyErrors.ERR_TOO_MANY_REQUEST_IN_FLIGHT;

public class RpcMessageHandler extends ChannelDuplexHandler implements IRpcMessageHandler {
    static final Logger LOG = LoggerFactory.getLogger(RpcMessageHandler.class);

    // 对futures集合的读写都在IO线程上，因此不需要进行同步保护
    private final Map<Object, ResponseFuture> futures = new HashMap<>();
    private final int maxInFlightCount;
    private final IRpcMessageAdapter adapter;

    private volatile EventExecutor executor;
    private volatile Channel channel;

    private Future<?> timer;

    public RpcMessageHandler(int maxInFlightCount, IRpcMessageAdapter adapter) {
        this.maxInFlightCount = maxInFlightCount;
        this.adapter = adapter;
    }

    public RpcMessageHandler(int maxInFlightCount) {
        this(maxInFlightCount, DefaultRpcMessageAdapter.INSTANCE);
    }

    static class ResponseFuture {
        long expireTime;
        CompletableFuture<Object> future;

        public ResponseFuture(long expireTime, CompletableFuture<Object> future) {
            this.expireTime = expireTime;
            this.future = future;
        }

        public boolean isDone() {
            return future.isDone();
        }
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        super.channelRegistered(ctx);
        this.executor = ctx.executor();
        this.channel = ctx.channel();
        timer = executor.scheduleWithFixedDelay(this::checkTimeout, 100, 100, TimeUnit.MILLISECONDS);
    }

    private void checkTimeout() {
        long now = CoreMetrics.currentTimeMillis();
        NopException exp = null;
        Iterator<Map.Entry<Object, ResponseFuture>> it = futures.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<Object, ResponseFuture> entry = it.next();
            Object msgId = entry.getKey();
            ResponseFuture future = entry.getValue();

            if (future.expireTime < now) {
                if (future.future.isDone()) {
                    it.remove();
                    continue;
                }
                LOG.debug("nop.netty.expire-rpc-request:msgId={},remoteAddr={},localAddr={}",
                        msgId, channel.remoteAddress(), channel.localAddress());
                if (exp == null)
                    exp = new NopTimeoutException();
                future.future.completeExceptionally(exp);
                it.remove();
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        LOG.info("nop.netty.channel.inactive:remoteAddr={},localAddr={},waitingFutures={}",
                channel.remoteAddress(), channel.localAddress(), futures.size());
        for (ResponseFuture future : futures.values()) {
            future.future.cancel(false);
        }
        futures.clear();
        super.channelInactive(ctx);
        this.channel = null;

        if (this.timer != null) {
            this.timer.cancel(false);
            this.timer = null;
        }
    }

    @Override
    public void send(Object msg, long timeout, CompletableFuture<Object> ret) {
        EventExecutor executor = this.executor;
        if (executor == null) {
            ret.completeExceptionally(new NopException(ERR_CHANNEL_NOT_ACTIVE));
            return;
        }

        ResponseFuture future = new ResponseFuture(CoreMetrics.currentTimeMillis() + timeout, ret);
        if (executor.inEventLoop()) {
            write(future, msg);
        } else {
            // 如果当前线程不是IO线程，则投递到IO线程上执行
            executor.execute(() -> {
                write(future, msg);
            });
        }
    }

    private void write(ResponseFuture future, Object msg) {
        Object msgId = adapter.getRequestId(msg);
        if (future.isDone()) {
            LOG.info("nop.netty.skip-send-done:msgId={}", msgId);
            return;
        }

        int current = futures.size();
        if (current >= maxInFlightCount) {
            LOG.error("nop.err.netty.too-many-request-in-flight:count={},msgId={}", current, msgId);
            future.future.completeExceptionally(
                    new NopException(ERR_TOO_MANY_REQUEST_IN_FLIGHT));
        } else {
            futures.put(msgId, future);
            channel.writeAndFlush(msg);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        Object msgId = adapter.getResponseId(msg);
        if (msgId == null) {
            LOG.info("nop.netty.ignore-response-no-id:msg={}", msg);
            return;
        }

        ResponseFuture future = futures.remove(msgId);
        if (future == null) {
            LOG.info("nop.netty.ignore-response-not-match-id:msgId={},msg={}", msgId, msg);
        } else if (future.isDone()) {
            LOG.info("nop.netty.ignore-response-when-future-is-done:msgId={},msg={}", msgId, msg);
        } else {
            LOG.debug("nop.netty.receive-response:msgId={},msg={}", msgId, msg);
            future.future.complete(msg);
        }
    }
}