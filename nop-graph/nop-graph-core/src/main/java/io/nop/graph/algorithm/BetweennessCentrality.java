package io.nop.graph.algorithm;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.concurrent.thread.NamedThreadFactory;
import io.nop.graph.NopGraphErrors;
import io.nop.graph.api.Edge;
import io.nop.graph.api.IGraph;

/**
 * 介数中心性算法，委托 JGraphT BetweennessCentrality。
 *
 * <p>超时包络（5.6-W-08，plan 2529 裁定⑥）：计算体（jgraph 构建 + Brandes + 防御拷贝）
 * 在每调用一次性 daemon 工作线程（NamedThreadFactory，nop-commons concurrent/thread 使用面）
 * 上执行，调用线程按墙钟等待。语义边界：超时仅放弃等待——底层 Brandes 计算不可中断
 * （JGraphT 1.5.2 零 interrupt 引用），将在后台 daemon 线程继续至自然结束（不阻塞 JVM
 * 退出，无线程池/队列/驻留），放弃后计算自身的失败不再上报；超时对同一图确定性复现，
 * 盲重试无收益且每轮空烧超时预算——超时错误为 bizFatal。
 *
 * <p>生产接线（评审 Major-1）：2 参 {@link #compute(IGraph, Set)} 缺省委托 3 参路径，
 * 超时来源 = 配置变量 {@code nop.graph.betweenness-timeout-ms}（每次调用读取，变更即时
 * 生效）；未配置回落 {@link #DEFAULT_BETWEENNESS_TIMEOUT_MS}（30000，LeidenConfig 家族
 * 一致性）；配置 {@code <= 0} = 显式关闭墙钟包络（调用线程同步直跑——风险为 O(V·E) 无界
 * 占用回归，禁静默缺省）。既有 2 参调用方（GraphMetricMaterializer/CodeGraphService）
 * 零改动自动生效。
 */
public final class BetweennessCentrality {

    private static final Logger LOG = LoggerFactory.getLogger(BetweennessCentrality.class);

    /**
     * 缺省墙钟包络（毫秒）。缺省值 = 生产行为变更声明：现网 Betweenness 等待无上界，
     * 未配置时受 30s 上界约束；合法超 30s 的计算以显式错误终止，补救路径 = 调大
     * {@code nop.graph.betweenness-timeout-ms}、显式 3 参 overload、或缩减节点集
     * （既有 V 守卫不变）。
     */
    public static final long DEFAULT_BETWEENNESS_TIMEOUT_MS = 30000;

    private static final SourceLocation s_loc = SourceLocation.fromClass(BetweennessCentrality.class);
    private static final String WORKER_THREAD_PREFIX = "nop-graph-betweenness";
    private static final IConfigReference<Long> CFG_BETWEENNESS_TIMEOUT_MS =
            AppConfig.varRef(s_loc, "nop.graph.betweenness-timeout-ms", Long.class,
                    DEFAULT_BETWEENNESS_TIMEOUT_MS);

    private BetweennessCentrality() {
    }

    /**
     * 计算图中各节点的介数中心性（缺省超时包络，见类 javadoc）。
     *
     * @param graph 图
     * @param nodes 要计算的节点集
     * @return nodeId → betweenness score
     */
    public static Map<String, Double> compute(IGraph graph, Set<String> nodes) {
        long configured = CFG_BETWEENNESS_TIMEOUT_MS.get();
        if (configured <= 0) {
            // explicit disable: synchronous execution on the calling thread, no envelope
            LOG.warn("nop.graph.betweenness-timeout-disabled: configured <= 0, betweenness "
                    + "runs synchronously without a wall-clock envelope (O(V*E) occupation risk)");
            return doCompute(graph, nodes);
        }
        return compute(graph, nodes, configured);
    }

    /**
     * 计算图中各节点的介数中心性，受显式墙钟包络约束。
     *
     * <p>入参校验保持在调用线程：{@code timeoutMs <= 0} 为非法（显式传入必须为正值；
     * 「不设超时」档仅经 2 参路径的配置变量 {@code nop.graph.betweenness-timeout-ms <= 0}
     * 表达，非静默禁用）。
     *
     * @param graph     图
     * @param nodes     要计算的节点集
     * @param timeoutMs 墙钟包络（毫秒，必须为正）
     * @return nodeId → betweenness score
     */
    public static Map<String, Double> compute(IGraph graph, Set<String> nodes, long timeoutMs) {
        if (graph == null || nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("graph and nodes must not be null or empty");
        }
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be positive, got: " + timeoutMs);
        }

        FutureTask<Map<String, Double>> task = new FutureTask<>(() -> doCompute(graph, nodes));
        Thread worker = new NamedThreadFactory(WORKER_THREAD_PREFIX, true).newThread(task);
        worker.start();
        try {
            return task.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // give up waiting; the non-interruptible Brandes keeps running to natural
            // completion on the one-shot daemon thread (never blocks JVM exit)
            LOG.warn("nop.graph.betweenness-timeout: give up waiting after {}ms, nodes={}; the "
                            + "underlying Brandes computation is not interruptible and finishes in "
                            + "the background on a daemon thread", timeoutMs, nodes.size(), e);
            throw (NopException) new NopException(NopGraphErrors.ERR_GRAPH_COMPUTE_TIMEOUT)
                    .param(NopGraphErrors.ARG_TIMEOUT_MS, timeoutMs)
                    .param(NopGraphErrors.ARG_NODE_COUNT, nodes.size())
                    .bizFatal(true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            task.cancel(false);
            throw new NopException(NopGraphErrors.ERR_GRAPH_COMPUTE_INTERRUPTED, e);
        } catch (ExecutionException e) {
            // unwrap unconditionally: the caller face never sees an ExecutionException shell.
            // doCompute declares no checked exceptions, so the checked fall-through is
            // defensive only (a smuggled foreign Throwable); both registered NopGraphErrors
            // codes carry timeout/interrupt semantics that would falsify it - the platform
            // idiom NopException.adapt rethrows Error/RuntimeException as-is and wraps a
            // checked foreign throwable as NopWrapException with the cause preserved
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof NopException) {
                throw (NopException) cause;
            }
            throw NopException.adapt(cause);
        }
    }

    /**
     * 计算体：jgraph 构建（经 IGraph 回调）+ Brandes + 防御拷贝——全部纳入超时管辖的
     * 工作线程，使「计算确实在包装线程上执行」成为接线钉的可观测事实（裁定⑥.7）。
     */
    private static Map<String, Double> doCompute(IGraph graph, Set<String> nodes) {
        Graph<String, DefaultEdge> jgraph = new DefaultDirectedGraph<>(DefaultEdge.class);

        for (String node : nodes) {
            jgraph.addVertex(node);
        }

        for (String node : nodes) {
            for (Edge edge : graph.getOutEdges(node)) {
                String target = edge.getTargetId();
                if (nodes.contains(target)) {
                    try {
                        jgraph.addEdge(node, target);
                    } catch (IllegalArgumentException e) {
                        // duplicate edge, skip
                    }
                }
            }
        }

        @SuppressWarnings("unchecked")
        org.jgrapht.alg.scoring.BetweennessCentrality<String, DefaultEdge> bc =
                new org.jgrapht.alg.scoring.BetweennessCentrality<>(jgraph, false);
        Map<String, Double> scores = bc.getScores();

        return new HashMap<>(scores);
    }
}
