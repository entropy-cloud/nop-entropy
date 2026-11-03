package io.nop.graph.algorithm;

import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.concurrent.thread.NamedThreadFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.graph.api.CommunityInfo;
import io.nop.graph.api.CommunityResult;
import io.nop.graph.api.Edge;
import io.nop.graph.api.IGraph;
import io.nop.graph.api.LeidenConfig;
import nl.cwts.networkanalysis.Clustering;
import nl.cwts.networkanalysis.LeidenAlgorithm;
import nl.cwts.networkanalysis.Network;
import nl.cwts.util.LargeIntArray;

/**
 * Leiden 社区检测算法。
 *
 * 将 IGraph 转为 CWTS Network，运行 LeidenAlgorithm，
 * 返回 CommunityResult。不含 dominantPackage/label 等业务逻辑。
 *
 * <p>超时包络（5.6-W-04，plan 2530 设计裁定①）：计算体（CWTS findClustering）在每调用
 * 一次性 daemon 工作线程（NamedThreadFactory，nop-commons concurrent/thread 使用面，前缀
 * {@link #LEIDEN_WORKER_THREAD_PREFIX}——与 8.36 W-08 Betweenness 设施同纪律，独立实例
 * 不共享）上执行，调用线程按墙钟等待。语义边界：底层 CWTS Leiden 计算不可中断
 * （networkanalysis-1.3.0 全库 0 interrupt 反汇编在案），超时仅为放弃等待 + 既有
 * LabelPropagation 降级——放弃后计算在后台 daemon 线程继续至自然结束（不阻塞 JVM 退出、
 * 无线程池驻留、按名可辨识），放弃后计算自身的失败不再上报；超时降级的结果语义与可观测
 * 标签零变更（detect 出口维持 "LEIDEN" 字面量重建）。{@code timeoutMs <= 0} 维持既有
 * 同步直跑分支（调用方显式传参面）。
 */
public final class LeidenDetector {

    private static final Logger LOG = LoggerFactory.getLogger(LeidenDetector.class);

    /**
     * 工作线程命名前缀（幽灵按名可辨识：NamedThreadFactory 产物名形如
     * nop-graph-leiden-&lt;pool&gt;-&lt;n&gt;，与 nop-graph-betweenness- 同族、独立实例）。
     */
    static final String LEIDEN_WORKER_THREAD_PREFIX = "nop-graph-leiden";

    private LeidenDetector() {
    }

    public static CommunityResult detect(IGraph graph, Set<String> nodes, LeidenConfig config) {
        if (graph == null || nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("graph and nodes must not be null or empty");
        }
        if (nodes.size() < 2) {
            throw new IllegalArgumentException("Leiden requires at least 2 nodes");
        }

        long startTime = CoreMetrics.currentTimeMillis();

        List<String> indexNodeMap = new ArrayList<>(nodes);
        Map<String, Integer> nodeIndexMap = new HashMap<>();
        for (int i = 0; i < indexNodeMap.size(); i++) {
            nodeIndexMap.put(indexNodeMap.get(i), i);
        }

        List<int[]> edgeList = new ArrayList<>();
        Set<String> seenEdges = new HashSet<>();
        for (String node : indexNodeMap) {
            for (Edge edge : graph.getOutEdges(node)) {
                String target = edge.getTargetId();
                if (nodeIndexMap.containsKey(target)) {
                    int src = nodeIndexMap.get(node);
                    int tgt = nodeIndexMap.get(target);
                    String key = src + "_" + tgt;
                    if (src != tgt && seenEdges.add(key)) {
                        edgeList.add(new int[]{src, tgt});
                    }
                }
            }
        }

        CommunityResult result = runLeiden(edgeList, indexNodeMap.size(), indexNodeMap,
                graph, config);

        return new CommunityResult(result.getCommunities(),
                nodes.size(),
                result.getTotalCommunities(),
                result.getAverageCohesion(),
                result.getModularity(),
                "LEIDEN",
                CoreMetrics.currentTimeMillis() - startTime);
    }

    private static CommunityResult runLeiden(List<int[]> edgeList, int nNodes,
                                              List<String> indexNodeMap,
                                              IGraph graph, LeidenConfig config) {
        try {
            LargeIntArray[] edges = new LargeIntArray[2];
            edges[0] = new LargeIntArray(edgeList.size());
            edges[1] = new LargeIntArray(edgeList.size());

            for (int i = 0; i < edgeList.size(); i++) {
                int[] edge = edgeList.get(i);
                edges[0].set(i, edge[0]);
                edges[1].set(i, edge[1]);
            }

            Network network = new Network(nNodes, false, edges, false, false);

            LeidenAlgorithm leiden = new LeidenAlgorithm(
                    config.getResolution(),
                    config.getMaxIterations(),
                    0.01,
                    new Random()
            );

            Clustering clustering;
            if (config.getTimeoutMs() > 0) {
                clustering = runWithTimeout(() -> leiden.findClustering(network),
                        config.getTimeoutMs(), indexNodeMap.size());
            } else {
                clustering = leiden.findClustering(network);
            }

            double modularity = leiden.calcQuality(network, clustering);

            List<CommunityInfo> communities = convertClustering(clustering, indexNodeMap,
                    graph, config);

            if (communities.isEmpty()) {
                LOG.debug("Leiden returned no communities, falling back to LabelPropagation");
                return LabelPropagation.detect(toIGraph(graph, indexNodeMap),
                        new LinkedHashSet<>(indexNodeMap), config.getMaxIterations());
            }

            double avgCohesion = communities.stream()
                    .mapToDouble(CommunityInfo::getCohesion).average().orElse(0);

            return new CommunityResult(communities, nNodes, communities.size(),
                    avgCohesion, modularity, "LEIDEN", 0);

        } catch (Exception e) {
            LOG.warn("Leiden algorithm failed, falling back to LabelPropagation", e);
            return LabelPropagation.detect(toIGraph(graph, indexNodeMap),
                    new LinkedHashSet<>(indexNodeMap), config.getMaxIterations());
        }
    }

