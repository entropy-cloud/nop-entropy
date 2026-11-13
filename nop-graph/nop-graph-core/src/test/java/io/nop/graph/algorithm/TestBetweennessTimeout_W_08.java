package io.nop.graph.algorithm;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.graph.NopGraphErrors;
import io.nop.graph.api.Edge;
import io.nop.graph.api.IGraph;
import io.nop.graph.impl.InMemoryGraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 5.6-W-08（plan 2529 Phase 2 组 C / 设计裁定⑥）Betweenness 超时包络回归钉（三形态）。
 *
 * <p>(i) 3 参超时钉：探针 IGraph 在出边枚举处闩阻塞 + timeout≈0（100ms）→ 显式超时错误
 * {@code ERR_GRAPH_COMPUTE_TIMEOUT} + bizFatal（非静默空 map/静默取消——rule 24）；放行闩后
 * 工作线程 join 终结（一次性 daemon 线程，不成幽灵——防 W-04 同型自反）。
 *
 * <p>(ii) 接线钉（评审 Major-1 生产接线条款）：2 参 compute 缺省委托 3 参路径——探针捕获
 * 全部 getOutEdges 回调线程，断言均在 {@code nop-graph-betweenness-} 前缀 daemon 工作线程
 * 上（回调全在调用线程 = pre-fix 红）；缺省常量 {@code DEFAULT_BETWEENNESS_TIMEOUT_MS ==
 * 30000}（LeidenConfig 家族一致性）；2 参结果 == 3 参(30000) 结果。
 *
 * <p>(iii) 异常透传钉（评审 R1 Major-1）：探针在闩内抛既定 NopException（既定错误码 +
 * bizFatal）→ 3 参 compute 原码 + bizFatal 原样透出、调用方面永不出现
 * ExecutionException 外壳。
 *
 * <p>语义边界（C10 义务，裁定⑥.6）：超时仅放弃等待——底层 Brandes 计算不可中断（JGraphT
 * 1.5.2 零 interrupt 引用），将在后台 daemon 线程继续至自然结束，不阻塞 JVM 退出；本测试
 * 不钉死「可中断」的错误承诺。
 */
class TestBetweennessTimeout_W_08 {

    private static final ErrorCode PROBE_ERROR = ErrorCode.define(
            "nop.err.graph.test-probe-error", "probe error {probe}", "probe");

    /**
     * 探针 IGraph：出边枚举处可注入阻塞闩/异常，并记录全部回调线程（名称 + daemon 现场）。
     */
    static final class ProbeGraph implements IGraph {
        private final InMemoryGraph delegate;
        final ConcurrentLinkedQueue<Thread> callbackThreads = new ConcurrentLinkedQueue<>();
        CountDownLatch entered;
        CountDownLatch release;
        RuntimeException throwOnProbe;

        ProbeGraph(InMemoryGraph delegate) {
            this.delegate = delegate;
        }

