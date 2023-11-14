package io.nop.core.model.graph.dag;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.collections.MutableIntArray;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.objects.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static io.nop.core.CoreErrors.ARG_NODE_NAMES;
import static io.nop.core.CoreErrors.ERR_GRAPH_NODES_NOT_REACHABLE;

/**
 * 原始的图结构中有可能包含环，分析之后会删除一些连接使其成为环
 */
public class DagAnalyzer {
    private final Dag dag;

    private final AnalyzeData[] data;

    // 删除哪些next连接才能得到一个无循环的DAG结构。
    private List<Pair<String, String>> removedEdges = new ArrayList<>();

    static class AnalyzeData {
        DagNode node;
        MutableIntArray prevIndexes = new MutableIntArray();

        boolean nextCleared;

        AnalyzeData(DagNode node) {
            this.node = node;
        }
    }

    public DagAnalyzer(Dag dag) {
        this.dag = dag;
        this.data = new AnalyzeData[dag.size()];
    }

    public List<Pair<String, String>> getRemovedEdges() {
        return removedEdges;
    }

    public void analyze() {
        checkStartReachable();
        checkLoop();
        initDagNodes();
        initControlNode();
        dag.setRemovedEdges(removedEdges);
    }

    private void checkStartReachable() {
        int index = 0;
        Iterator<DagNode> it = dag.breathFirstIterator(dag.getRootNode());
        while (it.hasNext()) {
            DagNode node = it.next();
            this.data[index] = new AnalyzeData(node);
            node.setNodeIndex(index++);
        }

        if (index != dag.size()) {
            // 通过宽度优先遍历无法达到所有节点
            throw new NopException(ERR_GRAPH_NODES_NOT_REACHABLE)
                    .param(ARG_NODE_NAMES, getUnreachableNodes());
        }

        dag.forEachNode(node -> {
            dag.forEachNextNode(node, next -> {
                this.data[next.getNodeIndex()].prevIndexes.add(node.getNodeIndex());
            });
        });
    }

    private void checkLoop() {
        for (int i = 0, n = data.length; i < n; i++) {
            AnalyzeData aData = data[i];
            if (!aData.prevIndexes.isEmpty()) {
                String to = aData.node.getName();
                aData.prevIndexes.forEach(prevIndex -> {
                    String from = data[prevIndex].node.getName();
                    removedEdges.add(Pair.of(from, to));
                });
            }

            clearNext(aData);

            for (int j = i + 1; j < n; j++) {
                clearNext(data[j]);
            }
        }
    }

    private void clearNext(AnalyzeData aData) {
        if (aData.nextCleared)
            return;

        DagNode node = aData.node;
        dag.forEachNextNode(node, next -> {
            data[next.getNodeIndex()].prevIndexes.removeValue(node.getNodeIndex());
        });
        aData.nextCleared = true;
    }

    public List<String> getUnreachableNodes() {
        List<String> ret = new ArrayList<>();
        dag.forEachNode(node -> {
            if (node.getNodeIndex() <= 0)
                ret.add(node.getName());
        });
        return ret;
    }

    private void initDagNodes() {
        // 从next集合中删除导致循环的边
        for (Pair<String, String> edge : removedEdges) {
            String from = edge.getFirst();
            String to = edge.getSecond();

            dag.getNode(from).removeNextNode(to);
        }

        // 初始化节点的prevNodeNames集合
        dag.forEachNode(node -> {
            dag.forEachNextNode(node, next -> {
                next.addPrevNode(node.getName());
            });
        });

        dag.forEachNode(node -> {
            if (!hasInternalNode(node.getNextNodeNames())) {
                node.setNextNormalNodeNames(node.getNextNodeNames());
            } else {
                node.setNextNormalNodeNames(collectNormal(node, DagNode::getNextNodeNames));
            }

            if (!hasInternalNode(node.getPrevNodeNames())) {
                node.setPrevNormalNodeNames(node.getPrevNodeNames());
            } else {
                node.setPrevNormalNodeNames(collectNormal(node, DagNode::getPrevNodeNames));
            }
        });
    }

    private Set<String> collectNormal(DagNode node, Function<DagNode, Set<String>> nextFetcher) {
        Set<String> ret = new LinkedHashSet<>();
        collectNormal(ret, node, nextFetcher);
        return ret;
    }

    private void collectNormal(Set<String> ret, DagNode node, Function<DagNode, Set<String>> nextFetcher) {
        Set<String> next = nextFetcher.apply(node);
        if (next != null && !next.isEmpty()) {
            for (String name : next) {
                DagNode nextNode = dag.getNode(name);
                if (nextNode.isInternal()) {
                    collectNormal(ret, nextNode, nextFetcher);
                } else {
                    ret.add(name);
                }
            }
        }
    }

    private boolean hasInternalNode(Set<String> names) {
        if (names == null || names.isEmpty())
            return false;

        for (String name : names) {
            if (dag.getNode(name).isInternal())
                return true;
        }
        return false;
    }

    private void initControlNode() {
        Map<String, Set<String>> controlMap = new HashMap<>();
        dag.forEachNode(node -> {
            if (node.getPrevNodeNames() != null) {
                if (node.getPrevNodeNames().size() == 1) {
                    node.setControlNodeName(CollectionHelper.first(node.getPrevNodeNames()));
                } else {
                    controlMap.put(node.getName(), new HashSet<>(node.getPrevNodeNames()));
                }
            }
        });

        while (!controlMap.isEmpty()) {
            Iterator<Map.Entry<String, Set<String>>> it = controlMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Set<String>> entry = it.next();
                String name = entry.getKey();
                Set<String> prev = entry.getValue();
                if (prev.size() == 1) {
                    dag.getNode(name).setControlNodeName(CollectionHelper.first(prev));
                    it.remove();
                } else {
                    Set<String> joints = new HashSet<>();
                    advance(joints, prev);
                    if (joints.isEmpty()) {
                        it.remove();
                    } else {
                        entry.setValue(joints);
                    }
                }
            }
        }
    }

    private void advance(Set<String> joints, Set<String> prevNames) {
        for (String prevName : prevNames) {
            DagNode joint = dag.getPrevJoint(dag.getNode(prevName));
            if (joint != null) {
                joints.add(joint.getName());
            }
        }
    }
}