    private static List<CommunityInfo> convertClustering(Clustering clustering,
                                                           List<String> indexNodeMap,
                                                           IGraph graph,
                                                           LeidenConfig config) {
        int[][] nodesPerCluster = clustering.getNodesPerCluster();
        List<CommunityInfo> communities = new ArrayList<>();

        for (int clusterIdx = 0; clusterIdx < nodesPerCluster.length; clusterIdx++) {
            int[] nodeIndices = nodesPerCluster[clusterIdx];
            Set<String> clusterNodes = new LinkedHashSet<>();

            for (int nodeIdx : nodeIndices) {
                if (nodeIdx >= 0 && nodeIdx < indexNodeMap.size()) {
                    clusterNodes.add(indexNodeMap.get(nodeIdx));
                }
            }

            if (clusterNodes.size() < config.getMinCommunitySize()) {
                continue;
            }

            double cohesion = calculateCohesion(clusterNodes, graph);
            communities.add(new CommunityInfo(clusterIdx, clusterNodes, cohesion));
        }

        communities.sort((a, b) -> Integer.compare(b.getNodeCount(), a.getNodeCount()));
        return communities;
    }

    static double calculateCohesion(Set<String> cluster, IGraph graph) {
        if (cluster.size() < 2) return 1.0;

        int internalEdges = 0;
        int externalEdges = 0;

        for (String node : cluster) {
            for (Edge edge : graph.getOutEdges(node)) {
                if (cluster.contains(edge.getTargetId())) {
                    internalEdges++;
                } else {
                    externalEdges++;
                }
            }
        }

        int total = internalEdges + externalEdges;
        return total == 0 ? 1.0 : (double) internalEdges / total;
    }

    private static IGraph toIGraph(IGraph graph, List<String> nodes) {
        return graph;
    }

    /**
     * 超时包络（5.6-W-04，设计裁定①）：每调用一次性 daemon 工作线程 + FutureTask——无池、
     * 无队列、无驻留，完成即亡。任务体仅 findClustering（包络范围显式声明，图转换与
     * cohesion 留在调用线程）。timeoutMs &lt;= 0 不会到达本方法（既有同步直跑分支承载）。
     *
     * <p>生命周期配对闭环（C6-CL-031 裁定兑现）：成功路径线程自然终结；超时/中断路径
     * cancel(false) 后 daemon 线程继续至自然结束、不阻塞 JVM 退出；awaitTermination
     * 不采（CWTS 无中断检查点，awaitTermination 仅延长调用方阻塞——负修复）。
     */
    private static <T> T runWithTimeout(Callable<T> task, long timeoutMs, int nodeCount)
            throws Exception {
        FutureTask<T> future = new FutureTask<>(task);
        Thread worker = new NamedThreadFactory(LEIDEN_WORKER_THREAD_PREFIX, true).newThread(future);
        worker.start();
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // give up waiting; the non-interruptible CWTS computation keeps running to
            // natural completion on the one-shot daemon thread (never blocks JVM exit);
            // the rethrown TimeoutException drives the existing LabelPropagation fallback
            LOG.warn("nop.graph.leiden-timeout: give up waiting after {}ms, nodes={}; the "
                            + "underlying CWTS Leiden computation is not interruptible and "
                            + "finishes in the background on a daemon thread (timeout only "
                            + "abandons the wait; the LabelPropagation fallback takes over)",
                    timeoutMs, nodeCount, e);
            future.cancel(false);
            throw e;
        } catch (InterruptedException e) {
            // restore the interrupt flag (cleared by get()) and cancel the abandoned
            // computation; the runLeiden fallback still degrades to LabelPropagation
            Thread.currentThread().interrupt();
            future.cancel(false);
            throw e;
        } catch (ExecutionException e) {
            // unwrap unconditionally: the caller face never sees an ExecutionException shell
            // and the runLeiden degrade WARN logs the root cause, not the wrapper.
            // findClustering declares no checked exceptions, so a non-Exception cause can
            // only be an Error, which must escape the degrade path (wrapping OOM etc. into
            // a degrade would mask the primary failure - explicit behavior correction point)
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw (Error) cause;
        }
    }
}
