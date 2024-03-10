/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.delta;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.Pair;
import io.nop.core.lang.json.delta.DeltaMergeHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.xdef.IXDefAttribute;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdsl.XDslKeys;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.xlang.XLangErrors.ARG_ATTR_NAME;
import static io.nop.xlang.XLangErrors.ARG_ATTR_VALUE;
import static io.nop.xlang.XLangErrors.ARG_NODE;
import static io.nop.xlang.XLangErrors.ARG_NODE_A;
import static io.nop.xlang.XLangErrors.ARG_NODE_B;
import static io.nop.xlang.XLangErrors.ARG_TAG_NAME;
import static io.nop.xlang.XLangErrors.ARG_XDEF_NODE;
import static io.nop.xlang.XLangErrors.ARG_XDEF_NODE_NAME;
import static io.nop.xlang.XLangErrors.ERR_XDSL_MULTIPLE_NODE_HAS_SAME_UNIQUE_ATTR_VALUE;
import static io.nop.xlang.XLangErrors.ERR_XDSL_MULTIPLE_NODE_WITH_SAME_TAG;
import static io.nop.xlang.XLangErrors.ERR_XDSL_NODE_UNIQUE_KEY_VALUE_NOT_ALLOW_EMPTY;
import static io.nop.xlang.XLangErrors.ERR_XDSL_UNDEFINED_CHILD_NODE;

/**
 * 分析子节点的结构，按照唯一键进行组织，便于后续直接获取。
 */
public class ChildNodeMap {
    static class NodeData {
        XNode node;
        int aIndex;
        int bIndex;

        IXDefNode defChild;
        // 相同tagName但是具有不同uniqueAttr的节点。如果uniques不为null，则node为null
        Map<String, NodeData> uniques;

        public NodeData(XNode node, int aIndex, int bIndex, IXDefNode defChild) {
            this.node = node;
            this.aIndex = aIndex;
            this.bIndex = bIndex;
            this.defChild = defChild;
        }

        public void addUnique(String attrName, NodeData data) {
            uniques = addByUniqueAttr(uniques, attrName, data);
        }

        public boolean isSame(NodeData data) {
            if (this.node == null) {
                if (data.node != null)
                    return false;

                return isSameMap(uniques, data.uniques);
            } else {
                return node.isXmlEquals(data.node);
            }
        }
    }

    // tagName唯一的节点
    Map<String, NodeData> byTags;

    // 根据keyAttr唯一的节点
    Map<String, NodeData> byKeys;

    public ChildNodeMap(XNode node, boolean bNode, IXDefNode defNode, XDslKeys keys) {
        build(node, bNode, defNode, keys);
    }

