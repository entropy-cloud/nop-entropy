package io.nop.ai.gateway.failover;

import io.nop.api.core.time.CoreMetrics;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ErrorClassification;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.core.model.ModelClassCandidate;
import io.nop.ai.gateway.routing.ModelClassRouter;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancelToken;
import io.nop.gateway.core.executor.BoundedWindowBuffer;
import io.nop.gateway.core.executor.StreamingBufferOverflowException;
import io.nop.commons.metrics.GlobalMeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 本地形态流式 failover 的 {@link Flow.Publisher} 实现（plan 2026-08-15-1116-2 LOCAL-03，
 * W1 spike SPIKE-02 输入；plan 2509 R2-06 起迁移为共享有界窗口缓冲组件
 * {@link BoundedWindowBuffer} 的 3.3 侧宿主）。
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
 * <p><b>共享缓冲核契约</b>（plan 2509 裁定①③④⑤⑥）：buffer/窗口态（passed/forwarded）/
 * demand 记账/pendingComplete 终态挂起/cancel 清理由 {@link BoundedWindowBuffer} 承载，本类
 * monitor 保护全部组件调用；3.3 侧上游授信 = <b>request(MAX) 保留</b>（上游为阻塞 submit 语义，
 * demand 联动会阻塞 HTTP 接收线程 stall 连接——裁定⑤(b) 成文）+ 越窗上界兜底：越窗缓冲满
 * （同 bufferSize）= {@link StreamingBufferOverflowException} 类型化溢出错误终态，且为
 * <b>不可重试终态</b>（绕过分类/recordFailure/重选/重订阅，直接断流 + releaseAttempt）。
 *
 * <p><b>acquire/release 配对</b>（裁定② C2-001）：{@code AttemptSubscriber.onSubscribe} 取消/
 * 放弃分支 = {@code subscription.cancel() + releaseAttempt(state)} 漏斗单行——覆盖全部 4 进入点
 * （首 attempt / 超限重选递归 / CACHE_STATE_LOST 原地重发 / 切换类重订阅）× 全部取消交错时序；
 * {@code released} CAS 保证 exactly-once。
 *
 * <p><b>monitor 完整性</b>（裁定③ C1-001）：buffer 全部触点均在 monitor 内（clear 入锁）；
 * flush 信号携带 attempt 引用，投递前 identity 复检——放弃 attempt 的缓冲永不交付。
 *
 * <p><b>终态挂起</b>（裁定④ C3-001）：complete 路径冲刷后缓冲非空（demand 耗尽）→ 置
 * pendingComplete 挂起终态，request() 冲空缓冲后补发 onComplete（尾部 chunk 不得丢弃）；错误
 * 终态豁免（错误优先、剩余缓冲丢弃为显式语义）。
 *
 * <p><b>出锁投递</b>（裁定⑥ C2-002）：锁内仅状态机变更（记账 + 元素摘取入信号队列），全部下游
 * 投递（onNext/onComplete/onError）与成功记账（recordSuccess/metrics）在 monitor 外经单一
 * drain 守卫串行执行——同元素不乱序、终态最后。
 *
 * <p><b>取消契约</b>（spike 实证）：取消顺序 = 先 cancel 适配器对 delegate publisher 的
 * <b>内部订阅</b>（唤醒阻塞 submit）→ 再 cancel 该 attempt 的 token（断 HTTP）；每次 attempt 前
 * 检查调用方 {@code isCancelled()}（Minor-5）。
 *
 * <p><b>无静默跳过</b>：预算耗尽 / NON_TRANSIENT / 窗口外失败 / 缓冲溢出 → 显式断流报错
 * （onError）；重订阅同步抛异常（checkRateLimit）→ 按传输异常级分类决策，不吞。
 */
final class FailoverStreamFlow implements Flow.Publisher<ChatStreamChunk> {

    private static final Logger LOG = LoggerFactory.getLogger(FailoverStreamFlow.class);

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
     * 单个 attempt 的运行时状态。缓冲/窗口/demand/pendingComplete 由共享组件
     * {@link BoundedWindowBuffer} 承载（StreamSubscription monitor 保护）；本状态仅保留
     * attempt 私有面 + {@code released} per-attempt 一次释放守卫（Minor-3，AtomicBoolean 无锁）。
     */
    private static final class AttemptState {
        final int index; // 1-based：1 = 首次 attempt
        final ModelClassCandidate candidate;
        final ChatOptions sunkOptions;
        final AttemptCancelToken token;
        final AtomicBoolean released = new AtomicBoolean();
        final long windowStart = CoreMetrics.currentTimeMillis(); // 仅 metrics 时长维度
        volatile Flow.Subscription upSub;

