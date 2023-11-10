package io.nop.core.model.graph.dag;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.collections.bit.IBitSet;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.objects.Pair;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static io.nop.core.CoreErrors.ARG_NODE_NAMES;
import static io.nop.core.CoreErrors.ERR_GRAPH_NODES_NOT_REACHABLE;

/**
 * 原始的图结构中有可能包含环，分析之后会删除一些连接使其成为环
 */
public class DagAnalyzer {
    private final Dag dag;

    private final DagNode[] nodes;

    // 删除哪些next连接才能得到一个无循环的DAG结构。
    private List<Pair<String, String>> removedLinks = new ArrayList<>();

    private final IBitSet analyzedFlags;

    public DagAnalyzer(Dag dag) {
        this.dag = dag;
        this.nodes = new DagNode[dag.size()];
        this.analyzedFlags = CollectionHelper.newFixedBitSet(dag.size());
    }

    public void analyze() {
        checkAllReachable();
        checkLoop();
    }

    private void checkAllReachable() {
        int index = 0;
        Iterator<DagNode> it = dag.widthFirstIterator();
        while (it.hasNext()) {
            DagNode node = it.next();
            this.nodes[index] = node;
            node.setNodeIndex(index++);
        }

        if (index != dag.size()) {
            // 通过宽度优先遍历无法达到所有节点
            throw new NopException(ERR_GRAPH_NODES_NOT_REACHABLE)
                    .param(ARG_NODE_NAMES, getUnreachableNodes());
        }
    }

    private void checkLoop() {
        DagNode root = nodes[0];

    }

    private List<String> getUnreachableNodes() {
        List<String> ret = new ArrayList<>();
        dag.forEachNode(node -> {
            if (node.getNodeIndex() <= 0)
                ret.add(node.getName());
        });
        return ret;
    }
}
