/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.graph.dag;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.model.graph.DefaultEdge;
import io.nop.core.model.graph.GraphBreadthFirstIterator;
import io.nop.core.model.graph.GraphDepthFirstIterator;
import io.nop.core.model.graph.GraphvizHelper;
import io.nop.core.model.graph.IGraphViewBase;
import io.nop.core.resource.component.AbstractFreezable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static io.nop.core.CoreErrors.ARG_NAME;
import static io.nop.core.CoreErrors.ERR_GRAPH_UNKNOWN_NODE;

@DataBean
public class Dag extends AbstractFreezable implements IGraphViewBase<DagNode, DefaultEdge<DagNode>> {
    public static final String DEFAULT_ROOT_NAME = "__root__";

    private String rootNodeName;
    private Map<String, DagNode> nodes = new LinkedHashMap<>();

    /**
     * 为了从一般性的图结构抽取得到DAG，需要删除哪些链接
     */
    private List<List<String>> loopEdges;

    /**
     * 没有后续节点的末端节点
     */
    private Set<String> endNodeNames;

    public Dag() {
    }

    public Dag(String rootName) {
        setRootNodeName(rootName);
        nodes.put(rootName, new DagNode(rootName));
    }

    @JsonIgnore
    public Set<String> getNodeNames() {
        return nodes.keySet();
    }

    public boolean containsLoop() {
        return !loopEdges.isEmpty();
    }

    public Iterator<DagNode> breathFirstIterator(DagNode node) {
        return new GraphBreadthFirstIterator<>(this::getNextNodes, node);
    }

    public Iterator<DagNode> depthFirstIterator(DagNode node) {
        return new GraphDepthFirstIterator<>(this::getNextNodes, node);
    }

    public List<List<String>> getLoopEdges() {
        return loopEdges;
    }

    void setLoopEdges(List<List<String>> loopEdges) {
        this.loopEdges = loopEdges;
    }

    @JsonIgnore
    public Set<String> getEndNodeNames() {
        return endNodeNames;
    }

    void setEndNodeNames(Set<String> endNodeNames) {
        this.endNodeNames = endNodeNames;
    }

    public String toDot() {
        return GraphvizHelper.toDot(DagNode::getName, this, true, "dag");
    }

    @Override
    public Collection<DagNode> vertexSet() {
        return nodes.values();
    }

    @Override
    public List<DefaultEdge<DagNode>> edgeSet() {
        List<DefaultEdge<DagNode>> list = new ArrayList<>();
        forEachNode(node -> {
            forEachNextNode(node, next -> {
                list.add(new DefaultEdge<>(node, next));
            });
        });
        return list;
    }

    @Override
    public List<DagNode> getTargetVertexes(DagNode node) {
        return getNextNodes(node);
    }

    public List<DagNode> getNextNodes(DagNode node) {
        Set<String> nextNames = node.getNextNodeNames();
        if (nextNames == null || nextNames.isEmpty())
            return Collections.emptyList();

        return nextNames.stream().map(this::requireNode).collect(Collectors.toList());
    }

    @JsonIgnore
    public Set<String> getNoDependNodeNames() {
        Set<String> names = new HashSet<>();
        forEachNode(node -> {
            if (!node.hasPrevNode()) {
                names.add(node.getName());
            } else if (node.getPrevNodeNames().size() == 1 && node.getPrevNodeNames().contains(rootNodeName)) {
                names.add(node.getName());
            }
        });
        return names;
    }

    public void forEachNextNode(DagNode node, Consumer<DagNode> action) {
        Set<String> nextNames = node.getNextNodeNames();
        if (nextNames == null || nextNames.isEmpty())
            return;

        nextNames.stream().forEach(name -> {
            action.accept(requireNode(name));
        });
    }

    public void forEachPrevNode(DagNode node, Consumer<DagNode> action) {
        Set<String> prevNames = node.getPrevNodeNames();
        if (prevNames == null || prevNames.isEmpty())
            return;

        prevNames.stream().forEach(name -> {
            action.accept(requireNode(name));
        });
    }

    @JsonIgnore
    public DagNode getRootNode() {
        return requireNode(rootNodeName);
    }

    @JsonIgnore
    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    public void forEachNode(Consumer<DagNode> action) {
        this.nodes.values().forEach(action);
    }

    public int size() {
        return nodes.size();
    }

    public DagNode getNode(String name) {
        return nodes.get(name);
    }

    public DagNode requireNode(String name) {
        DagNode node = getNode(name);
        if (node == null)
            throw new NopException(ERR_GRAPH_UNKNOWN_NODE)
                    .param(ARG_NAME, name);
        return node;
    }

    public void analyze() {
        new DagAnalyzer(this).analyze();
    }

    public DagNode addNextNodes(String nodeName, Collection<String> next) {
        checkAllowChange();
        DagNode node = nodes.computeIfAbsent(nodeName, DagNode::new);
        if (next != null) {
            for (String name : next) {
                nodes.computeIfAbsent(name, DagNode::new);
            }
            node.addNextNodes(next);
        }
        return node;
    }

    public DagNode addNextNode(String nodeName, String next) {
        checkAllowChange();
        DagNode node = nodes.computeIfAbsent(nodeName, DagNode::new);
        nodes.computeIfAbsent(next, DagNode::new);
        node.addNextNode(next);
        return node;
    }

    public DagNode addNode(String nodeName) {
        checkAllowChange();
        return nodes.computeIfAbsent(nodeName, DagNode::new);
    }

    public Set<String> getDescendantNodeNames(String nodeName) {
        Set<String> set = new LinkedHashSet<>();
        GraphBreadthFirstIterator.reachable(set, name -> getNode(name).getNextNodeNames(), nodeName);
        set.remove(nodeName);
        return set;
    }

    public Set<String> getAncestorNodeNames(String nodeName) {
        Set<String> set = new LinkedHashSet<>();
        GraphBreadthFirstIterator.reachable(set, name -> getNode(name).getPrevNodeNames(), nodeName);
        set.remove(nodeName);
        return set;
    }

    public boolean hasDescendant(String source, String next) {
        DagNode node = requireNode(source);
        if (node.getNextNodeNames() == null)
            return false;

        if (node.getNextNodeNames().contains(next))
            return true;

        for (String name : node.getNextNodeNames()) {
            if (hasDescendant(name, next))
                return true;
        }
        return false;
    }

    public boolean hasAncestor(String source, String prev) {
        DagNode node = requireNode(source);
        if (node.getPrevNodeNames() == null)
            return false;

        if (node.getPrevNodeNames().contains(prev))
            return true;

        for (String name : node.getPrevNodeNames()) {
            if (hasAncestor(name, prev))
                return true;
        }
        return false;
    }

    public DagNode getPrevJoint(DagNode node) {
        if (node.getPrevNodeNames() == null || node.getPrevNodeNames().size() > 1)
            return null;

        String prevName = CollectionHelper.first(node.getPrevNodeNames());
        DagNode prev = getNode(prevName);
        if (prev.getNextNodeNames().size() == 1) {
            DagNode joint = getPrevJoint(prev);
            if (joint == null)
                joint = prev;
            return joint;
        }
        return prev;
    }

    public String getRootNodeName() {
        return rootNodeName;
    }

    public void setRootNodeName(String rootNodeName) {
        checkAllowChange();
        this.rootNodeName = rootNodeName;
    }

    public Map<String, DagNode> getNodes() {
        return nodes;
    }

    public void setNodes(Map<String, DagNode> nodes) {
        checkAllowChange();
        this.nodes = nodes;
    }
}
