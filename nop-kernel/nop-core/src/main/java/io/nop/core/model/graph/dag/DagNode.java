/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.graph.dag;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@DataBean
public class DagNode implements Comparable<DagNode> {
    private String name;

    /**
     * 如果是从根节点可达的节点，则它的nodeIndex会被设置为宽度遍历的序号，从0开始
     */
    private int nodeIndex = -1;

    /**
     * 从根节点向下的深度层次，从0开始。
     */
    private int depth = -1;

    private boolean internal;

    private String controlNodeName;

    private Set<String> nextNodeNames;
    private Set<String> prevNodeNames;

    private Set<String> nextNormalNodeNames;

    private Set<String> prevNormalNodeNames;

    public DagNode() {
    }

    public DagNode(String name) {
        setName(name);
    }

    @Override
    public int compareTo(DagNode o) {
        return Integer.compare(this.nodeIndex, o.nodeIndex);
    }

    public boolean hasPrevNode() {
        return prevNodeNames != null && !prevNodeNames.isEmpty();
    }

    public boolean hasNextNode() {
        return nextNodeNames != null && !nextNodeNames.isEmpty();
    }

    public void removeNextNode(String name) {
        if (this.nextNodeNames != null)
            this.nextNodeNames.remove(name);
    }

    public void addNextNodes(Collection<String> next) {
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

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
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

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Set<String> getNextNodeNames() {
        return nextNodeNames;
    }

    public void setNextNodeNames(Set<String> nextNodeNames) {
        this.nextNodeNames = nextNodeNames;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Set<String> getPrevNodeNames() {
        return prevNodeNames;
    }

    public void setPrevNodeNames(Set<String> prevNodeNames) {
        this.prevNodeNames = prevNodeNames;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Set<String> getNextNormalNodeNames() {
        return nextNormalNodeNames;
    }

    public void setNextNormalNodeNames(Set<String> nextNormalNodeNames) {
        this.nextNormalNodeNames = nextNormalNodeNames;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Set<String> getPrevNormalNodeNames() {
        return prevNormalNodeNames;
    }

    public void setPrevNormalNodeNames(Set<String> prevNormalNodeNames) {
        this.prevNormalNodeNames = prevNormalNodeNames;
    }
}
