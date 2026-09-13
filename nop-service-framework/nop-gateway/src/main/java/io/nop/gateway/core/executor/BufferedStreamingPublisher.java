/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.gateway.core.executor;

import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.beans.ApiRequest;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.gateway.core.interceptor.IGatewayInvocation;
import io.nop.gateway.core.streaming.IStreamingLifecycleListener;
import io.nop.gateway.core.streaming.IStreamingRetryCallback;
import io.nop.gateway.model.GatewayRouteModel;
import io.nop.gateway.model.GatewayStreamingModel;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IServerEventResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * 流式首段缓冲 + 重订阅 publisher（W7 机制 A 落地，plan 2026-08-15-1116-3 Phase 1 GW-A7）。
 *
 * <p>插在 {@code StreamingProcessor.createMappedPublisher} 与 {@code StreamingResponse} 之间
 * （publisher 链内部）：<b>单订阅者</b> publisher。每次 attempt（首次 + 每次重订阅）：
 * <ol>
 *   <li>发起 fetch（{@code httpClient.fetchServerEventFlow}，惰性）+ 元素映射链重跑（mapper，
 *       留在 nop-gateway 内部——回调不实现第二条映射链）→ 订阅映射链。</li>
 *   <li>首段缓冲窗口（N 元素 / T 毫秒，先到者越窗）：窗口内（未向订阅者转发任何数据）元素
 *       缓冲不转发；越窗后透传（下游 demand 门控）。</li>
 *   <li>窗口内上游失败 → 丢弃已缓冲元素 → 经 {@link IStreamingRetryCallback} 重执行回调
 *       （返回新 {@link HttpRequest} = 重订阅；null = 不重试）→ 新链缓冲 → 越窗转发；
 *       窗口外失败 → 原样转发错误（降级终止，不 consult 回调）。</li>
 * </ol>
 *
 * <p><b>生命周期回调</b>（Phase 1 GW-A6/B-2）：每次 attempt fetch 发起调
 * {@code lifecycle.onFetchStarted()}；attempt 终止（onComplete/onError/静默取消经
 * {@link #cancel()}）调 {@code lifecycle.onStreamTerminated(cause)}，exactly-once 去重
 * （onComplete + abort 不双记 -1；F4）。回调/监听器经 {@link IGatewayContext} attribute 注入
 * （B-12）；监听器独立于缓冲开关（缓冲关闭时仍触发计数）。
 *
 * <p><b>委托式 subscription wrapper</b>（B-11）：本 publisher 的 subscription 即下游持有的
 * subscription（经 {@code StreamingResponse.setUpstreamSubscription} 注入，abort 经其生效），
 * {@link #cancel()} 委托给<b>当前活跃</b> attempt 的上游 sub——取代"重注入"表述，无竞态。
 *
 * <p><b>无静默跳过</b>：未配置重执行回调 = 不重订阅，窗口内失败原样转发错误（显式语义）；
 * 缓冲关闭（bufferEnabled=false）= 元素直接透传（零回归直通，非吞错）。
 */
public class BufferedStreamingPublisher implements Flow.Publisher<Object> {

    private static final Logger LOG = LoggerFactory.getLogger(BufferedStreamingPublisher.class);

    private final IHttpClient httpClient;
    private final GatewayRouteModel route;
    private final ApiRequest<?> request;
    private final IGatewayContext context;
    private final GatewayStreamingModel streaming;
    private final IStreamingRetryCallback retryCallback;
    private final IStreamingLifecycleListener lifecycle;
    private final Function<Flow.Publisher<IServerEventResponse>, Flow.Publisher<Object>> mapper;
    private final HttpRequest initialRequest;
    private final int bufferSize;
    private final long bufferTimeMs;
    private final boolean bufferEnabled;

    public BufferedStreamingPublisher(IHttpClient httpClient,
                                      GatewayRouteModel route,
                                      ApiRequest<?> request,
                                      IGatewayContext context,
                                      GatewayStreamingModel streaming,
                                      IStreamingRetryCallback retryCallback,
                                      IStreamingLifecycleListener lifecycle,
                                      Function<Flow.Publisher<IServerEventResponse>, Flow.Publisher<Object>> mapper,
                                      HttpRequest initialRequest) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.route = Objects.requireNonNull(route, "route");
        this.request = Objects.requireNonNull(request, "request");
        this.context = Objects.requireNonNull(context, "context");
        this.streaming = Objects.requireNonNull(streaming, "streaming");
        this.retryCallback = retryCallback;
        this.lifecycle = lifecycle;
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.initialRequest = Objects.requireNonNull(initialRequest, "initialRequest");
        this.bufferSize = streaming.getBufferSize() != null ? streaming.getBufferSize() : 10;
        this.bufferTimeMs = streaming.getBufferTimeMs() != null ? streaming.getBufferTimeMs() : 1000L;
        this.bufferEnabled = Boolean.TRUE.equals(streaming.getBufferEnabled());
        if (bufferSize < 1) {
            throw new IllegalArgumentException("bufferSize must be >= 1: " + bufferSize);
        }
        if (bufferTimeMs < 0) {
            throw new IllegalArgumentException("bufferTimeMs must be >= 0: " + bufferTimeMs);
        }
    }

    @Override
    public void subscribe(Flow.Subscriber<? super Object> subscriber) {
        Objects.requireNonNull(subscriber);
        BufferedSubscription sub = new BufferedSubscription(subscriber);
        subscriber.onSubscribe(sub);
        sub.startFirstAttempt();
    }

    /**
     * 单个 attempt 的运行时状态。buffer/passed/forwarded 由 {@link BufferedSubscription} 的
     * monitor 保护；upSub 为 volatile（cancel 路径跨线程读取）。
     */
    private static final class Attempt {
        final int index; // 1-based：1 = 首次 attempt
        final long windowStart = CoreMetrics.currentTimeMillis();
        final ArrayDeque<Object> buffer = new ArrayDeque<>();
        volatile Flow.Subscription upSub;
        boolean passed;    // guarded by BufferedSubscription monitor
        boolean forwarded; // guarded by BufferedSubscription monitor

        Attempt(int index) {
            this.index = index;
        }
    }

    private final class BufferedSubscription implements Flow.Subscription {

        private final Flow.Subscriber<? super Object> subscriber;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private long demand;        // guarded by this
        private boolean terminated; // guarded by this
        private Attempt attempt;    // guarded by this
        /**
         * 上游已 onComplete 但缓冲区尚有未交付元素（demand 耗尽）时挂起终态：
         * 后续 request() 冲空缓冲后才补发 onComplete（Flow 规范：onComplete 前必须
         * 交付所有已发出元素，尾部数据不得丢弃）。
         */
        private Attempt pendingComplete; // guarded by this
        private int attemptCount;

        BufferedSubscription(Flow.Subscriber<? super Object> subscriber) {
            this.subscriber = subscriber;
        }

        // ======================= Flow.Subscription =======================

        @Override
        public void request(long n) {
            if (n <= 0) {
                throw new IllegalArgumentException("request amount must be positive: " + n);
            }
            if (cancelled.get()) {
                return;
            }
            synchronized (this) {
                demand += n;
                if (demand < 0) {
                    demand = Long.MAX_VALUE;
                }
                Attempt current = attempt;
                if (current != null) {
                    flushBuffer(current);
                }
                // 挂起的终态：冲空缓冲后补发 onComplete（尾部元素不得随 onComplete 丢弃）
                Attempt pc = pendingComplete;
                if (pc != null) {
                    flushAll(pc);
                    if (pc.buffer.isEmpty()) {
                        pendingComplete = null;
                        terminate(null);
                    }
                }
            }
        }

        /**
         * 静默取消（abort/客户端断连）委托当前活跃 attempt 的上游 sub（B-11）；生命周期回调
         * exactly-once（F4：onComplete + abort 不双记 -1；已 terminated 则不再触发）。
         */
        @Override
        public void cancel() {
            if (!cancelled.compareAndSet(false, true)) {
                return;
            }
            Attempt current;
            boolean notifyLifecycle;
            synchronized (this) {
                current = attempt;
                attempt = null;
                pendingComplete = null; // 已取消：挂起的终态不再补发
                notifyLifecycle = !terminated;
                terminated = true;
            }
            if (current != null && current.upSub != null) {
                current.upSub.cancel();
            }
            if (notifyLifecycle && lifecycle != null) {
                lifecycle.onStreamTerminated(null);
            }
        }

        // ======================= attempt 生命周期 =======================

        void startFirstAttempt() {
            synchronized (this) {
                if (cancelled.get()) {
                    return;
                }
                startAttempt(initialRequest);
            }
        }

        /**
         * 发起一次 attempt：生命周期 onFetchStarted → fetch（惰性，映射链订阅期首次 request
         * 同步发起 HTTP）→ 订阅映射链。同步抛异常按流式路径错误语义（窗口内）决策，不吞。
         */
        private void startAttempt(HttpRequest httpRequest) {
            if (cancelled.get()) {
                return;
            }
            Attempt state = new Attempt(++attemptCount);
            attempt = state;
            if (lifecycle != null) {
                lifecycle.onFetchStarted();
            }
            try {
                Flow.Publisher<IServerEventResponse> eventPublisher =
                        httpClient.fetchServerEventFlow(httpRequest, context);
                Flow.Publisher<Object> mapped = mapper.apply(eventPublisher);
                mapped.subscribe(new AttemptSubscriber(state));
            } catch (Throwable t) {
                handleError(state, t);
            }
        }

        // ======================= 缓冲窗口（GW-A7，N/T 先到者越窗） =======================

        /**
         * 窗口内/外判定 = 是否有数据已送达下游（forwarded）。N/T 只决定缓冲何时转为透传：
         * N 满或 T 超时后首个到达元素触发越窗 + 冲刷缓冲。T 超时但无元素到达时失败仍完全透明
         * = 窗口内（与需求 §3.2"未转发任何数据时失败可重订阅"精确对齐）。
         */
        private void handleItem(Attempt state, Object item) {
            if (cancelled.get()) {
                return;
            }
            if (!state.passed) {
                if (bufferEnabled
                        && state.buffer.size() < bufferSize
                        && CoreMetrics.currentTimeMillis() - state.windowStart < bufferTimeMs) {
                    state.buffer.add(item);
                    return;
                }
                state.passed = true;
                flushBuffer(state);
            }
            if (demand > 0) {
                demand--;
                state.forwarded = true;
                subscriber.onNext(item);
            } else {
                state.buffer.add(item);
            }
        }

        private void flushBuffer(Attempt state) {
            if (!state.passed) {
                return;
            }
            while (!state.buffer.isEmpty() && demand > 0 && !cancelled.get()) {
                demand--;
                state.forwarded = true;
                subscriber.onNext(state.buffer.poll());
            }
        }

        /**
         * 终态冲刷（onComplete/断流前）：不受窗口状态门控——窗口语义只延迟转发，不吞数据。
         */
        private void flushAll(Attempt state) {
            while (!state.buffer.isEmpty() && demand > 0 && !cancelled.get()) {
                demand--;
                state.forwarded = true;
                subscriber.onNext(state.buffer.poll());
            }
        }

        // ======================= 终态处理 =======================

        /**
         * 流式错误决策：窗口内（未转发）→ 丢弃已缓冲元素（attempt1 前缀不得进入最终输出）
         * → 重执行回调（null = 不重试，原样转发错误）；窗口外（已转发）→ 不 consult 回调，
         * 原样转发错误（降级终止，需求 §3.2）。
         */
        private void handleError(Attempt state, Throwable throwable) {
            boolean inWindow = !state.forwarded;
            if (inWindow && retryCallback != null) {
                state.buffer.clear();
                HttpRequest newRequest;
                try {
                    newRequest = retryCallback.retry(throwable, route, request, context);
                } catch (Throwable t) {
                    // 回调抛异常 = 重试决策失败：fail-loud（断流报错），不静默。
                    LOG.error("nop.gateway.streaming-retry-callback-failed:routeId={}", route.getId(), t);
                    terminate(t);
                    return;
                }
                if (newRequest != null) {
                    // 重订阅：cancel 旧 subscription（B-11）→ -1 旧 attempt → +1 新 attempt。
                    Flow.Subscription oldSub = state.upSub;
                    if (oldSub != null) {
                        oldSub.cancel();
                    }
                    attempt = null;
                    if (lifecycle != null) {
                        lifecycle.onStreamTerminated(throwable);
                    }
                    startAttempt(newRequest);
                    return;
                }
            }
            terminate(throwable);
        }

        private void handleComplete(Attempt state) {
            flushAll(state);
            if (!state.buffer.isEmpty() && !cancelled.get()) {
                // demand 耗尽且缓冲区还有元素：挂起终态，等后续 request() 冲空缓冲再补发
                // onComplete——缓冲的尾部元素（如携带 finish_reason/usage 的最后 chunk）不得丢弃
                pendingComplete = state;
                return;
            }
            terminate(null);
        }

        private void terminate(Throwable cause) {
            synchronized (this) {
                if (terminated) {
                    return;
                }
                terminated = true;
                attempt = null;
            }
            if (lifecycle != null) {
                lifecycle.onStreamTerminated(cause);
            }
            if (!cancelled.get()) {
                if (cause != null) {
                    subscriber.onError(cause);
                } else {
                    subscriber.onComplete();
                }
            }
        }

        // ======================= 内部订阅者（每个 attempt 一个） =======================

        private final class AttemptSubscriber implements Flow.Subscriber<Object> {
            private final Attempt state;

            AttemptSubscriber(Attempt state) {
                this.state = state;
            }

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                state.upSub = subscription;
                synchronized (BufferedSubscription.this) {
                    if (cancelled.get() || state != attempt) {
                        // 已取消或已被放弃的 attempt：立即取消上游（防 HTTP 连接到 EOF 泄漏）。
                        subscription.cancel();
                        return;
                    }
                }
                // 映射链自驱动（createMappedPublisher 每元素内部 request(1)），缓冲层不追加请求。
            }

            @Override
            public void onNext(Object item) {
                synchronized (BufferedSubscription.this) {
                    if (cancelled.get() || state != attempt) {
                        return;
                    }
                    handleItem(state, item);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                synchronized (BufferedSubscription.this) {
                    if (cancelled.get() || state != attempt) {
                        // 被放弃 attempt 的迟到终态信号：忽略（当前 attempt 已接管）。
                        return;
                    }
                    handleError(state, throwable);
                }
            }

            @Override
            public void onComplete() {
                synchronized (BufferedSubscription.this) {
                    if (cancelled.get() || state != attempt) {
                        return;
                    }
                    handleComplete(state);
                }
            }
        }
    }
}
