package io.nop.ai.gateway.failover;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ErrorClassification;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.core.routing.ModelClassCandidate;
import io.nop.ai.core.routing.ModelClassRouter;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancelToken;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 本地形态流式 failover 的 {@link Flow.Publisher} 实现（plan 2026-08-15-1116-2 LOCAL-03，
 * W1 spike SPIKE-02 输入）。
 *
 * <p><b>语义</b>：单订阅者 publisher。每次 attempt（首次 + 每次重订阅）：
 * <ol>
 *   <li>选择候选（主动路径）→ 下沉 {@code ChatOptions} 四字段 → 并发计数 +1（delegate
 *       {@code callStream} 调用时刻，W1 spike）→ 委托调用（per-attempt 独立 {@code ICancelToken}）。</li>
 *   <li>订阅返回的 publisher，包首段缓冲窗口（N 元素 / T 毫秒，Phase 1 D1）：窗口内（未转发任何
 *       数据）失败 → 分类（流式路径恢复响应级分类）→ 熔断记账 → 重选（被动路径，含 provider 链
 *       扩展）→ 重试预算递减 → 重订阅（新 {@code ChatOptions} + 新窗口 + 新 per-attempt token）；
 *       已缓冲元素丢弃（Major-1，attempt1 内容不得进入最终输出）。</li>
 *   <li>窗口外失败 → 不切换，断流报错（需求 §3.2）。成功（onComplete）→
 *       {@code recordSuccess(末次 attempt 候选)}（Minor-2：探活成功 → CLOSED 恢复）。</li>
 * </ol>
 *
 * <p><b>取消契约</b>（spike 实证）：取消顺序 = 先 cancel 适配器对 delegate publisher 的
 * <b>内部订阅</b>（唤醒阻塞 submit）→ 再 cancel 该 attempt 的 token（断 HTTP）；窗口期保持
 * demand（request(MAX)）。调用方取消（token 或输出订阅 cancel）→ 传播到<b>当前</b> attempt
 * token，两路径都必须完整 teardown（含并发计数释放）。每次 attempt 前检查调用方
 * {@code isCancelled()}（Minor-5：已取消 → 干净终态，不发起新 fetch）。
 *
 * <p><b>并发计数</b>（D7/M-2/M-3/M-4）：+1 挂钩 delegate {@code callStream} 调用时刻；-1 挂钩
 * attempt 终止（onComplete/onError）或取消完成，键 = 被终止 attempt 的候选下沉值（旧
 * provider/accountKey）；per-attempt 一次释放守卫（AtomicBoolean）防误下溢。
 *
 * <p><b>无静默跳过</b>：预算耗尽 / NON_TRANSIENT / 窗口外失败 → 显式断流报错（onError）；
 * 重订阅同步抛异常（checkRateLimit）→ 按传输异常级分类决策，不吞。
 */
final class FailoverStreamFlow implements Flow.Publisher<ChatStreamChunk> {

    private final ChatServiceFailoverAdapter adapter;
    private final ChatRequest request;
    private final ModelClassRouter router;
    private final ICancelToken callerToken;
    private final Object subscribeLock = new Object();
    private StreamSubscription subscription;

    FailoverStreamFlow(ChatServiceFailoverAdapter adapter, ChatRequest request,
                       ModelClassRouter router, ICancelToken callerToken) {
        this.adapter = adapter;
        this.request = request;
        this.router = router;
        this.callerToken = callerToken;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ChatStreamChunk> subscriber) {
        Objects.requireNonNull(subscriber);
        StreamSubscription sub;
        synchronized (subscribeLock) {
            if (subscription != null) {
                // 单订阅者契约：第二个订阅者显式失败（不静默吞）。
                subscriber.onSubscribe(SubscriptionStub.INSTANCE);
                subscriber.onError(new IllegalStateException("FailoverStreamFlow supports a single subscriber"));
                return;
            }
            sub = new StreamSubscription(subscriber);
            subscription = sub;
        }
        subscriber.onSubscribe(sub);
        sub.start();
    }

