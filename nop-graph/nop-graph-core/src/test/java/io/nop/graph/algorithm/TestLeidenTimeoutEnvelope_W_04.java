package io.nop.graph.algorithm;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.nop.graph.api.CommunityResult;
import io.nop.graph.api.LeidenConfig;
import io.nop.graph.impl.InMemoryGraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 5.6-W-04（plan 2530 Phase 2 组 A / 设计裁定①）LeidenDetector runWithTimeout 超时包络
 * 回归钉（三形态，C10 缺保护面 P10-P1-4(a)+(b) 随本体同 diff）。
 *
 * <p>(a) 行为绿锚（降级观测，不依赖标签——R1 Blocker-1 选型 (a)）：timeout=1ms + 中图
 * 合成夹具 → detect 在超时上界内返回（防挂 &lt;10s）+ 结果非空且社区节点覆盖全部节点
 * （LabelPropagation 全覆盖语义）+ {@code algorithmUsed=="LEIDEN"}（既有字面量零变更锚，
 * TestCommunityAlgorithms 同断言形态；「超时降级后仍报 LEIDEN」为既有行为登记——本批
 * 缺陷面是幽灵线程不是标签）。
 *
 * <p>(b) 结构钉（主保护，2529 红⑥(i) 同款编译期构造形态红）：包内可见工作线程前缀常量
 * {@code LEIDEN_WORKER_THREAD_PREFIX == "nop-graph-leiden"}（与 W-08
 * nop-graph-betweenness 同族命名，不共享线程设施实例——2529 裁定⑥.8）。
 *
 * <p>(c) 幽灵按名可观测（辅断言，有界轮询）：扫描窗与 detect 执行窗重叠地按名轮询，
 * 断言超时路径上存在 {@code nop-graph-leiden-} 前缀的工作线程且为 daemon（JVM 退出永
 * 不被阻塞）。pre-fix 幽灵名为 {@code pool-N-thread-1} 且非 daemon——该前缀在 pre-fix
 * 不可能存在，红判据确定性成立。
 *
 * <p><b>偏差登记（裁定④ 红①(c) 执行期实测修订，plan 组 A 注记同步）</b>：设计预设
 * 「detect 返回后按名轮询 ≤5s」依赖 findClustering 残余时长 ≫ LabelPropagation 回退
 * 时长；亲测（strong-community 链族，38k~1.26M 边，resolution 0.1/1/10）CWTS
 * findClustering 暖机后时长恒小于回退 LabelPropagation 时长（F/L≈0.6）——detect 返回时
 * 幽灵已自然终结，post-return 轮询窗物理不可行。采最小偏离：扫描窗与 detect 执行窗重叠
 * （幽灵自提交起存活于 findClustering 全程，20ms 轮询窗 ≪ 实测 F≥300ms），断言语义
 * 不变（按名 + daemon + 有界轮询）；(b) 结构钉为主保护，不依赖时序。
 *
 * <p>语义边界（C10 义务，裁定①.6）：底层 CWTS Leiden 计算不可中断（networkanalysis-1.3.0
 * 全库 0 interrupt 反汇编在案），超时仅为放弃等待 + 既有 LabelPropagation 降级——放弃后
 * 计算在后台 daemon 线程继续至自然结束（不阻塞 JVM 退出、无线程池驻留、按名可辨识），
 * 放弃后计算自身的失败不再上报。本测试不钉死「可中断」的错误承诺。
 */
class TestLeidenTimeoutEnvelope_W_04 {

    /**
     * 强社区合成夹具：communityCount 个大小为 communitySize 的团（双向边）+ 首尾桥边。
     * 强社区结构使 LabelPropagation 快速收敛（回退时长下界），CWTS findClustering 的
     * 绝对时长随边数线性增长——为 (c) 的扫描窗提供可观测的计算体时长。
     */
    static InMemoryGraph buildCommunityGraph(int communityCount, int communitySize) {
        InMemoryGraph g = new InMemoryGraph();
        for (int ci = 0; ci < communityCount; ci++) {
            for (int a = 0; a < communitySize; a++) {
                String src = "n" + ci + "_" + a;
                for (int b = a + 1; b < communitySize; b++) {
                    g.addEdge(src, "n" + ci + "_" + b);
                    g.addEdge("n" + ci + "_" + b, src);
                }
            }
            g.addEdge("n" + ci + "_0", "n" + ((ci + 1) % communityCount) + "_0");
        }
        return g;
    }

