package io.nop.core.model.graph.dag;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.objects.Pair;
import io.nop.core.model.graph.GraphBreadthFirstIterator;
import io.nop.core.model.graph.GraphDepthFirstIterator;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static io.nop.core.CoreErrors.ARG_NAME;
import static io.nop.core.CoreErrors.ERR_GRAPH_UNKNOWN_NODE;

@DataBean
public class Dag {
    private String rootNodeName;
    private Map<String, DagNode> nodes = new HashMap<>();

    /**
     * 为了从一般性的图结构抽取得到DAG，需要删除哪些链接
     */
    private List<Pair<String, String>> removedEdges;

    public Dag() {
    }

    public Dag(String rootName) {
        setRootNodeName(rootName);
        nodes.put(rootName, new DagNode(rootName));
    }

    public Iterator<DagNode> breathFirstIterator(DagNode node) {
        return new GraphBreadthFirstIterator<>(this::getNextNodes, node);
    }

    public Iterator<DagNode> depthFirstIterator(DagNode node) {
        return new GraphDepthFirstIterator<>(this::getNextNodes, node);
    }

    public List<Pair<String, String>> getRemovedEdges() {
        return removedEdges;
    }

    public void setRemovedEdges(List<Pair<String, String>> removedEdges) {
        this.removedEdges = removedEdges;
    }

    public List<DagNode> getNextNodes(DagNode node) {
        Set<String> nextNames = node.getNextNodeNames();
        if (nextNames == null || nextNames.isEmpty())
            return Collections.emptyList();

        return nextNames.stream().map(this::requireNode).collect(Collectors.toList());
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

    public DagNode getRootNode() {
        return requireNode(rootNodeName);
    }

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

    public DagNode addNextNodes(String nodeName, Set<String> next) {
        DagNode node = nodes.computeIfAbsent(nodeName, DagNode::new);
        if (next != null) {
            node.addNextNodes(next);
        }
        return node;
    }

    public DagNode addNextNode(String nodeName, String next) {
        DagNode node = nodes.computeIfAbsent(nodeName, DagNode::new);
        node.addNextNode(next);
        return node;
    }

    public Set<String> getDescendantNodeNames(String nodeName) {
        Set<String> set = new LinkedHashSet<>();
        GraphBreadthFirstIterator.reachable(set, name -> getNode(name).getNextNodeNames(), nodeName);
        return set;
    }

    public Set<String> getAncestorNodeNames(String nodeName) {
        Set<String> set = new LinkedHashSet<>();
        GraphBreadthFirstIterator.reachable(set, name -> getNode(name).getPrevNodeNames(), nodeName);
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
        this.rootNodeName = rootNodeName;
    }

    public Map<String, DagNode> getNodes() {
        return nodes;
    }

    public void setNodes(Map<String, DagNode> nodes) {
        this.nodes = nodes;
    }
}
