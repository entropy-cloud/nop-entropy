package io.nop.graph.algorithm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.nop.graph.api.Edge;
import io.nop.graph.api.IGraph;

/**
 * Tarjan 强连通分量算法（迭代式，防栈溢出）。
 */
public final class TarjanSCC {

    private TarjanSCC() {
    }

    /**
     * 计算图中的所有强连通分量。
     *
     * @param graph 图
     * @param nodes 要分析的节点集
     * @return SCC 列表，每个 SCC 是一个节点 ID 集合
     */
    public static List<Set<String>> compute(IGraph graph, Set<String> nodes) {
        if (graph == null || nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("graph and nodes must not be null or empty");
        }

        Map<String, Integer> index = new HashMap<>();
        Map<String, Integer> lowLink = new HashMap<>();
        Set<String> onStack = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        List<Set<String>> result = new ArrayList<>();
        int[] counter = {0};

        for (String node : nodes) {
            if (!index.containsKey(node)) {
                strongconnect(graph, node, index, lowLink, onStack, stack, result, counter);
            }
        }

        return result;
    }

    private static void strongconnect(IGraph graph, String start,
                                       Map<String, Integer> index,
                                       Map<String, Integer> lowLink,
                                       Set<String> onStack,
                                       Deque<String> stack,
                                       List<Set<String>> result,
                                       int[] counter) {
        Deque<Object[]> callStack = new ArrayDeque<>();
        callStack.push(new Object[]{start, 0, false});

        while (!callStack.isEmpty()) {
            Object[] frame = callStack.pop();
            String v = (String) frame[0];
            int edgeIdx = (Integer) frame[1];
            boolean returning = (Boolean) frame[2];

            if (!returning && !index.containsKey(v)) {
                index.put(v, counter[0]);
                lowLink.put(v, counter[0]);
                counter[0]++;
                stack.push(v);
                onStack.add(v);
            }

            List<Edge> outEdges = graph.getOutEdges(v);
            boolean pushedChild = false;

            for (int i = edgeIdx; i < outEdges.size(); i++) {
                String w = outEdges.get(i).getTargetId();
                if (!index.containsKey(w)) {
                    callStack.push(new Object[]{v, i + 1, true});
                    callStack.push(new Object[]{w, 0, false});
                    pushedChild = true;
                    break;
                } else if (onStack.contains(w)) {
                    lowLink.put(v, Math.min(lowLink.get(v), index.get(w)));
                }
            }

            if (!pushedChild) {
                if (returning && edgeIdx > 0 && edgeIdx - 1 < outEdges.size()) {
                    String w = outEdges.get(edgeIdx - 1).getTargetId();
                    if (lowLink.containsKey(w)) {
                        lowLink.put(v, Math.min(lowLink.get(v), lowLink.get(w)));
                    }
                }

                if (lowLink.get(v).equals(index.get(v))) {
                    Set<String> scc = new LinkedHashSet<>();
                    String w;
                    do {
                        w = stack.pop();
                        onStack.remove(w);
                        scc.add(w);
                    } while (!w.equals(v));
                    result.add(scc);
                }
            }
        }
    }
}