        @Override
        public List<Edge> getOutEdges(String nodeId) {
            callbackThreads.add(Thread.currentThread());
            if (throwOnProbe != null) {
                throw throwOnProbe;
            }
            if (entered != null && release != null) {
                entered.countDown();
                try {
                    if (!release.await(30, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("probe gate timed out");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("probe interrupted");
                }
            }
            return delegate.getOutEdges(nodeId);
        }

        @Override
        public List<Edge> getInEdges(String nodeId) {
            return delegate.getInEdges(nodeId);
        }
    }

    private static InMemoryGraph buildSmallGraph() {
        InMemoryGraph g = new InMemoryGraph();
        // a -> bridge -> b, plus leaves - small deterministic Brandes workload
        g.addEdge("a", "bridge");
        g.addEdge("bridge", "b");
        g.addEdge("a1", "a");
        g.addEdge("a2", "a");
        g.addEdge("b", "b1");
        g.addEdge("b", "b2");
        return g;
    }

    /**
     * (i) 超时钉：timeout=100ms 显式超时错误 + bizFatal + 工作线程不泄漏。
     */
    @Test
    @Timeout(60)
    void testW_08_timeoutEnvelopeFailsFastAndWorkerTerminates() throws Exception {
        InMemoryGraph g = buildSmallGraph();
        ProbeGraph probe = new ProbeGraph(g);
        probe.entered = new CountDownLatch(1);
        probe.release = new CountDownLatch(1);
        Set<String> nodes = g.nodeSet();

        long start = System.nanoTime();
        NopException e = org.junit.jupiter.api.Assertions.assertThrows(NopException.class,
                () -> BetweennessCentrality.compute(probe, nodes, 100L));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertEquals(NopGraphErrors.ERR_GRAPH_COMPUTE_TIMEOUT.getErrorCode(), e.getErrorCode(),
                "timeout must surface the explicit compute-timeout code, got: " + e.getErrorCode());
        assertTrue(e.isBizFatal(), "timeout on the same graph reproduces deterministically - bizFatal");

        // the failure is the wall-clock envelope, not the Brandes workload (which is parked)
        assertTrue(elapsedMs < 10_000, "timeout must fail fast, took " + elapsedMs + "ms");
        // the computation really ran on the wrapped worker, not the calling thread
        Thread worker = probe.callbackThreads.peek();
        assertNotNull(worker, "computation must have entered getOutEdges");
        assertFalse(Thread.currentThread() == worker,
                "computation must run on the worker thread, not the caller");

        // release the probe: the daemon worker finishes the bounded workload and terminates
        probe.release.countDown();
        worker.join(30_000);
        assertFalse(worker.isAlive(), "worker thread must terminate after the workload ends");
        assertTrue(worker.isDaemon(), "worker must be a daemon thread (JVM exit never blocked)");
    }

    /**
     * (ii) 接线钉：2 参缺省委托 3 参缺省常量路径——回调线程为 nop-graph-betweenness- 前缀
     * daemon 工作线程；缺省常量 30000；2 参结果 == 3 参(30000) 结果。
     */
    @Test
    @Timeout(60)
    void testW_08_twoArgEntryWiredToDefaultTimeoutPath() throws Exception {
        assertEquals(30000L, BetweennessCentrality.DEFAULT_BETWEENNESS_TIMEOUT_MS,
                "default envelope must match the LeidenConfig family default (30000ms)");

        InMemoryGraph g = buildSmallGraph();
        ProbeGraph probe = new ProbeGraph(g);
        Set<String> nodes = g.nodeSet();

        Map<String, Double> twoArg = BetweennessCentrality.compute(probe, nodes);

        assertFalse(probe.callbackThreads.isEmpty(), "computation must query the graph");
        for (Thread t : probe.callbackThreads) {
            assertTrue(t.getName().startsWith("nop-graph-betweenness-"),
                    "all graph callbacks must run on the named daemon worker, got: "
                            + t.getName());
            assertTrue(t.isDaemon(), "the default-path worker must be daemon");
        }

        Map<String, Double> threeArg = BetweennessCentrality.compute(g, nodes,
                BetweennessCentrality.DEFAULT_BETWEENNESS_TIMEOUT_MS);
        assertEquals(threeArg, twoArg,
                "2-arg entry must delegate the 3-arg default-constant path (same scores)");
    }

    /**
     * (iii) 异常透传钉：任务体抛出的 NopException 原码 + bizFatal 原样透出，无
     * ExecutionException 外壳。
     */
    @Test
    @Timeout(60)
    void testW_08_taskBodyNopExceptionPassesThroughUnwrapped() {
        InMemoryGraph g = buildSmallGraph();
        ProbeGraph probe = new ProbeGraph(g);
        probe.throwOnProbe = (NopException) new NopException(PROBE_ERROR)
                .param("probe", "betweenness-task-body")
                .bizFatal(true);
        Set<String> nodes = g.nodeSet();

        NopException e = org.junit.jupiter.api.Assertions.assertThrows(NopException.class,
                () -> BetweennessCentrality.compute(probe, nodes, 30_000L));

        assertEquals(PROBE_ERROR.getErrorCode(), e.getErrorCode(),
                "task-body NopException must pass through with its original code, got: "
                        + e.getErrorCode());
        assertTrue(e.isBizFatal(), "original bizFatal flag must be preserved");
        assertFalse(e.getClass().getName().contains("ExecutionException"),
                "the caller face must never see an ExecutionException shell");
    }
}