        AttemptState(int index, ModelClassCandidate candidate, ChatOptions sunkOptions,
                     AttemptCancelToken token) {
            this.index = index;
            this.candidate = candidate;
            this.sunkOptions = sunkOptions;
            this.token = token;
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
        final AttemptState attempt;
        final ChatStreamChunk item;
        final Throwable cause;

        private Signal(int kind, AttemptState attempt, ChatStreamChunk item, Throwable cause) {
            this.kind = kind;
            this.attempt = attempt;
            this.item = item;
            this.cause = cause;
        }

        static Signal item(AttemptState attempt, ChatStreamChunk item) {
            return new Signal(ITEM, attempt, item, null);
        }

        static Signal complete(AttemptState attempt) {
            return new Signal(COMPLETE, attempt, null, null);
        }

        static Signal error(AttemptState attempt, Throwable cause) {
            return new Signal(ERROR, attempt, null, cause);
        }
    }

    private final class StreamSubscription implements Flow.Subscription {

        private final Flow.Subscriber<? super ChatStreamChunk> subscriber;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        // 共享状态核：buffer/窗口/passed/forwarded/demand/pendingComplete/cancel 由组件承载
        private final BoundedWindowBuffer<ChatStreamChunk> buffer;
        private volatile AttemptState attempt;
        private volatile boolean callerListenerRegistered;
        private int attemptsStarted; // 仅在实际创建 attempt（发起 fetch）时递增
        private boolean terminated;  // guarded by this：终态信号 exactly-once
        private volatile Counter overflowCounter; // 溢出计数指标（懒创建，provider/model 维度）

        private final java.util.ArrayDeque<Signal> signalQueue = new java.util.ArrayDeque<>(); // guarded by this
        private boolean draining; // guarded by this：单一 drain 守卫（投递串行化）

        StreamSubscription(Flow.Subscriber<? super ChatStreamChunk> subscriber) {
            this.subscriber = subscriber;
            this.buffer = new BoundedWindowBuffer<>(adapter.getBufferSize(), false,
                    adapter.getBufferTimeMs());
        }

        // ======================= Flow.Subscription =======================

