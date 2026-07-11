package io.nop.graph.adapter;

import java.util.ArrayList;
import java.util.List;

import io.nop.graph.api.Edge;
import io.nop.graph.api.IGraph;
import io.nop.core.model.graph.IDirectedGraphView;

/**
 * 将 nop-core 的 IDirectedGraphView 包装为 IGraph。
 *
 * 这是一个单向适配器：IDirectedGraphView → IGraph。
 * IDirectedGraphView 的顶点类型 V 通过 String.valueOf 转为 String ID。
 */
public final class DirectedGraphAdapter {

    private DirectedGraphAdapter() {
    }

    /**
     * 将 nop-core 的 IDirectedGraphView 包装为 IGraph。
     *
     * @param graph nop-core 有向图视图
     * @return IGraph 接口实现
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static IGraph fromDirectedGraph(IDirectedGraphView graph) {
        if (graph == null) {
            throw new IllegalArgumentException("graph must not be null");
        }

        return new IGraph() {
            @Override
            public List<Edge> getOutEdges(String nodeId) {
                List<? extends io.nop.core.model.graph.IEdge<?>> edges = graph.getOutwardEdges(nodeId);
                List<Edge> result = new ArrayList<>();
                if (edges != null) {
                    for (io.nop.core.model.graph.IEdge<?> edge : edges) {
                        result.add(new Edge(
                                String.valueOf(edge.getSource()),
                                String.valueOf(edge.getTarget())));
                    }
                }
                return result;
            }

            @Override
            public List<Edge> getInEdges(String nodeId) {
                List<? extends io.nop.core.model.graph.IEdge<?>> edges = graph.getInwardEdges(nodeId);
                List<Edge> result = new ArrayList<>();
                if (edges != null) {
                    for (io.nop.core.model.graph.IEdge<?> edge : edges) {
                        result.add(new Edge(
                                String.valueOf(edge.getSource()),
                                String.valueOf(edge.getTarget())));
                    }
                }
                return result;
            }
        };
    }
}
