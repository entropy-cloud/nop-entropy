/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.json.xml;

import io.nop.api.core.util.SourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将json对象的属性拆解开，便于生成XNode节点
 */
public class NodeData {
    JsonXNodeType nodeType;
    SourceLocation location;

    String tagName;

    Map<String, Object> attrs;

    Map<String, Object> childrenMap;

    Object content;

    List<Object> children;

    public void addChild(Object child) {
        if (children == null) {
            children = new ArrayList<>();
        }
        children.add(child);
    }

    public void addNamedChild(String name, Object child) {
        if (childrenMap == null)
            childrenMap = new LinkedHashMap<>();
        childrenMap.put(name, child);
    }

    public void addAttr(String name, Object value) {
        if (attrs == null)
            attrs = new LinkedHashMap<>();
        attrs.put(name, value);
    }

    public SourceLocation getLocation() {
        return location;
    }

    public void setLocation(SourceLocation location) {
        this.location = location;
    }

    public boolean hasChild() {
        if (children != null && !children.isEmpty())
            return true;
        if (childrenMap != null && !childrenMap.isEmpty())
            return true;
        return false;
    }

    public JsonXNodeType getNodeType() {
        return nodeType;
    }

    public void setNodeType(JsonXNodeType nodeType) {
        this.nodeType = nodeType;
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    public Map<String, Object> getAttrs() {
        return attrs;
    }

    public void setAttrs(Map<String, Object> attrs) {
        this.attrs = attrs;
    }

    public Map<String, Object> getChildrenMap() {
        return childrenMap;
    }

    public void setChildrenMap(Map<String, Object> childrenMap) {
        this.childrenMap = childrenMap;
    }

    public Object getContent() {
        return content;
    }

    public void setContent(Object content) {
        this.content = content;
    }

    public List<Object> getChildren() {
        return children;
    }

    public void setChildren(List<Object> children) {
        this.children = children;
    }
}