    /**
     * (a) 行为绿锚：超时上界内返回 + 降级结果非空覆盖全部节点 + 既有标签字面量零变更。
     */
    @Test
    @Timeout(60)
    void testW_04_timeoutDegradesWithinBoundWithFullCoverageAndLiteralLabel() {
        InMemoryGraph g = buildCommunityGraph(100, 20);
        Set<String> nodes = g.nodeSet();
        LeidenConfig config = LeidenConfig.create().setTimeoutMs(1);

        long start = System.nanoTime();
        CommunityResult result = LeidenDetector.detect(g, nodes, config);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(elapsedMs < 10_000,
                "detect must return within the timeout bound, took " + elapsedMs + "ms");
        assertNotNull(result);
        assertFalse(result.getCommunities().isEmpty(),
                "degraded result must be non-empty (LabelPropagation fallback form)");

        Set<String> covered = new HashSet<>();
        for (var community : result.getCommunities()) {
            covered.addAll(community.getNodeIds());
        }
        assertEquals(nodes, covered,
                "degraded result must cover every node (LabelPropagation full coverage)");

        assertEquals("LEIDEN", result.getAlgorithmUsed(),
                "observable label is unchanged existing behavior (Blocker-1 selection (a))");
    }

    /**
     * (b) 结构钉（主保护）：包内可见工作线程前缀常量存在且取值与 Betweenness 设施同族。
     * pre-fix 常量不存在——编译期构造形态红（red form 见 _tmp/red-A-2530.log）。
     */
    @Test
    void testW_04_workerThreadPrefixConstantMatchesFacilityFamily() {
        assertEquals("nop-graph-leiden", LeidenDetector.LEIDEN_WORKER_THREAD_PREFIX,
                "worker prefix must match the nop-graph facility family (W-08 sibling, "
                        + "separate NamedThreadFactory instance)");
    }

    /**
     * (c) 幽灵按名可观测（辅断言，有界轮询——扫描窗与 detect 执行窗重叠，见类 javadoc
     * 偏差登记）：断言超时路径上存在 nop-graph-leiden- 前缀的 daemon 工作线程。
     */
    @Test
    @Timeout(120)
    void testW_04_ghostWorkerObservableByNameAndDaemon() throws Exception {
        InMemoryGraph g = buildCommunityGraph(2000, 20);
        Set<String> nodes = g.nodeSet();

        ConcurrentLinkedQueue<Thread> seen = new ConcurrentLinkedQueue<>();
        AtomicBoolean stop = new AtomicBoolean(false);
        Thread scanner = new Thread(() -> {
            // scanner-confined identity set: record each matching thread once
            Set<Thread> known = Collections.newSetFromMap(new IdentityHashMap<>());
            while (!stop.get()) {
                for (Thread t : Thread.getAllStackTraces().keySet()) {
                    String name = t.getName();
                    if (name.startsWith("nop-graph-leiden-") || name.startsWith("pool-")) {
                        if (known.add(t)) {
                            seen.add(t);
                        }
                    }
                }
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "w04-ghost-scanner");
        scanner.setDaemon(true);
        scanner.start();

        try {
            LeidenDetector.detect(g, nodes, LeidenConfig.create().setTimeoutMs(1));
        } finally {
            stop.set(true);
            scanner.join(5_000);
        }

        Set<String> named = new HashSet<>();
        for (Thread t : seen) {
            if (t.getName().startsWith("nop-graph-leiden-")) {
                named.add(t.getName());
                assertTrue(t.isDaemon(),
                        "the abandoned computation must run on a daemon thread (JVM exit "
                                + "never blocked), got: " + t.getName());
            }
        }
        assertFalse(named.isEmpty(),
                "a nop-graph-leiden- prefixed worker must be observable on the timeout path "
                        + "(pre-fix the ghost is pool-N-thread-1 and non-daemon - the prefix "
                        + "cannot exist, deterministic red)");
    }
}