    /**
     * 每 attempt 独立取消令牌（M-1 修复）：适配器为每个 attempt 新建 token（旧 attempt 的 token
     * 由适配器主动 cancel 以断旧 HTTP fetch）；调用方 token 取消 → 传播到当前 attempt token；
     * 绝不可直接取消调用方 token（置其 isCancelled()==true 会终止整个操作、后续 attempt 的
     * fetch 取消回调挂不上）。
     */
    static final class AttemptCancelToken implements ICancelToken {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final List<Consumer<String>> tasks = new CopyOnWriteArrayList<>();
        private volatile String reason;

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }

        @Override
        public String getCancelReason() {
            return reason;
        }

        @Override
        public void appendOnCancel(Consumer<String> task) {
            if (cancelled.get()) {
                task.accept(reason);
            } else {
                tasks.add(task);
            }
        }

        @Override
        public void removeOnCancel(Consumer<String> task) {
            tasks.remove(task);
        }

        void cancel(String reason) {
            if (cancelled.compareAndSet(false, true)) {
                this.reason = reason;
                for (Consumer<String> task : tasks) {
                    task.accept(reason);
                }
            }
        }
    }

    private enum SubscriptionStub implements Flow.Subscription {
        INSTANCE;

        @Override
        public void request(long n) {
        }

        @Override
        public void cancel() {
        }
    }

    /**
     * 单个 attempt 的运行时状态。缓冲区/window 状态由 {@link StreamSubscription} 的 monitor
     * 保护；{@code released} 为 per-attempt 一次释放守卫（Minor-3，AtomicBoolean 无锁）。
     */
    private static final class AttemptState {
        final int index; // 1-based：1 = 首次 attempt
        final ModelClassCandidate candidate;
        final ChatOptions sunkOptions;
        final AttemptCancelToken token;
        final AtomicBoolean released = new AtomicBoolean();
        final ArrayDeque<ChatStreamChunk> buffer = new ArrayDeque<>();
        final long windowStart = System.currentTimeMillis();
        volatile Flow.Subscription upSub;
        boolean passed;   // guarded by StreamSubscription monitor
        boolean forwarded; // guarded by StreamSubscription monitor

        AttemptState(int index, ModelClassCandidate candidate, ChatOptions sunkOptions,
                     AttemptCancelToken token) {
            this.index = index;
            this.candidate = candidate;
            this.sunkOptions = sunkOptions;
            this.token = token;
        }
    }

    private final class StreamSubscription implements Flow.Subscription {

        private final Flow.Subscriber<? super ChatStreamChunk> subscriber;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private long demand; // guarded by this
        private volatile AttemptState attempt;
        private volatile boolean callerListenerRegistered;
        private int attemptsStarted; // 仅在实际创建 attempt（发起 fetch）时递增

        StreamSubscription(Flow.Subscriber<? super ChatStreamChunk> subscriber) {
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
                AttemptState current = attempt;
                if (current != null) {
                    flushBuffer(current);
                }
            }
        }

        @Override
        public void cancel() {
            // 输出侧取消可观测（Minor-10）：触发内部 attempt teardown + 计数释放，不依赖
            // SubmissionPublisher 静默移除语义。取消后不再发送任何信号（与 ChatServiceImpl 一致）。
            if (cancelled.compareAndSet(false, true)) {
                teardownCurrentAttempt("stream subscription cancelled");
            }
        }

        // ======================= attempt 生命周期 =======================

        void start() {
            if (cancelled.get()) {
                return;
            }
            if (callerToken != null && callerToken.isCancelled()) {
                // Minor-5：调用方已取消 → 干净终态（onError(CancellationException)，不发起新 fetch、
                // 不记账、不耗预算；订阅者必须收到终态信号——ReactiveStreams 契约）。
                deliverError(new CancellationException(callerToken.getCancelReason() != null
                        ? callerToken.getCancelReason() : "caller token already cancelled"));
                return;
            }
            registerCallerListener();
            startAttemptWithOptions(null, null);
        }

        /**
         * 启动一个 attempt。{@code fixedCandidate != null} = CACHE_STATE_LOST 原地重发
         * （同候选同 options，不重选）；否则经 router 选择（主动/被动按既有 attempt 数）。
         */
        private void startAttemptWithOptions(ModelClassCandidate fixedCandidate, ChatOptions fixedOptions) {
            if (cancelled.get()) {
                return;
            }
            if (callerToken != null && callerToken.isCancelled()) {
                // Minor-5：每次 attempt 前检查调用方取消 → 干净终态（onError(CancellationException)，
                // 不发起新 fetch；此前 attempt 的计数已在终止路径释放）。
                deliverError(new CancellationException(callerToken.getCancelReason() != null
                        ? callerToken.getCancelReason() : "caller token cancelled before attempt"));
                return;
            }
            ModelClassCandidate candidate;
            ChatOptions sunk;
            if (fixedCandidate != null) {
                candidate = fixedCandidate;
                sunk = fixedOptions;
            } else {
                try {
                    boolean active = attempt == null;
                    candidate = adapter.selectNextWithProbe(router, active, request);
                } catch (NopException e) {
                    // 全池饱和 fail-loud（主动路径不记熔断、不耗预算）。
                    deliverError(e);
                    return;
                }
                sunk = router.toChatOptions(request.getOptions(), candidate);
            }
            // 并发计数 +1（delegate callStream 调用时刻）+ acquire 后复查（M-6a）：超限 →
            // release + 视为饱和跳过 → 重选。
            int count = adapter.getRegistry().acquire(candidate.getProvider(), candidate.getAccountKey());
            IFailoverMetrics metrics = adapter.getMetrics();
            if (metrics != null) {
                metrics.onConcurrencyAcquire(candidate.getProvider(), candidate.getAccountKey());
            }
            if (adapter.isOverConcurrencyLimit(candidate, count)) {
                adapter.getRegistry().release(candidate.getProvider(), candidate.getAccountKey());
                if (metrics != null) {
                    metrics.onConcurrencyRelease(candidate.getProvider(), candidate.getAccountKey());
                }
                startAttemptWithOptions(null, null);
                return;
            }
            attemptsStarted++;
            if (attemptsStarted >= 2) {
                // 第二次及以后的 attempt = 流式重订阅（含 CACHE_STATE_LOST 同候选原地重发）；
                // 换候选重订阅（fixedCandidate == null，经被动路径重选）同时计入切换。
                if (metrics != null) {
                    metrics.onResubscribe(candidate.getProvider(), candidate.getModel(), candidate.getAccountKey());
                    if (fixedCandidate == null) {
                        metrics.onSwitchAttempt(candidate.getProvider(), candidate.getModel(), candidate.getAccountKey());
                    }
                }
            }
            AttemptState state = new AttemptState(attemptsStarted, candidate, sunk, new AttemptCancelToken());
            attempt = state;
            try {
                Flow.Publisher<ChatStreamChunk> publisher =
                        adapter.getDelegate().callStream(attemptRequest(sunk), state.token);
                publisher.subscribe(new AttemptSubscriber(state));
            } catch (Throwable t) {
                // M-4 修复：重订阅/首次调用同步抛异常（checkRateLimit 等）→ 按流式路径错误语义
                // （onError 信号）决策，不得静默吞掉。
                handleStreamFailure(state, t);
            }
        }

        private ChatRequest attemptRequest(ChatOptions sunk) {
            ChatRequest req = new ChatRequest(request.getMessages(), sunk);
            if (request.getRequestId() != null) {
                req.setRequestId(request.getRequestId());
            }
            return req;
        }

        private void registerCallerListener() {
            if (callerToken != null && !callerListenerRegistered) {
                callerListenerRegistered = true;
                callerToken.appendOnCancel(reason -> {
                    if (cancelled.compareAndSet(false, true)) {
                        teardownCurrentAttempt(reason != null ? reason : "caller token cancelled");
                    }
                });
            }
        }

        /**
         * 取消顺序契约（spike 实证）：先 cancel 内部订阅（唤醒阻塞 submit）→ 再 cancel attempt
         * token（断 HTTP）；释放并发计数（per-attempt 一次守卫）。
         */
        private void teardownCurrentAttempt(String reason) {
            AttemptState state = attempt;
            attempt = null;
            if (state != null) {
                Flow.Subscription up = state.upSub;
                if (up != null) {
                    up.cancel();
                }
                state.token.cancel(reason);
                releaseAttempt(state);
            }
        }

        private void releaseAttempt(AttemptState state) {
            if (state.released.compareAndSet(false, true)) {
                adapter.getRegistry().release(state.candidate.getProvider(), state.candidate.getAccountKey());
                IFailoverMetrics metrics = adapter.getMetrics();
                if (metrics != null) {
                    metrics.onConcurrencyRelease(state.candidate.getProvider(), state.candidate.getAccountKey());
                }
            }
        }

        // ======================= 缓冲窗口（D1/D1a/Major-1） =======================

        /**
         * 窗口内/外判定 = 是否有数据已送达下游（forwarded）。N/T 只决定缓冲何时转为透传：
         * N 满或 T 超时后首个到达元素触发越窗 + 冲刷缓冲。T 超时但无元素到达时无元素可转发，
         * 失败仍完全透明 = 窗口内（与需求 §3.2"未转发任何数据时失败可重订阅"精确对齐）。
         */
        private void bufferOrForward(AttemptState state, ChatStreamChunk item) {
            synchronized (this) {
                if (cancelled.get()) {
                    return;
                }
                if (!state.passed) {
                    if (state.buffer.size() < adapter.getBufferSize()
                            && System.currentTimeMillis() - state.windowStart < adapter.getBufferTimeMs()) {
                        state.buffer.add(item);
                        return;
                    }
                    state.passed = true;
                    flushBuffer(state);
                }
                deliver(state, item);
            }
        }

        private void deliver(AttemptState state, ChatStreamChunk item) {
            if (cancelled.get()) {
                return;
            }
            if (demand > 0) {
                demand--;
                state.forwarded = true;
                subscriber.onNext(item);
            } else {
                state.buffer.add(item);
            }
        }

        private void flushBuffer(AttemptState state) {
            // 窗口未越前缓冲元素不得提前流出（D1a：缓冲的语义 = 窗口期不转发）。
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
        private void flushAll(AttemptState state) {
            while (!state.buffer.isEmpty() && demand > 0 && !cancelled.get()) {
                demand--;
                state.forwarded = true;
                subscriber.onNext(state.buffer.poll());
            }
        }

        // ======================= 终态处理 =======================

        /**
         * 流式错误决策（D5a 流式路径）：窗口内（未转发）→ 分类 + 记账 + 重选/重订阅（预算）；
         * 窗口外（已转发）→ 断流报错不切换（需求 §3.2）。
         */
        private void handleStreamFailure(AttemptState state, Throwable throwable) {
            boolean inWindow;
            synchronized (this) {
                inWindow = !state.forwarded;
            }
            releaseAttempt(state);
            IFailoverMetrics metrics = adapter.getMetrics();
            if (metrics != null) {
                // attempt 失败（切换类 / NON_TRANSIENT / 窗口外断流）；取消路径不经过本方法。
                metrics.onRequestFailure(state.candidate.getProvider(), state.candidate.getModel(),
                        state.candidate.getAccountKey(),
                        System.currentTimeMillis() - state.windowStart);
            }
            if (!inWindow) {
                // 窗口外失败：不切换。先交付剩余缓冲元素（受 demand 约束），再断流报错。
                synchronized (this) {
                    if (cancelled.get()) {
                        return;
                    }
                    flushAll(state);
                    subscriber.onError(throwable);
                }
                return;
            }
            // 窗口内失败：丢弃已缓冲元素（Major-1——attempt1 前缀不得进入最终输出），干净重来。
            state.buffer.clear();
            ErrorClassification cls = ChatServiceFailoverAdapter.classifyStreamError(throwable, state.candidate);
            if (cls == ErrorClassification.CACHE_STATE_LOST) {
                // 原地重试语义：不触发账号切换，重发同一候选一次，预算同样计数（防御分支）。
                if (state.index <= adapter.getRetryBudget()) {
                    startAttemptWithOptions(state.candidate, state.sunkOptions);
                } else {
                    deliverError(throwable);
                }
                return;
            }
            if (!ChatServiceFailoverAdapter.isSwitchable(cls)) {
                // NON_TRANSIENT：不切换直接失败（不消耗预算）。
                deliverError(throwable);
                return;
            }
            CircuitObservation.recordFailure(adapter.getMetrics(), adapter.getBreaker(),
                    state.candidate.getProvider(), state.candidate.getModel(), state.candidate.getModelKey());
            if (state.index > adapter.getRetryBudget()) {
                // 预算耗尽 → 断流报错（fail-loud，不静默）。
                deliverError(throwable);
                return;
            }
            // 重订阅：被动路径重选（含 provider 链扩展）+ 新窗口 + 新 per-attempt token。
            startAttemptWithOptions(null, null);
        }

        private void handleStreamComplete(AttemptState state) {
            synchronized (this) {
                if (cancelled.get()) {
                    return;
                }
                releaseAttempt(state);
                // 流式成功路径 recordSuccess 挂钩（Minor-2）：HALF_OPEN 探活成功 → CLOSED 恢复。
                CircuitObservation.recordSuccess(adapter.getMetrics(), adapter.getBreaker(),
                        state.candidate.getProvider(), state.candidate.getModel(), state.candidate.getModelKey());
                IFailoverMetrics metrics = adapter.getMetrics();
                if (metrics != null) {
                    metrics.onRequestSuccess(state.candidate.getProvider(), state.candidate.getModel(),
                            state.candidate.getAccountKey(), System.currentTimeMillis() - state.windowStart);
                }
                // 窗口期成功终止：缓冲元素必须全部交付（窗口语义只延迟转发，不吞数据）。
                flushAll(state);
                subscriber.onComplete();
            }
        }

        private void deliverError(Throwable throwable) {
            synchronized (this) {
                if (cancelled.get()) {
                    return;
                }
                AttemptState current = attempt;
                if (current != null) {
                    current.buffer.clear();
                }
                subscriber.onError(throwable);
            }
        }

        // ======================= 内部订阅者（每个 attempt 一个） =======================

        private final class AttemptSubscriber implements Flow.Subscriber<ChatStreamChunk> {
            private final AttemptState state;

            AttemptSubscriber(AttemptState state) {
                this.state = state;
            }

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                state.upSub = subscription;
                if (cancelled.get() || state != attempt) {
                    // 已取消或已被放弃的 attempt：立即取消内部订阅（防 HTTP 连接到 EOF 泄漏）。
                    subscription.cancel();
                    return;
                }
                // 窗口期保持 demand（request(MAX)），避免缓冲积压导致 submit/close 阻塞（spike）。
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ChatStreamChunk item) {
                if (cancelled.get() || state != attempt) {
                    return;
                }
                bufferOrForward(state, item);
            }

            @Override
            public void onError(Throwable throwable) {
                if (cancelled.get() || state != attempt) {
                    // 被放弃 attempt 的迟到终态信号：忽略（当前 attempt 已接管）。
                    return;
                }
                handleStreamFailure(state, throwable);
            }

            @Override
            public void onComplete() {
                if (cancelled.get() || state != attempt) {
                    return;
                }
                handleStreamComplete(state);
            }
        }
    }
}