        @Override
        public void request(long n) {
            if (n <= 0) {
                // Reactive Streams 规范 3.9 强制要求 request(n<=0) 抛 IAE（豁免错误码改造）。
                throw new IllegalArgumentException("request amount must be positive: " + n);
            }
            if (cancelled.get()) {
                return;
            }
            synchronized (this) {
                buffer.addDemand(n);
                enqueueFlushLocked();
                if (buffer.isPendingComplete()) {
                    enqueueFlushAllLocked();
                    if (buffer.isEmpty()) {
                        // 挂起的终态：冲空缓冲后补发 onComplete（尾部元素不得随 onComplete 丢弃，
                        // 裁定④ pendingComplete 先例对齐——第 6 投递点，出锁投递）。
                        buffer.setPendingComplete(false);
                        signalQueue.add(Signal.complete(attempt));
                    }
                }
            }
            drain();
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
         * acquire 后的装配/订阅段在 monitor 外（裁定⑥：attempt 发起出锁）。
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
            synchronized (this) {
                if (cancelled.get() || terminated) {
                    // 取消落在 acquire 后/装配前：漏斗行统一释放（不装配、不留幽灵计数）。
                    releaseAttempt(state);
                    return;
                }
                attempt = state;
                buffer.beginWindow();
            }
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
         * token（断 HTTP）；释放并发计数（per-attempt 一次守卫）。组件 cancel 清理半边：
         * 挂起终态不再补发、缓冲/demand 全清理（裁定①组件契约）。
         */
        private void teardownCurrentAttempt(String reason) {
            AttemptState state;
            synchronized (this) {
                state = attempt;
                attempt = null;
                buffer.clearForCancel();
            }
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

        // ======================= 锁内状态机（裁定⑥：仅记账，不出信号） =======================

        /**
         * 窗口内/外判定 = 是否有数据已送达下游（forwarded，组件承载）。N/T 只决定缓冲何时转为
         * 透传：N 满或 T 超时后首个到达元素触发越窗 + 冲刷缓冲。溢出面：越窗后 demand==0 的元素
         * 入缓冲前容量检查（与窗口期同界 bufferSize），满 = 类型化溢出错误终态（裁定⑤）。
         * 调用方持锁。
         */
        private void handleItemLocked(AttemptState state, ChatStreamChunk item) {
            if (terminated) {
                return;
            }
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
                // 越窗上界（与窗口期同界 bufferSize）：溢出 = 类型化不可重试终态（fail-loud，
                // 绕过分类/记账/重订阅），request(MAX) 阻塞 submit 语义下的兜底防线（裁定⑤）。
                recordOverflow(buffer.size(), state);
                signalQueue.add(Signal.error(state, new StreamingBufferOverflowException(
                        "streaming failover buffer overflow: backlog " + buffer.size()
                                + " chunks exceeds bufferSize " + buffer.getCapacity()
                                + " (slow downstream, fail loud)")));
            }
        }

        /**
         * 缓冲冲刷（passed 门控）：锁内摘元素 + demand 递减入信号队列（元素携带摘取时点的
         * attempt 引用——identity 守卫素材，裁定③）。调用方持锁。
         */
        private void enqueueFlushLocked() {
            if (!buffer.isPassed()) {
                // 窗口未越前缓冲元素不得提前流出（D1a：缓冲的语义 = 窗口期不转发）。
                return;
            }
            AttemptState current = attempt;
            while (!buffer.isEmpty() && buffer.hasDemand() && !cancelled.get() && current != null) {
                buffer.decDemand();
                buffer.markForwarded();
                signalQueue.add(Signal.item(current, buffer.poll()));
            }
        }

        /**
         * 终态冲刷（onComplete/断流前）：不受窗口状态门控——窗口语义只延迟转发，不吞数据。
         * 调用方持锁。
         */
        private void enqueueFlushAllLocked() {
            AttemptState current = attempt;
            while (!buffer.isEmpty() && buffer.hasDemand() && !cancelled.get() && current != null) {
                buffer.decDemand();
                buffer.markForwarded();
                signalQueue.add(Signal.item(current, buffer.poll()));
            }
        }

        private void recordOverflow(int depth, AttemptState state) {
            LOG.warn("nop.ai.gateway.failover.streaming-buffer-overflow:provider={},model={},accountKey={},"
                            + "backlogDepth={},bufferSize={}",
                    state.candidate.getProvider(), state.candidate.getModel(),
                    state.candidate.getAccountKey(), depth, buffer.getCapacity());
            Counter counter = overflowCounter;
            if (counter == null) {
                MeterRegistry registry = GlobalMeterRegistry.instance();
                if (registry != null) {
                    counter = Counter.builder("nop.ai.gateway.failover.buffer.overflow")
                            .tag("provider", String.valueOf(state.candidate.getProvider()))
                            .tag("model", String.valueOf(state.candidate.getModel()))
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
         * 已 forwarded 的残留丢弃 + WARN 留痕计数——数据丢失由上游 failover 重试承载）；
         * 终态信号为流级，由 terminated exactly-once 门控。
         */
        private void deliver(Signal signal) {
            if (signal.kind == Signal.ITEM) {
                synchronized (this) {
                    if (signal.attempt != attempt) {
                        if (buffer.isForwarded()) {
                            LOG.warn("nop.ai.gateway.failover.dropped-orphan-item:attemptIndex={},depth={}",
                                    signal.attempt.index, buffer.size());
                        }
                        return;
                    }
                }
                subscriber.onNext(signal.item);
                return;
            }
            if (signal.kind == Signal.ERROR) {
                terminate(signal.attempt, signal.cause);
                return;
            }
            // COMPLETE 终态（最后序由入队序 + 顺序 drain 保证）：成功记账出锁（裁定⑥）。
            terminateComplete(signal.attempt);
        }

        // ======================= 终态处理（monitor 外） =======================

        /**
         * 流式错误决策（D5a 流式路径）：窗口内（未转发）→ 分类 + 记账 + 重选/重订阅（预算）；
         * 窗口外（已转发）→ 断流报错不切换（需求 §3.2）。溢出错误 = 不可重试终态，直接断流。
         * 锁内仅 inWindow 判定 + clear（裁定③：clear 入锁）+ 终态信号入队；分类（文件 I/O 重活）、
         * 记账与重订阅决策全部在 monitor 外（裁定⑥）。
         */
        private void handleStreamFailure(AttemptState state, Throwable throwable) {
            boolean inWindow;
            synchronized (this) {
                if (terminated || cancelled.get()) {
                    return;
                }
                inWindow = !buffer.isForwarded();
                if (inWindow) {
                    // 窗口内失败：丢弃已缓冲元素（Major-1——attempt1 前缀不得进入最终输出）。
                    // clear 入锁（裁定③ C1-001）：与 request() 冲刷互斥，零撕裂零泄漏。
                    buffer.clear();
                }
            }
            releaseAttempt(state);
            IFailoverMetrics metrics = adapter.getMetrics();
            if (metrics != null) {
                // attempt 失败（切换类 / NON_TRANSIENT / 窗口外断流）；取消路径不经过本方法。
                metrics.onRequestFailure(state.candidate.getProvider(), state.candidate.getModel(),
                        state.candidate.getAccountKey(),
                        CoreMetrics.currentTimeMillis() - state.windowStart);
            }
            if (throwable instanceof StreamingBufferOverflowException) {
                // 裁定⑤：溢出 = 不可重试终态——不分类、不 recordFailure、不重选/重订阅，直接断流。
                synchronized (this) {
                    if (terminated || cancelled.get()) {
                        return;
                    }
                    buffer.clear();
                    signalQueue.add(Signal.error(state, throwable));
                }
                drain();
                return;
            }
            if (!inWindow) {
                // 窗口外失败：不切换。先交付剩余缓冲元素（受 demand 约束），再断流报错。
                synchronized (this) {
                    if (terminated || cancelled.get()) {
                        return;
                    }
                    enqueueFlushAllLocked();
                    signalQueue.add(Signal.error(state, throwable));
                }
                drain();
                return;
            }
            // 分类在锁外（裁定③协调：文件 I/O 重活不拖回锁内）。
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
            // 重试决策与 startAttempt 均在 monitor 外（裁定⑥）。
            startAttemptWithOptions(null, null);
        }

        /**
         * 完成终态（裁定④）：锁内冲刷 + 缓冲非空挂起 pendingComplete；缓冲空则 complete 信号
         * 入队。成功记账（recordSuccess/metrics）在投递段出锁执行（裁定⑥）。
         */
        private void handleStreamComplete(AttemptState state) {
            synchronized (this) {
                if (terminated || cancelled.get() || state != attempt) {
                    return;
                }
                enqueueFlushAllLocked();
                if (!buffer.isEmpty() && !cancelled.get()) {
                    // demand 耗尽且缓冲区还有元素：挂起终态，等后续 request() 冲空缓冲再补发
                    // onComplete——尾部元素（如携带 finish_reason/usage 的最后 chunk）不得丢弃
                    //（裁定④ pendingComplete 先例对齐）
                    buffer.setPendingComplete(true);
                } else {
                    signalQueue.add(Signal.complete(state));
                }
            }
            // 挂起分支同样必须 drain——冲刷出的元素信号已在队列，出锁投递不得滞留
            drain();
        }

        /**
         * 成功终态交付（monitor 外）：成功记账出锁（裁定⑥：锁内仅置位）+ onComplete。
         */
        private void terminateComplete(AttemptState state) {
            synchronized (this) {
                if (terminated || cancelled.get()) {
                    return;
                }
                terminated = true;
                attempt = null;
            }
            releaseAttempt(state);
            // 流式成功路径 recordSuccess 挂钩（Minor-2）：HALF_OPEN 探活成功 → CLOSED 恢复。
            CircuitObservation.recordSuccess(adapter.getMetrics(), adapter.getBreaker(),
                    state.candidate.getProvider(), state.candidate.getModel(), state.candidate.getModelKey());
            IFailoverMetrics metrics = adapter.getMetrics();
            if (metrics != null) {
                metrics.onRequestSuccess(state.candidate.getProvider(), state.candidate.getModel(),
                        state.candidate.getAccountKey(), CoreMetrics.currentTimeMillis() - state.windowStart);
            }
            if (!cancelled.get()) {
                subscriber.onComplete();
            }
        }

        /**
         * 错误终态（monitor 外）：deliverError 决策入口与错误信号投递共用。错误路径豁免成文
         * （裁定④）：错误优先、剩余缓冲丢弃为显式语义（无「半个成功」数据完整性违约面）。
         */
        private void deliverError(Throwable throwable) {
            synchronized (this) {
                if (terminated || cancelled.get()) {
                    return;
                }
                buffer.clear();
                signalQueue.add(Signal.error(attempt, throwable));
            }
            drain();
        }

        private void terminate(AttemptState state, Throwable cause) {
            synchronized (this) {
                if (terminated || cancelled.get()) {
                    return;
                }
                terminated = true;
                attempt = null;
            }
            if (!cancelled.get()) {
                subscriber.onError(cause);
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
                    // 已取消或已被放弃的 attempt：立即取消内部订阅（防 HTTP 连接到 EOF 泄漏）
                    // + 释放 acquire（裁定② C2-001 漏斗单行：released CAS exactly-once，
                    // 覆盖全部 4 进入点 × 全部取消交错时序）。
                    subscription.cancel();
                    releaseAttempt(state);
                    return;
                }
                // 窗口期保持 demand（request(MAX)），避免缓冲积压导致 submit/close 阻塞（spike 实证，
                // 裁定⑤(b) 3.3 侧成文：阻塞 submit 语义不可 demand 联动，越窗上界兜底承担防线）。
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ChatStreamChunk item) {
                if (cancelled.get() || state != attempt) {
                    return;
                }
                synchronized (StreamSubscription.this) {
                    if (cancelled.get() || terminated || state != attempt) {
                        return;
                    }
                    handleItemLocked(state, item);
                }
                drain();
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
