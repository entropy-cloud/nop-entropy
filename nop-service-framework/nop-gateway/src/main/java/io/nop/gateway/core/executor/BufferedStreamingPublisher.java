/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.gateway.core.executor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.nop.api.core.beans.ApiRequest;
import io.nop.commons.metrics.GlobalMeterRegistry;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * 流式首段缓冲 + 重订阅 publisher（W7 机制 A 落地，plan 2026-08-15-1116-3 Phase 1 GW-A7；
 * plan 2509 R2-06 起迁移为共享有界窗口缓冲组件 {@link BoundedWindowBuffer} 的 2.3 侧宿主）。
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
 * <p><b>demand 门控授信</b>（裁定⑤(b) 2.3 半边）：映射链 request(1) 自驱动改为经缓冲层
 * demand 检查——下游 request(n) 授信，映射产出消耗授信；demand==0 时不拉取上游，
 * SSE 反压自然传导（越窗缓冲上界退为第二道防线）。授信插桩 = 上游订阅包装
 * （{@link GatedUpstreamPublisher}），映射链无感知。
 *
 * <p><b>越窗上界 fail-loud</b>（裁定⑤(a)）：越窗缓冲硬上界与窗口期同界（bufferSize），
 * 溢出 = {@link StreamingBufferOverflowException} 类型化错误终态（不可重试：不进
 * retryCallback、零重订阅、零 recordFailure 面）+ 上游 cancel 回收。
 *
 * <p><b>出锁投递</b>（裁定⑥ C2-009 2.3 半边）：锁内仅状态机变更（buffer/demand/forwarded/
 * pendingComplete 记账 + 元素摘取入信号队列），全部下游投递（onNext/onComplete/onError）
 * 与重试决策（retryCallback.retry）、attempt 发起（startAttempt）在 monitor 外执行；
 * 信号序由单一 drain 守卫串行化（投递六点：缓冲冲刷 ×3 / request 补发 onComplete /
 * 错误终态 / 完成终态）——同元素不乱序、终态最后。错误终态路径补上游 cancel
 * （C3-003 两态：与 {@code StreamingProcessor} :164-166 落点同闭合）。
 *
 * <p><b>生命周期回调</b>（Phase 1 GW-A6/B-2）：每次 attempt fetch 发起调
 * {@code lifecycle.onFetchStarted()}；attempt 终止（onComplete/onError/静默取消经
 * {@link #cancel()}）调 {@code lifecycle.onStreamTerminated(cause)}，exactly-once 去重。
 *
 * <p><b>无静默跳过</b>：未配置重执行回调 = 不重订阅，窗口内失败原样转发错误（显式语义）；
 * 缓冲关闭（bufferEnabled=false）= 直通档（元素直接透传，零回归直通，非吞错）。
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

    /** 当前活跃订阅（C3-007 降级终态交付的委托目标；subscribe 时置位）。 */
    private volatile BufferedSubscription activeSubscription;

    /**
     * C3-007 宿主半边（plan 2509 裁定⑦(a)）：降级响应的显式降级终态交付——降级 wrapper
     * 不经元素通路（不占 demand、不进缓冲），窗口内清缓冲后经出锁 drain 直接以
     * 「onNext + onComplete」终态序列交付。直通形态不使用本 API（保持既有行为零回归）。
     */
    public void deliverDegradedTerminal(Object degraded) {
        BufferedSubscription sub = this.activeSubscription;
        if (sub != null)
            sub.deliverDegradedTerminal(degraded);
    }

    @Override
    public void subscribe(Flow.Subscriber<? super Object> subscriber) {
        Objects.requireNonNull(subscriber);
        BufferedSubscription sub = new BufferedSubscription(subscriber);
        this.activeSubscription = sub;
        subscriber.onSubscribe(sub);
        sub.startFirstAttempt();
    }

    /**
     * 单个 attempt 的运行时状态。可共享状态（缓冲/窗口/demand/pendingComplete/cancel）
     * 由组件 {@link BoundedWindowBuffer} 承载；attempt 私有状态仅上游订阅与授信挂起计数。
     * upSub 为 volatile（cancel 路径跨线程读取）。
     */
    private static final class Attempt {
        final int index; // 1-based：1 = 首次 attempt
        volatile Flow.Subscription upSub;
        int pendingUpstreamRequests; // demand==0 时被门控挂起的映射链 request(1) 数

        Attempt(int index) {
            this.index = index;
        }
    }

    /**
     * 出锁投递信号。锁内入队（入队序 = 投递序），drain 守卫串行消费——终态信号必位于
     * 在途元素信号之后，顺序 drain 天然终态最后。
     */
    private static final class Signal {
        static final int ITEM = 0;
        static final int COMPLETE = 1;
        static final int ERROR = 2;

        final int kind;
        final Attempt attempt;
        final Object item;
        final Throwable cause;

        private Signal(int kind, Attempt attempt, Object item, Throwable cause) {
            this.kind = kind;
            this.attempt = attempt;
            this.item = item;
            this.cause = cause;
        }

        static Signal item(Attempt attempt, Object item) {
            return new Signal(ITEM, attempt, item, null);
        }

        static Signal complete(Attempt attempt) {
            return new Signal(COMPLETE, attempt, null, null);
        }

        static Signal error(Attempt attempt, Throwable cause) {
            return new Signal(ERROR, attempt, null, cause);
        }
    }

    private final class BufferedSubscription implements Flow.Subscription {

        private final Flow.Subscriber<? super Object> subscriber;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        // 共享状态核：buffer/窗口/passed/forwarded/demand/pendingComplete/cancel 由组件承载
        private final BoundedWindowBuffer<Object> buffer;

        private boolean terminated; // guarded by this
        private Attempt attempt;    // guarded by this
        private int attemptCount;
        private volatile Counter overflowCounter; // 溢出计数指标（懒创建，routeId 维度）

        private final ArrayDeque<Signal> signalQueue = new ArrayDeque<>(); // guarded by this
        private boolean draining; // guarded by this：单一 drain 守卫（投递串行化）

        BufferedSubscription(Flow.Subscriber<? super Object> subscriber) {
            this.subscriber = subscriber;
            this.buffer = new BoundedWindowBuffer<>(bufferSize, !bufferEnabled, bufferTimeMs);
        }

        // ======================= Flow.Subscription =======================

        /**
         * 下游 request(n) = 授信：demand 累加 → 冲刷缓冲/挂起终态 → 补发被门控挂起的
         * 上游 request(1)（demand==0 前期间不拉取）→ 出锁投递（drain）。
         */
        @Override
        public void request(long n) {
            if (n <= 0) {
                throw new IllegalArgumentException("request amount must be positive: " + n);
            }
            if (cancelled.get()) {
                return;
            }
            List<Flow.Subscription> toGrant = null;
            synchronized (this) {
                buffer.addDemand(n);
                enqueueFlushLocked();
                if (buffer.isPendingComplete()) {
                    enqueueFlushAllLocked();
                    if (buffer.isEmpty()) {
                        // 挂起的终态：冲空缓冲后补发 onComplete（尾部元素不得随 onComplete 丢弃）
                        buffer.setPendingComplete(false);
                        signalQueue.add(Signal.complete(attempt));
                    }
                }
                // 授信记账：demand 可用则补发被门控挂起的映射链 request(1)
                if (attempt != null && buffer.hasDemand()) {
                    while (attempt.pendingUpstreamRequests > 0 && buffer.hasDemand()) {
                        attempt.pendingUpstreamRequests--;
                        if (toGrant == null) {
                            toGrant = new ArrayList<>(2);
                        }
                        toGrant.add(attempt.upSub);
                    }
                }
            }
            if (toGrant != null) {
                for (Flow.Subscription up : toGrant) {
                    if (up != null) {
                        up.request(1);
                    }
                }
            }
            drain();
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
                buffer.clearForCancel(); // 已取消：挂起终态不再补发，缓冲/demand 全清理
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
            }
            // monitor 外发起（裁定⑥：attempt 发起出锁——同步发射型上游在订阅链内即触发
            // 信号回路，首 attempt 不得把 monitor 持有跨过整条链）
            startAttempt(initialRequest);
        }

        /**
         * 发起一次 attempt：生命周期 onFetchStarted → fetch（惰性，映射链订阅期首次 request
         * 经授信门控）→ 订阅映射链。同步抛异常按流式路径错误语义（窗口内）决策，不吞。
         * 调用方保证在 monitor 外（裁定⑥：attempt 发起出锁）。
         */
        private void startAttempt(HttpRequest httpRequest) {
            if (cancelled.get()) {
                return;
            }
            Attempt state = new Attempt(++attemptCount);
            synchronized (this) {
                attempt = state;
                buffer.beginWindow();
            }
            if (lifecycle != null) {
                lifecycle.onFetchStarted();
            }
            try {
                Flow.Publisher<IServerEventResponse> eventPublisher =
                        httpClient.fetchServerEventFlow(httpRequest, context);
                // 授信插桩：映射链 request(1) 经门控（demand==0 不拉取上游）
                Flow.Publisher<Object> mapped = mapper.apply(
                        new GatedUpstreamPublisher(state, eventPublisher));
                mapped.subscribe(new AttemptSubscriber(state));
            } catch (Throwable t) {
                synchronized (this) {
                    if (state == attempt && !terminated && !cancelled.get()) {
                        signalQueue.add(Signal.error(state, t));
                    }
                }
                drain();
            }
        }

        /**
         * C3-007 降级终态交付（外层 {@link #deliverDegradedTerminal(Object)} 委托目标）：
         * 清缓冲 + 挂起终态失效 + 出锁 drain 直投「degraded onNext + onComplete」终态序列
         * （不占 demand、不进缓冲；attempt-identity 经信号携带的 attempt 引用由投递段复核）。
         * 调用方 = 外层公共 API，非上游回调语境。
         */
        void deliverDegradedTerminal(Object degraded) {
            synchronized (this) {
                if (cancelled.get()) {
                    return;
                }
                buffer.clear();
                buffer.setPendingComplete(false);
                signalQueue.add(Signal.item(attempt, degraded));
                signalQueue.add(Signal.complete(attempt));
            }
            drain();
        }

        // ======================= 锁内状态机（裁定⑥：仅记账，不出信号） =======================

        /**
         * 越窗判定 + 元素接收。返回 false = 元素已被缓冲/记账完毕，无需投递信号；
         * 溢出 = fail-loud 错误信号入队（不可重试终态）。调用方持锁。
         */
        private void handleItemLocked(Attempt state, Object item) {
            if (!buffer.isPassed()) {
                if (buffer.shouldBufferInWindow()) {
                    buffer.add(item);
                    return;
                }
                buffer.markPassed();
                enqueueFlushLocked();
            }
            if (buffer.hasDemand()) {
                buffer.decDemand();
                buffer.markForwarded();
                signalQueue.add(Signal.item(state, item));
            } else if (!buffer.add(item)) {
                // 越窗上界（与窗口期同界）：溢出 = 类型化不可重试终态，第二道防线
                recordOverflow(buffer.size());
                signalQueue.add(Signal.error(state, new StreamingBufferOverflowException(
                        "streaming buffer overflow: backlog " + buffer.size()
                                + " items exceeds bufferSize " + buffer.getCapacity()
                                + " (slow downstream, fail loud)")));
            }
        }

        /**
         * 缓冲冲刷（passed 门控）：锁内摘元素 + demand 递减入信号队列。调用方持锁。
         */
        private void enqueueFlushLocked() {
            if (!buffer.isPassed()) {
                return;
            }
            while (!buffer.isEmpty() && buffer.hasDemand() && !cancelled.get()) {
                buffer.decDemand();
                buffer.markForwarded();
                signalQueue.add(Signal.item(attempt, buffer.poll()));
            }
        }

        /**
         * 终态冲刷：不受窗口状态门控——窗口语义只延迟转发，不吞数据。调用方持锁。
         */
        private void enqueueFlushAllLocked() {
            while (!buffer.isEmpty() && buffer.hasDemand() && !cancelled.get()) {
                buffer.decDemand();
                buffer.markForwarded();
                signalQueue.add(Signal.item(attempt, buffer.poll()));
            }
        }

        private void recordOverflow(int depth) {
            LOG.warn("nop.gateway.streaming-buffer-overflow:routeId={},backlogDepth={},bufferSize={}",
                    route.getId(), depth, bufferSize);
            Counter counter = overflowCounter;
            if (counter == null) {
                MeterRegistry registry = GlobalMeterRegistry.instance();
                if (registry != null) {
                    counter = Counter.builder("nop.gateway.streaming.buffer.overflow")
                            .tag("routeId", route.getId() == null ? "unknown" : route.getId())
                            .register(registry);
                    overflowCounter = counter;
                }
            }
            if (counter != null) {
                counter.increment();
            }
        }

        // ======================= 出锁投递（drain-loop，裁定⑥钉死形态） =======================

        /**
         * while 循环：持锁取快照 → 出锁投递 → 重入锁重查队列，空则清投递标志退出、非空继续
         * 消费。投递期间新入队信号由下一轮消费（无滞留、无通知原语）；同元素不乱序、终态最后。
         */
        private void drain() {
            synchronized (this) {
                if (draining) {
                    return; // 外层 drain 循环将消费本轮新入队信号
                }
                draining = true;
            }
            try {
                while (true) {
                    List<Signal> snapshot;
                    synchronized (this) {
                        if (signalQueue.isEmpty() || cancelled.get()) {
                            return;
                        }
                        snapshot = new ArrayList<>(signalQueue);
                        signalQueue.clear();
                    }
                    for (Signal signal : snapshot) {
                        if (cancelled.get()) {
                            return;
                        }
                        deliver(signal);
                    }
                }
            } finally {
                synchronized (this) {
                    draining = false;
                }
            }
        }

        /**
         * 单信号出锁投递。identity 复检失败仅丢弃元素信号（放弃 attempt 的缓冲永不交付，
         * 已 forwarded 的残留丢弃 + WARN 留痕）；终态信号为流级，不受 attempt identity 门控。
         */
        private void deliver(Signal signal) {
            if (signal.kind == Signal.ERROR) {
                handleError(signal.attempt, signal.cause);
                return;
            }
            if (signal.kind == Signal.ITEM) {
                synchronized (this) {
                    if (signal.attempt != attempt) {
                        if (signal.attempt != null && buffer.isForwarded()) {
                            LOG.warn("nop.gateway.streaming-dropped-orphan-item:attemptIndex={},depth={}",
                                    signal.attempt.index, buffer.size());
                        }
                        return;
                    }
                }
                subscriber.onNext(signal.item);
                return;
            }
            // COMPLETE 终态（最后序由入队序 + 顺序 drain 保证）
            terminate(signal.attempt, null);
        }

        // ======================= 终态处理（monitor 外） =======================

        /**
         * 流式错误决策：窗口内（未转发）→ 丢弃已缓冲元素（attempt1 前缀不得进入最终输出）
         * → 重执行回调（null = 不重试，原样转发错误）；窗口外（已转发）→ 不 consult 回调，
         * 原样转发错误（降级终止）。溢出错误 = 不可重试终态，直接断流。
         * retryCallback.retry 与 startAttempt 均在 monitor 外（裁定⑥ C2-009）。
         */
        private void handleError(Attempt state, Throwable throwable) {
            boolean retryable;
            synchronized (this) {
                if (terminated || cancelled.get() || state != attempt) {
                    return; // 已终止/放弃 attempt 的迟到错误：忽略
                }
                retryable = !buffer.isForwarded() && retryCallback != null
                        && !(throwable instanceof StreamingBufferOverflowException);
                if (retryable) {
                    buffer.clear(); // attempt1 已缓冲前缀不得进入最终输出
                }
            }
            if (!retryable) {
                terminate(state, throwable);
                return;
            }
            HttpRequest newRequest;
            try {
                newRequest = retryCallback.retry(throwable, route, request, context);
            } catch (Throwable t) {
                // 回调抛异常 = 重试决策失败：fail-loud（断流报错），不静默。
                LOG.error("nop.gateway.streaming-retry-callback-failed:routeId={}", route.getId(), t);
                terminate(state, t);
                return;
            }
            if (newRequest == null) {
                // 回调拒绝重试 = 原样转发错误（无静默跳过）
                terminate(state, throwable);
                return;
            }
            Flow.Subscription oldSub;
            synchronized (this) {
                if (terminated || cancelled.get() || state != attempt) {
                    return;
                }
                oldSub = state.upSub;
                attempt = null;
            }
            if (oldSub != null) {
                oldSub.cancel(); // 重订阅：cancel 旧 subscription（B-11）
            }
            if (lifecycle != null) {
                lifecycle.onStreamTerminated(throwable);
            }
            startAttempt(newRequest);
        }

        /**
         * 终态交付（完成/错误）。错误终态补上游 cancel 回收（C3-003 两态：修复 SSE 连接
         * 滞留至 EOF）。monitor 外执行（全部投递点出锁）。
         */
        private void terminate(Attempt state, Throwable cause) {
            synchronized (this) {
                if (terminated) {
                    return;
                }
                terminated = true;
                attempt = null;
            }
            if (cause != null && state != null && state.upSub != null) {
                state.upSub.cancel();
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

        // ======================= 授信门控（裁定⑤(b) 2.3 半边） =======================

        /**
         * 上游订阅包装：映射链 request(1) 前置缓冲层 demand 检查——demand==0 挂起
         * （由下游 request(n) 补发），demand 可用即透传。demand==0 时上游 SSE 自然停滞，
         * 越窗缓冲上界退为第二道防线。
         */
        private final class GatedUpstreamPublisher implements Flow.Publisher<IServerEventResponse> {
            private final Attempt state;
            private final Flow.Publisher<IServerEventResponse> delegate;

            GatedUpstreamPublisher(Attempt state, Flow.Publisher<IServerEventResponse> delegate) {
                this.state = state;
                this.delegate = delegate;
            }

            @Override
            public void subscribe(Flow.Subscriber<? super IServerEventResponse> downstream) {
                delegate.subscribe(new Flow.Subscriber<>() {
                    @Override
                    public void onSubscribe(Flow.Subscription up) {
                        state.upSub = up;
                        boolean abandoned;
                        synchronized (BufferedSubscription.this) {
                            abandoned = cancelled.get() || state != attempt;
                        }
                        if (abandoned) {
                            // 已取消或已被放弃的 attempt：立即取消上游（防 HTTP 连接到 EOF 泄漏）。
                            up.cancel();
                        }
                        downstream.onSubscribe(new GatedSubscription(state, up));
                    }

                    @Override
                    public void onNext(IServerEventResponse item) {
                        downstream.onNext(item);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        downstream.onError(throwable);
                    }

                    @Override
                    public void onComplete() {
                        downstream.onComplete();
                    }
                });
            }
        }

        private final class GatedSubscription implements Flow.Subscription {
            private final Attempt state;
            private final Flow.Subscription up;

            GatedSubscription(Attempt state, Flow.Subscription up) {
                this.state = state;
                this.up = up;
            }

            @Override
            public void request(long n) {
                boolean go;
                synchronized (BufferedSubscription.this) {
                        if (cancelled.get() || state != attempt) {
                        return; // 取消/放弃 attempt：不再授信拉取
                    }
                    if (buffer.hasDemand()) {
                        go = true;
                    } else {
                        state.pendingUpstreamRequests++;
                        go = false;
                    }
                }
                if (go) {
                    up.request(n);
                }
            }

            @Override
            public void cancel() {
                up.cancel();
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
                // 授信与 identity 守卫在 GatedUpstreamPublisher 一层承载（映射链无感知），
                // 此处不追加请求。
            }

            @Override
            public void onNext(Object item) {
                synchronized (BufferedSubscription.this) {
                    if (cancelled.get() || state != attempt) {
                        return;
                    }
                    handleItemLocked(state, item);
                }
                drain();
            }

            @Override
            public void onError(Throwable throwable) {
                synchronized (BufferedSubscription.this) {
                    if (cancelled.get() || state != attempt) {
                        // 被放弃 attempt 的迟到终态信号：忽略（当前 attempt 已接管）。
                        return;
                    }
                    signalQueue.add(Signal.error(state, throwable));
                }
                drain();
            }

            @Override
            public void onComplete() {
                synchronized (BufferedSubscription.this) {
                    if (cancelled.get() || state != attempt) {
                        return;
                    }
                    enqueueFlushAllLocked();
                    if (!buffer.isEmpty() && !cancelled.get()) {
                        // demand 耗尽且缓冲区还有元素：挂起终态，等后续 request() 冲空缓冲再补发
                        // onComplete——缓冲的尾部元素（如携带 finish_reason/usage 的最后 chunk）
                        // 不得丢弃（C3-001 正确先例：pendingComplete）
                        buffer.setPendingComplete(true);
                        return;
                    }
                    signalQueue.add(Signal.complete(state));
                }
                drain();
            }
        }
    }
}
