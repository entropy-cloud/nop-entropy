/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.graph.dag;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.collections.MutableIntArray;
import io.nop.commons.util.CollectionHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
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
    private List<List<String>> removedEdges = new ArrayList<>();

    private boolean useControlNode = false;

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

    public DagAnalyzer useControlNode(boolean b) {
        this.useControlNode = b;
        return this;
    }

    public List<List<String>> getRemovedEdges() {
        return removedEdges;
    }

    public void analyze() {
        checkStartReachable();
        checkLoop();
        initDagNodes();
        initNodeDepth();
        if (useControlNode)
            initControlNode();
        initEndNodes();
        dag.setLoopEdges(removedEdges);
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
                    removedEdges.add(Arrays.asList(from, to));
                });
            }

            clearNext(aData);

            for (int j = i + 1; j < n; j++) {
                if (data[j].prevIndexes.isEmpty()) {
                    clearNext(data[j]);
                }
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
            if (node.getNodeIndex() < 0)
                ret.add(node.getName());
        });
        return ret;
    }

    private void initDagNodes() {
        // 从next集合中删除导致循环的边
        for (List<String> edge : removedEdges) {
            String from = edge.get(0);
            String to = edge.get(1);

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

    private void initNodeDepth() {
        DagNode rootNode = dag.getRootNode();
        _initNodeDepth(rootNode, 0);
    }

    private void _initNodeDepth(DagNode node, int depth) {
        // 如果depth有值，则表示经过其他途径已经遍历过了，不再需要继续遍历
        if (node.getDepth() >= 0)
            return;
        node.setDepth(depth);

        List<DagNode> nextList = dag.getNextNodes(node);
        if (nextList != null) {
            depth++;
            for (DagNode next : nextList) {
                _initNodeDepth(next, depth);
            }
        }
    }

    private void initEndNodes() {
        Set<String> endNodes = new HashSet<>();

        dag.forEachNode(node -> {
            if (node.getName().equals(dag.getRootNodeName()))
                return;

            if (node.getNextNodeNames() == null || node.getNextNodeNames().isEmpty()) {
                endNodes.add(node.getName());
            }
        });
        dag.setEndNodeNames(endNodes);
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
        dag.forEachNode(node -> {
            if (node.getPrevNodeNames() != null) {
                if (node.getPrevNodeNames().size() == 1) {
                    // 如果只存在唯一的父节点，则控制节点就是父节点
                    node.setControlNodeName(CollectionHelper.first(node.getPrevNodeNames()));
                } else {
                    // 具有多个父节点时查找控制节点
                    node.setControlNodeName(findControlNode(node));
                }
            }
        });
    }

    private String findControlNode(DagNode node) {
        // 将前驱节点按照深度进行排序
        Queue<DagNode> queue = new PriorityQueue<>((o1, o2) -> {
            return -Integer.compare(o1.getDepth(), o2.getDepth());
        });
        dag.forEachPrevNode(node, prev -> {
            queue.add(prev);
        });

        // 更深的节点替换为它的父节点
        while (queue.size() > 1) {
            DagNode prev = queue.poll();
            dag.forEachPrevNode(prev, pprev -> {
                queue.add(pprev);
            });
        }

        // 当不同分支不断查找父节点，到达某一深度时合并为同一个节点，这就是控制节点。
        // 一个节点可以具有多个父节点，但是只会有一个控制节点。起始节点没有控制节点
        if (queue.size() == 1) {
            return queue.poll().getName();
        }
        return null;
    }
}