    // 按照唯一键对子节点进行拆分
    private void build(XNode node, boolean bNode, IXDefNode defNode, XDslKeys keys) {
        String defaultKeyAttr = node.attrText(keys.KEY_ATTR);
        for (int i = 0, n = node.getChildCount(); i < n; i++) {
            XNode child = node.child(i);
            String tagName = child.getTagName();

            IXDefNode defChild = defNode == null ? null : defNode.getChild(tagName);
            int aIndex, bIndex;
            if (bNode) {
                aIndex = -1;
                bIndex = i;
            } else {
                aIndex = i;
                bIndex = -1;
            }
            NodeData data = new NodeData(child, aIndex, bIndex, defChild);

            if (defChild != null) {
                String keyAttr = defNode.getXdefKeyAttr();
                // 如果允许重复
                if (defChild.isAllowMultiple()) {
                    if (keyAttr != null) {
                        // 如果具有keyAttr
                        IXDefAttribute defAttr = defChild.getAttribute(keyAttr);
                        if (defAttr != null) {
                            byKeys = addByUniqueAttr(byKeys, keyAttr, data);
                            continue;
                        }
                    }
                    // 如果没有keyAttr，但是具有uniqueAttr。则具有同样tagName的节点按照uniqueKey组织
                    String uniqueAttr = defChild.getXdefUniqueAttr();
                    if (uniqueAttr != null) {
                        byKeys = addByUniqueAttr(byKeys, uniqueAttr, data);
                    }
                } else {
                    // 如果不允许重复，且按照tagName组织
                    byTags = addByTag(byTags, data);
                }
            } else {
                // x:post-extends等名字空间
                if (tagName.startsWith(keys.X_NS_PREFIX)) {
                    byTags = addByTag(byTags, data);
                    continue;
                }
                // 如果def模型不允许未知节点
                if (defNode != null && !supportUnknownChild(defNode))
                    throw new NopException(ERR_XDSL_UNDEFINED_CHILD_NODE).param(ARG_TAG_NAME, child.getTagName())
                            .param(ARG_NODE, child).param(ARG_XDEF_NODE, defNode)
                            .param(ARG_XDEF_NODE_NAME, defNode.getTagName());

                // 如果没有def模型，则查找x:name/v:id/id/name等属性
                String name, key;
                if (StringHelper.isEmpty(defaultKeyAttr)) {
                    Pair<String, String> keyPair = DeltaMergeHelper.buildUniqueKey(child);
                    if (keyPair != null) {
                        name = keyPair.getFirst();
                        key = keyPair.getSecond();
                    } else {
                        name = null;
                        key = null;
                    }
                } else {
                    name = defaultKeyAttr;
                    key = child.attrText(name);
                }

                if (!StringHelper.isEmpty(key)) {
                    byKeys = addByUniqueAttr(byKeys, name, data);
                } else {
                    byTags = addByTag(byTags, data);
                }
            }
        }
    }

    boolean supportUnknownChild(IXDefNode defNode) {
        if (defNode.isAllowUnknownTag())
            return true;

        return defNode.getXdefValue() != null && defNode.isSupportExtends();
    }

    NodeData makeData(Map<String, NodeData> map, String key, IXDefNode defChild) {
        NodeData data = map.get(key);
        if (data == null) {
            data = new NodeData(null, -1, -1, defChild);
            map.put(key, data);
        }
        return data;
    }

    static Map<String, NodeData> addByUniqueAttr(Map<String, NodeData> map, String attrName, NodeData data) {
        if (map == null)
            map = new HashMap<>();
        String key = data.node.attrText(attrName);
        if (StringHelper.isEmpty(key))
            throw new NopException(ERR_XDSL_NODE_UNIQUE_KEY_VALUE_NOT_ALLOW_EMPTY).param(ARG_NODE, data.node)
                    .param(ARG_ATTR_NAME, attrName);

        data.node.uniqueAttr(attrName);
        NodeData old = map.put(key + '|' + attrName, data);
        if (old != null)
            throw new NopException(ERR_XDSL_MULTIPLE_NODE_HAS_SAME_UNIQUE_ATTR_VALUE).param(ARG_NODE_A, old.node)
                    .param(ARG_NODE_B, data.node).param(ARG_ATTR_NAME, attrName).param(ARG_ATTR_VALUE, key);
        return map;
    }

    static Map<String, NodeData> addByTag(Map<String, NodeData> map, NodeData data) {
        if (map == null)
            map = new HashMap<>();
        String key = data.node.getTagName();
        NodeData old = map.put(key, data);
        if (old != null)
            throw new NopException(ERR_XDSL_MULTIPLE_NODE_WITH_SAME_TAG).param(ARG_NODE_A, old.node)
                    .param(ARG_NODE_B, data.node).param(ARG_TAG_NAME, key);
        return map;
    }

    public static boolean isSameMap(Map<String, NodeData> mapA, Map<String, NodeData> mapB) {
        return CollectionHelper.isSameMap(mapA, mapB, (n1, n2) -> n1.isSame(n2));
    }

    public static boolean isSameList(List<NodeData> listA, List<NodeData> listB) {
        return CollectionHelper.isSameList(listA, listB, (n1, n2) -> n1.isSame(n2));
    }
}