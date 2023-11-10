package io.nop.core.model.graph.dag;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.model.tree.impl.WidthFirstIterator;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
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

    public Dag() {
    }

    public Dag(String rootName) {
        setRootNodeName(rootName);
        nodes.put(rootName, new DagNode(rootName));
    }

    public Iterator<DagNode> widthFirstIterator() {
        return new WidthFirstIterator<>(this::getNextNodes, getRootNode(), true, null);
    }

    public List<DagNode> getNextNodes(DagNode node) {
        Set<String> nextNames = node.getNextNodeNames();
        if (nextNames == null || nextNames.isEmpty())
            return Collections.emptyList();

        return nextNames.stream().map(this::requireNode).collect(Collectors.toList());
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
