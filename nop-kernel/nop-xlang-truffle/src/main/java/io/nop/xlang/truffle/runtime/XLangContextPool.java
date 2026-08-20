package io.nop.xlang.truffle.runtime;

import io.nop.core.lang.eval.DisabledEvalOutput;
import io.nop.core.lang.eval.EvalScopeImpl;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.exec.NullExecutable;
import io.nop.xlang.truffle.XLangTruffleConfigs;
import io.nop.xlang.truffle.eval.XLangTruffleEval;
import io.nop.xlang.truffle.lang.XLangContext;
import io.nop.xlang.truffle.lang.XLangLanguage;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * XLang truffle 后端 Context 池（设计 truffle 02 §五"路 A"：Context 池 + 共享 Engine +
 * SHARED；plan I8 Phase 1 §3 契约定稿）。
 *
 * <p><b>租借协议（契约）</b>：求值线程从池租借 Context（{@link #lease()} = 出借 +
 * {@code enter()}），批求值 = 一个 {@link Lease} 内多次 {@link Lease#eval}（每次一个
 * handoff 循环，可重入——polyglot enter 语义允许同线程多次 enter，本形态为单 enter 包批）；
 * 用毕 {@link Lease#close()} 归还（= 残留检测 + 防御清空 + {@code leave()} + 归还）。
 * 不做每线程固定绑定（线程池弹性伸缩会泄漏 Context）。租借注入复用既有窗口协议：
 * 求值现场（scope + 输出缓冲）经 {@code EvalHandoff} 逐求值注入、根节点
 * {@code bindEvaluation/clearEvaluation} 绑定清空——context 内状态只在
 * {@code enter()..leave()} 窗口内有效；<b>归还前清空由池侧保证</b>（残留检测红灯 + 退役；
 * 无残留时防御性二次清空），归还后不得残留上一批求值的可变状态（翻译缓存等可共享数据
 * 除外，存语言实例作用域）。
 *
 * <p><b>池耗尽语义（裁定）</b>：有界阻塞等待（idle 队列阻塞取）；fail-fast 保留给协议
 * 违约（双重归还/残留/use-after-close）与 Engine 创建失败；池大小调优归 I12。
 *
 * <p><b>线程亲和</b>：Lease 须由租借线程使用并归还（enter/leave 与 handoff 均线程绑定）；
 * 并发由多 Context 承担（每 Context 内保持串行——语言 {@code isThreadAccessAllowed} 默认）。
 *
 * <p><b>预热</b>：每 Context 创建即经租借机制跑一次平凡求值——池出口到 CallTarget 执行
 * 全链 fail-fast 接线自检，并保证语言 context 已初始化（归还时
 * {@link XLangLanguage#currentContext()} 可达）。
 */
public final class XLangContextPool implements AutoCloseable {

    private static final String WARMUP_SOURCE_KEY = "xl:pool-warmup";

    private final BlockingQueue<PooledContext> idle;

    private final AtomicInteger leasedCount = new AtomicInteger();

    private final int maxSize;

    private volatile XLangLanguage language;

    private volatile boolean closed;

    private XLangContextPool(int maxSize) {
        this.maxSize = maxSize;
        this.idle = new ArrayBlockingQueue<>(maxSize);
    }

    /**
     * 打开一个池（容量 = {@code nop.xlang.truffle.context-pool.max-size}，缺省随 JVM 并行度）。
     */
    public static XLangContextPool open() {
        return open(XLangTruffleConfigs.CFG_TRUFFLE_CONTEXT_POOL_MAX_SIZE.get());
    }

    /**
     * 打开一个指定容量的池（创建全部 Context 并逐一预热；任一环节失败 fail-fast，
     * 已创建的 Context 全部回收）。
     */
    public static XLangContextPool open(int maxSize) {
        if (maxSize <= 0)
            throw new IllegalArgumentException("context pool max size must be positive: " + maxSize);
        XLangContextPool pool = new XLangContextPool(maxSize);
        try {
            pool.initialize();
        } catch (RuntimeException e) {
            try {
                pool.close();
            } catch (RuntimeException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
        return pool;
    }

    private void initialize() {
        for (int i = 0; i < maxSize; i++) {
            PooledContext pooled = createPooledContext();
            // 走与 lease() 相同的激活/归还状态机（计数与状态转移一处维护），
            // 绕过状态机会使归还路径误判协议违约而退役条目（预热后池空转 → 租借永久阻塞）
            try (Lease lease = activate(pooled)) {
                XLangTruffleEval.TranslatedEval warmup = lease.eval(WARMUP_SOURCE_KEY, NullExecutable.NULL,
                        new EvalScopeImpl(), DisabledEvalOutput.INSTANCE);
                if (warmup.getThrown() != null)
                    throw new IllegalStateException("pooled context warm-up evaluation failed (wiring defect)",
                            warmup.getThrown());
                XLangLanguage captured = warmup.getUnit().getRootNode().getXLangLanguage();
                if (captured == null)
                    throw new IllegalStateException(
                            "pooled context warm-up lost the language instance (wiring defect)");
                if (language == null)
                    language = captured;
            }
        }
    }

    /**
     * 共享 Engine 的语言实例（预热时捕获；SHARED 下单份）。翻译失败观测注册
     * （{@code addTranslationFailureListener}）与 SHARED 激活断言的取用通道（I9 truffle 侧
     * SPI 适配器经池句柄取得语言实例，plan I8 Phase 1 §6 消费路径）。
     */
    public XLangLanguage getLanguage() {
        XLangLanguage captured = language;
        if (captured == null)
            throw new IllegalStateException("pool language instance not captured (pool not initialized)");
        return captured;
    }

    private PooledContext createPooledContext() {
        Context context = Context.newBuilder(XLangLanguage.ID)
                .engine(XLangTruffleEngine.sharedEngine())
                .allowHostAccess(HostAccess.ALL)
                .build();
        return new PooledContext(context);
    }

    /**
     * 租借一个 Context（出借 + enter；池耗尽时有界阻塞等待；池关闭后 fail-fast）。
     */
    public Lease lease() {
        ensureNotClosed();
        PooledContext pooled = takeIdle();
        if (pooled == PooledContext.POISON)
            throw new IllegalStateException("context pool is closed: no lease available");
        return activate(pooled);
    }

    /**
     * 激活一条池条目为租约（状态机与计数统一入口：AVAILABLE→LEASED + 计数 + enter）。
     */
    private Lease activate(PooledContext pooled) {
        if (!pooled.state.compareAndSet(EntryState.AVAILABLE, EntryState.LEASED))
            throw new IllegalStateException(
                    "context pool protocol violation: borrowed entry not in AVAILABLE state: " + pooled.context);
        if (closed) {
            retire(pooled);
            throw new IllegalStateException("context pool is closed: no lease available");
        }
        leasedCount.incrementAndGet();
        Lease lease = new Lease(pooled);
        try {
            lease.enter();
        } catch (RuntimeException e) {
            leasedCount.decrementAndGet();
            retire(pooled);
            throw e;
        }
        return lease;
    }

    private PooledContext takeIdle() {
        try {
            return idle.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for an idle pooled context", e);
        }
    }

    private void returnToPool(PooledContext pooled) {
        if (closed || !pooled.state.compareAndSet(EntryState.LEASED, EntryState.AVAILABLE)) {
            retire(pooled);
            return;
        }
        // 容量 = maxSize 且退役条目不归还 → 队列永不满，offer 必然成功（失败即协议违约，fail-fast）
        if (!idle.offer(pooled))
            throw new IllegalStateException(
                    "context pool protocol violation: idle queue rejected a returned entry: " + pooled.context);
    }

    private void retire(PooledContext pooled) {
        pooled.state.set(EntryState.RETIRED);
        pooled.context.close();
    }

    private void ensureNotClosed() {
        if (closed)
            throw new IllegalStateException("context pool is closed: no lease available");
    }

    /**
     * 关闭池（全部 Context 关闭；须在所有 Lease 归还后调用——未归还租约 = 协议违约 fail-fast
     * 由租约侧红灯承载；被阻塞的租借者被唤醒并 fail-fast）。
     */
    @Override
    public void close() {
        if (closed)
            throw new IllegalStateException("context pool already closed");
        closed = true;
        PooledContext pooled;
        while ((pooled = idle.poll()) != null)
            retire(pooled);
        // 唤醒可能阻塞在 take() 的租借者（毒丸：maxSize 个封顶，超出队列容量的 offer 不发生）
        for (int i = 0; i < maxSize; i++)
            idle.offer(PooledContext.POISON);
    }

    public int getMaxSize() {
        return maxSize;
    }

    /**
     * 当前空闲（可租借）条目数（关闭后含毒丸，仅供诊断）。
     */
    public int availableCount() {
        return idle.size();
    }

    /**
     * 当前未归还租约数。
     */
    public int leasedCount() {
        return leasedCount.get();
    }

    private enum EntryState {
        AVAILABLE, LEASED, RETIRED
    }

    private static final class PooledContext {
        static final PooledContext POISON = new PooledContext(null);

        final Context context;

        final AtomicReference<EntryState> state = new AtomicReference<>(EntryState.AVAILABLE);

        PooledContext(Context context) {
            this.context = context;
        }
    }

    /**
     * 租借句柄（enter..leave 窗口 + 逐求值 handoff 循环；由租借线程使用并关闭）。
     */
    public final class Lease implements AutoCloseable {

        private final PooledContext pooled;

        private boolean closed;

        private Lease(PooledContext pooled) {
            this.pooled = pooled;
        }

        private void enter() {
            pooled.context.enter();
        }

        /**
         * 本租约的 polyglot Context（测试/诊断与协议注入用；不得跨线程使用）。
         */
        public Context getContext() {
            return pooled.context;
        }

        /**
         * 批求值成员（一个 Lease 多次调用 = 可重入批求值；每次调用一个 handoff 循环——
         * 求值现场逐次注入，嵌套 handoff fail-fast 语义保持）。
         */
        public XLangTruffleEval.TranslatedEval eval(String sourceKey, IExecutableExpression tree,
                                                    IEvalScope scope, IEvalOutput output) {
            ensureOpen();
            return XLangTruffleEval.evalOnContext(pooled.context, sourceKey, tree, scope, output);
        }

        @Override
        public void close() {
            if (closed)
                throw new IllegalStateException("lease already closed (double return is a protocol violation)");
            closed = true;
            RuntimeException violation = null;
            try {
                XLangContext langContext = XLangLanguage.currentContext();
                if (langContext.hasEvaluationResidue()) {
                    violation = new IllegalStateException(
                            "pooled context returned with evaluation residue (borrower leaked evaluation state): "
                                    + pooled.context);
                } else {
                    // 池侧归还清空契约：无残留时防御性二次清空（幂等；根节点 finally 已清空的健康路径零开销）
                    langContext.clearEvaluation();
                }
            } catch (RuntimeException e) {
                violation = new IllegalStateException("residue check failed for pooled context", e);
            } finally {
                leasedCount.decrementAndGet();
                pooled.context.leave();
            }
            if (violation != null) {
                // 可疑状态不得复用：退役（关闭）后红灯上抛
                retire(pooled);
                throw violation;
            }
            returnToPool(pooled);
        }

        private void ensureOpen() {
            if (closed)
                throw new IllegalStateException("lease already closed: eval after return is a protocol violation");
        }
    }
}
