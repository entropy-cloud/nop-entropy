/*
 * Copyright 2007 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// copy some function from Closure Compiler project

package io.nop.core.model.graph;

import io.nop.commons.util.StringHelper;

import java.util.Arrays;
import java.util.Collection;

public class GraphvizHelper {
    private static final String INDENT = "  ";
    private static final String ARROW = " -> ";
    private static final String LINE = " -- ";

    /**
     * Outputs a string in DOT format that presents the graph.
     *
     * @param graph Input graph.
     * @return A string in Dot format that presents the graph.
     */
    public static <V, E extends IEdge<V>> String toDot(IGraphvizAdapter<V> adapter, IGraphViewBase<V, E> graph,
                                                       boolean directed, String name) {
        StringBuilder builder = new StringBuilder();
        builder.append(directed ? "digraph" : "graph");
        builder.append(INDENT);
        builder.append(name);
        builder.append(" {\n");
        builder.append(INDENT);
        builder.append("node [color=lightblue2, style=filled];\n");

        final String edgeSymbol = directed ? ARROW : LINE;

        Collection<V> nodes = graph.vertexSet();

        String[] nodeNames = new String[nodes.size()];

        int i = 0;
        for (V gNode : nodes) {
            nodeNames[i++] = buildNodeName(gNode, adapter);
        }

        // We sort the nodes so we get a deterministic output every time regardless
        // of the implementation of the graph data structure.
        Arrays.sort(nodeNames);

        for (String nodeName : nodeNames) {
            builder.append(INDENT);
            builder.append(nodeName);
            builder.append(";\n");
        }

        Collection<E> edges = graph.edgeSet();

        String[] edgeNames = new String[edges.size()];

        i = 0;
        for (E edge : edges) {
            edgeNames[i] = adapter.getNodeId(edge.getSource()) + edgeSymbol + adapter.getNodeId(edge.getTarget());
            i++;
        }

        // Again, we sort the edges as well.
        Arrays.sort(edgeNames);

        for (String edgeName : edgeNames) {
            builder.append(INDENT);
            builder.append(edgeName);
            builder.append(";\n");
        }

        builder.append("}\n");
        return builder.toString();
    }

    private static <V> String buildNodeName(V node, IGraphvizAdapter<V> adapter) {
        StringBuilder sb = new StringBuilder();
        String nodeId = adapter.getNodeId(node);
        sb.append(nodeId);
        String label = adapter.getNodeLabel(node);
        if (label == null)
            label = nodeId;
        sb.append("[label=\"").append(StringHelper.escapeJava(label)).append('"');
        String color = adapter.getNodeColor(node);
        if (color != null) {
            sb.append(" color=\"").append(color).append('"');
        }
        sb.append("]");
        return sb.toString();
    }
}
