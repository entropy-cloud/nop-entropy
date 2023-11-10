package io.nop.core.model.graph.dag;

import io.nop.api.core.annotations.data.DataBean;

import java.util.LinkedHashSet;
import java.util.Set;

@DataBean
public class DagNode implements Comparable<DagNode> {
    private String name;

    /**
     * 如果是从根节点可达的节点，则它的nodeIndex会被设置为宽度遍历的序号
     */
    private int nodeIndex = -1;

    private boolean internal;

    private String controlNodeName;

    private Set<String> nextNodeNames;
    private Set<String> prevNodeNames;

    private Set<String> nextNormalNodeNames;

    private Set<String> prevNormalNodeNames;

    private boolean nextToEnd;

    private boolean eventuallyToEnd;

    private boolean nextToEmpty;

    private boolean eventuallyToEmpty;

    private boolean nextToAssigned;

    private boolean eventuallyToAssigned;

    public DagNode() {
    }

    public DagNode(String name) {
        setName(name);
    }

    @Override
    public int compareTo(DagNode o) {
        return Integer.compare(this.nodeIndex, o.nodeIndex);
    }

    public void addNextNodes(Set<String> next) {
        if (next == null)
            return;

        if (this.nextNodeNames == null)
            this.nextNodeNames = new LinkedHashSet<>();
        this.nextNodeNames.addAll(next);
    }

    public void addNextNode(String next) {
        if (next == null)
            return;
        if (this.nextNodeNames == null)
            this.nextNodeNames = new LinkedHashSet<>();
        this.nextNodeNames.add(next);
    }

    public void addPrevNodes(Set<String> prev) {
        if (prev == null)
            return;

        if (this.prevNodeNames == null)
            this.prevNodeNames = new LinkedHashSet<>();
        this.prevNodeNames.addAll(prev);
    }

    public void addPrevNode(String prev) {
        if (prev == null)
            return;

        if (this.prevNodeNames == null)
            this.prevNodeNames = new LinkedHashSet<>();
        this.prevNodeNames.add(prev);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getNodeIndex() {
        return nodeIndex;
    }

    public void setNodeIndex(int nodeIndex) {
        this.nodeIndex = nodeIndex;
    }

    public boolean isInternal() {
        return internal;
    }

    public void setInternal(boolean internal) {
        this.internal = internal;
    }

    public String getControlNodeName() {
        return controlNodeName;
    }

    public void setControlNodeName(String controlNodeName) {
        this.controlNodeName = controlNodeName;
    }

    public Set<String> getNextNodeNames() {
        return nextNodeNames;
    }

    public void setNextNodeNames(Set<String> nextNodeNames) {
        this.nextNodeNames = nextNodeNames;
    }

    public Set<String> getPrevNodeNames() {
        return prevNodeNames;
    }

    public void setPrevNodeNames(Set<String> prevNodeNames) {
        this.prevNodeNames = prevNodeNames;
    }

    public Set<String> getNextNormalNodeNames() {
        return nextNormalNodeNames;
    }

    public void setNextNormalNodeNames(Set<String> nextNormalNodeNames) {
        this.nextNormalNodeNames = nextNormalNodeNames;
    }

    public Set<String> getPrevNormalNodeNames() {
        return prevNormalNodeNames;
    }

    public void setPrevNormalNodeNames(Set<String> prevNormalNodeNames) {
        this.prevNormalNodeNames = prevNormalNodeNames;
    }

    public boolean isNextToEnd() {
        return nextToEnd;
    }

    public void setNextToEnd(boolean nextToEnd) {
        this.nextToEnd = nextToEnd;
    }

    public boolean isEventuallyToEnd() {
        return eventuallyToEnd;
    }

    public void setEventuallyToEnd(boolean eventuallyToEnd) {
        this.eventuallyToEnd = eventuallyToEnd;
    }

    public boolean isNextToEmpty() {
        return nextToEmpty;
    }

    public void setNextToEmpty(boolean nextToEmpty) {
        this.nextToEmpty = nextToEmpty;
    }

    public boolean isEventuallyToEmpty() {
        return eventuallyToEmpty;
    }

    public void setEventuallyToEmpty(boolean eventuallyToEmpty) {
        this.eventuallyToEmpty = eventuallyToEmpty;
    }

    public boolean isNextToAssigned() {
        return nextToAssigned;
    }

    public void setNextToAssigned(boolean nextToAssigned) {
        this.nextToAssigned = nextToAssigned;
    }

    public boolean isEventuallyToAssigned() {
        return eventuallyToAssigned;
    }

    public void setEventuallyToAssigned(boolean eventuallyToAssigned) {
        this.eventuallyToAssigned = eventuallyToAssigned;
    }
}
