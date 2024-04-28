/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.delta;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.json.delta.DeltaMergeHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.XDefBodyType;
import io.nop.xlang.xdef.XDefConstants;
import io.nop.xlang.xdef.XDefOverride;
import io.nop.xlang.xdsl.XDslKeys;
import io.nop.xlang.xpl.XplConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static io.nop.xlang.XLangErrors.ARG_ATTR_NAME;
import static io.nop.xlang.XLangErrors.ARG_NODE;
import static io.nop.xlang.XLangErrors.ARG_NODE_A;
import static io.nop.xlang.XLangErrors.ARG_NODE_B;
import static io.nop.xlang.XLangErrors.ARG_VALUE;
import static io.nop.xlang.XLangErrors.ERR_XDSL_FINAL_NODE_NOT_ALLOW_OVERRIDE;
import static io.nop.xlang.XLangErrors.ERR_XDSL_NOT_ALLOW_MERGE_BETWEEN_NODE;
import static io.nop.xlang.XLangErrors.ERR_XDSL_NOT_FIND_PROTOTYPE_NODE;
import static io.nop.xlang.XLangErrors.ERR_XDSL_NOT_FIND_SUPER_NODE;
import static io.nop.xlang.XLangErrors.ERR_XDSL_SUPER_NODE_NOT_ALLOW_BODY;

public class DeltaMerger implements IDeltaMerger {
    private final XDslKeys keys;

    public DeltaMerger(XDslKeys keys) {
        this.keys = keys;
    }

    @Override
    public void merge(XNode xa, XNode xb, IXDefNode defNode, boolean forPrototype) {
        XDefOverride defaultOverride = XDefOverride.MERGE;
        if (defNode != null) {
            if (!defNode.hasChild()) {
                defaultOverride = XDefOverride.MERGE_REPLACE;
            }
        }
        XDefOverride overrideA = OverrideHelper.getOverride(xa, keys, forPrototype, defNode, defaultOverride);
        XDefOverride overrideB = OverrideHelper.getOverride(xb, keys, forPrototype, defNode, defaultOverride);
        XDefOverride mergedOverride = OverrideHelper.mergedOverride(overrideA, overrideB);
        if (mergedOverride == null)
            throw new NopException(ERR_XDSL_NOT_ALLOW_MERGE_BETWEEN_NODE).source(xb).param(ARG_NODE_A, xa)
                    .param(ARG_NODE_B, xb);

        // prototype合并时xa只是作为模板，因此不需要检查final属性
        if (!forPrototype) {
            boolean isFinal = xa.attrBoolean(keys.FINAL, false);
            if (isFinal)
                throw new NopException(ERR_XDSL_FINAL_NODE_NOT_ALLOW_OVERRIDE).param(ARG_NODE, xa).param(ARG_NODE_B,
                        xb);
        }

        // abstract节点被覆盖后不再是abstract的
        xa.removeAttr(keys.ABSTRACT);

        // 节点参与合并操作之后删除inherit检查标记
        xb.removeAttr(keys.INHERIT);
        xb.removeAttr(keys.VIRTUAL);

        switch (overrideB) {
            case REMOVE:
                overrideRemove(xa, xb);
                xa.setAttr(keys.ABSTRACT, true);
                break;
            case REPLACE:
                overrideReplace(xa, xb);
                break;
            case PREPEND:
                if (isXpl(defNode)) {
                    normalizeXpl(xa);
                    normalizeXpl(xb);
                }
                normalizeMergeSuper(xa, overrideA, forPrototype);
                overridePrepend(xa, xb);
                break;
            case APPEND:
                if (isXpl(defNode)) {
                    normalizeXpl(xa);
                    normalizeXpl(xb);
                }
                normalizeMergeSuper(xa, overrideA, forPrototype);
                overrideAppend(xa, xb);
                break;
            case MERGE:
                overrideMerge(xa, xb, defNode, forPrototype, false);
                break;
            case MERGE_SUPER:
                normalizeMergeSuper(xa, overrideA, forPrototype);
                overrideMergeSuper(xa, xb, forPrototype);
                break;
            case MERGE_REPLACE:
                overrideMergeReplace(xa, xb);
                break;
            case BOUNDED_MERGE:
                overrideMerge(xa, xb, defNode, forPrototype, true);
                break;
            default:
                throw new IllegalArgumentException("invalid override operator:" + overrideB);
        }
        xa.setAttr(forPrototype ? keys.PROTOTYPE_OVERRIDE : keys.OVERRIDE, mergedOverride);
        if (forPrototype && XDefOverride.REMOVE == mergedOverride) {
            xa.setAttr(keys.OVERRIDE, mergedOverride);
        }
    }

    private boolean isXpl(IXDefNode def) {
        if (def == null)
            return false;
        return def.getXdefValue() != null && XDefConstants.STD_DOMAIN_XPL.equals(def.getXdefValue().getStdDomain());
    }

    private void normalizeXpl(XNode node) {
        if (!node.hasChild()) {
            if (node.hasContent()) {
                XNode script = XNode.make(XplConstants.TAG_C_IMPORT);
                script.setLocation(node.getLocation());
                script.content(node.content());
                node.clearBody();
                node.appendChild(script);
            }
        }
    }

    private void overrideRemove(XNode xa, XNode xb) {
        // 保留唯一键。在merge函数中会设置XNode.uniqueAttr属性，参见ChildNodeMap
        String uniqueAttr = xa.uniqueAttr();
        if (uniqueAttr != null) {
            ValueWithLocation attr = xa.attrValueLoc(uniqueAttr);
            xa.clearAttrs();
            if (!attr.isNull()) {
                xa.attrValueLoc(uniqueAttr, attr);
            }
        } else {
            xa.clearAttrs();
        }
        xa.clearBody();
        xa.setLocation(xb.getLocation());
    }

    private void overrideReplace(XNode xa, XNode xb) {
        replaceAttrs(xa, xb);
        replaceBody(xa, xb);
    }

    private void overrideAppend(XNode xa, XNode xb) {
        mergeAttrs(xa, xb);
        xa.normalizeContent();
        xb.normalizeContent();
        xa.appendChildren(xb.detachChildren());
    }

    private void overridePrepend(XNode xa, XNode xb) {
        mergeAttrs(xa, xb);
        xa.normalizeContent();
        xb.normalizeContent();
        xa.prependChildren(xb.detachChildren());
    }

    private void overrideMergeReplace(XNode xa, XNode xb) {
        mergeAttrs(xa, xb);
        replaceBody(xa, xb);
    }

    private void overrideMergeSuper(XNode xa, XNode xb, boolean forPrototype) {
        mergeAttrs(xa, xb);
        replaceSuper(xa, xb, forPrototype);
    }

    private void overrideMerge(XNode xa, XNode xb, IXDefNode defNode, boolean forPrototype, boolean boundedMerge) {
        mergeAttrs(xa, xb);
        if (!xb.hasChild()) {
            xa.clearBody();
            // 如果xb有内容，则直接覆盖xa的内容。
            if (xb.hasContent()) {
                xa.content(xb.content());
            }
        } else {
            if (!xa.hasChild()) {
                xa.clearBody();
                xa.appendChildren(xb.detachChildren());
            } else {
                mergeChildren(xa, xb, defNode, forPrototype, boundedMerge);
            }
        }
    }

    private void normalizeMergeSuper(XNode node, XDefOverride override, boolean forPrototype) {
        if (override == XDefOverride.PREPEND) {
            node.appendChild(XNode.make(keys.getSuper(forPrototype)));
        } else if (override == XDefOverride.APPEND) {
            node.prependChild(XNode.make(keys.getSuper(forPrototype)));
        }
    }

    private void replaceAttrs(XNode xa, XNode xb) {
        if (xb.getComment() != null)
            xa.setComment(xb.getComment());
        xa.clearAttrs();
        mergeAttrs(xa, xb);
    }

    private void mergeAttrs(XNode xa, XNode xb) {
        if (xb.getComment() != null)
            xa.setComment(xb.getComment());

        if (!xb.isDummyNode() && !xa.getTagName().equals(xb.getTagName())) {
            xa.setTagName(xb.getTagName());
            xa.setLocation(xb.getLocation());
        }
        xa.mergeAttrs(xb);
    }

    private void replaceBody(XNode xa, XNode xb) {
        xa.clearBody();
        xa.content(xb.content());
        xa.appendChildren(xb.detachChildren());
    }

    private XNode getSuperNode(XNode node, boolean forPrototype) {
        String superTag = forPrototype ? keys.PROTOTYPE_SUPER : keys.SUPER;

        XNode superNode = node.find(child -> child.getTagName().equals(superTag));

        if (superNode == null)
            throw new NopException(ERR_XDSL_NOT_FIND_SUPER_NODE).param(ARG_NODE, node);

        if (superNode.hasBody()) {
            throw new NopException(ERR_XDSL_SUPER_NODE_NOT_ALLOW_BODY).param(ARG_NODE, superNode);
        }

        return superNode;
    }

    private void replaceSuper(XNode xa, XNode xb, boolean forPrototype) {
        XNode superNode = getSuperNode(xb, forPrototype);
        superNode.replaceBy(xa.cloneInstance());
    }

    private void mergeChildren(XNode xa, XNode xb, IXDefNode defNode, boolean forPrototype, boolean boundedMerge) {
        ChildNodeMap mapA = new ChildNodeMap(xa, false, defNode, keys);

        if (CollectionHelper.isEmptyMap(mapA.byTags) && CollectionHelper.isEmptyMap(mapA.byKeys)) {
            if (boundedMerge) {
                replaceBody(xa, xb);
                return;
            } else {
                xa.normalizeContent();
                xb.normalizeContent();
                xa.appendChildren(xb.detachChildren());
                return;
            }
        }

        ChildNodeMap mapB = new ChildNodeMap(xb, true, defNode, keys);
        Map<String, ChildNodeMap.NodeData> byKeys = mergeMap(mapA.byKeys, mapB.byKeys, forPrototype);
        Map<String, ChildNodeMap.NodeData> byTags = mergeMap(mapA.byTags, mapB.byTags, forPrototype);

        int[] bIndexes = boundedMerge ? null : new int[xb.getChildCount()];
        if (bIndexes != null)
            Arrays.fill(bIndexes, -1);

        int aCount = xa.getChildCount();
        List<XNode> aChildren = xa.detachChildren();
        if (byKeys != null) {
            for (ChildNodeMap.NodeData data : byKeys.values()) {
                if (data.bIndex >= 0) {
                    // 如果是合并节点
                    if (data.aIndex >= 0)
                        xb.replaceChild(data.bIndex, data.node);
                    if (bIndexes != null)
                        bIndexes[data.bIndex] = data.aIndex;
                }
            }
        }

        if (byTags != null) {
            for (ChildNodeMap.NodeData data : byTags.values()) {
                if (data.bIndex >= 0) {
                    // 如果是合并节点
                    if (data.aIndex >= 0)
                        xb.replaceChild(data.bIndex, data.node);
                    if (bIndexes != null) {
                        bIndexes[data.bIndex] = data.aIndex;
                    }
                } else if (data.uniques != null) {
                    for (ChildNodeMap.NodeData unique : data.uniques.values()) {
                        if (unique.bIndex >= 0) {
                            xb.replaceChild(data.bIndex, data.node);
                            if (bIndexes != null) {
                                bIndexes[data.bIndex] = data.aIndex;
                            }
                        }
                    }
                }
            }
        }

        if (boundedMerge) {
            xa.appendChildren(xb.detachChildren());
            return;
        }

        List<DeltaMergeHelper.MatchData> matchList = DeltaMergeHelper.mergeList(aCount, bIndexes);
        List<XNode> merged = new ArrayList<>();
        for (DeltaMergeHelper.MatchData matchData : matchList) {
            if (matchData.aIndex >= 0) {
                merged.add(aChildren.get(matchData.aIndex));
            } else {
                merged.add(xb.child(matchData.bIndex));
            }
        }
        xb.detachChildren();
        xa.appendChildren(merged);
    }

    private Map<String, ChildNodeMap.NodeData> mergeMap(Map<String, ChildNodeMap.NodeData> mapA,
                                                        Map<String, ChildNodeMap.NodeData> mapB, boolean forPrototype) {
        if (mapA == null || mapA.isEmpty())
            return mapB;

        if (mapB == null) {
            return null;
        }

        for (Map.Entry<String, ChildNodeMap.NodeData> entry : mapB.entrySet()) {
            String key = entry.getKey();
            ChildNodeMap.NodeData data = entry.getValue();
            ChildNodeMap.NodeData prevData = mapA.get(key);

            if (prevData == null) {
                continue;
            }
            data.aIndex = prevData.aIndex;

            if (data.node == null) {
                data.uniques = mergeMap(prevData.uniques, data.uniques, forPrototype);
            } else {
                merge(prevData.node, data.node, data.defChild, forPrototype);
                data.node = prevData.node;
            }
        }
        return mapB;
    }

    @Override
    public void processPrototype(XNode node, IXDefNode defNode) {
        if (defNode == null)
            return;

        for (int i = 0, n = node.getChildCount(); i < n; i++) {
            XNode child = node.child(i);
            IXDefNode childDef = defNode.getChild(child.getTagName());
            if (childDef != null) {
                processPrototype(child, childDef);
            }
        }

        for (int i = 0, n = node.getChildCount(); i < n; i++) {
            XNode child = node.child(i);

            IXDefNode childDef = defNode.getChild(child.getTagName());
            if (childDef == null)
                continue;

            String keyAttr = defNode.getXdefKeyAttr();
            if (keyAttr == null)
                keyAttr = childDef.getXdefUniqueAttr();

            if (keyAttr == null) {
                if (defNode.getXdefBodyType() == XDefBodyType.map) {
                    keyAttr = ApiConstants.TREE_BEAN_PROP_TYPE;
                }
            }

            if (keyAttr != null) {
                String prototype = child.removeAttr(keys.PROTOTYPE).asString();
                if (!StringHelper.isEmpty(prototype)) {
                    mergePrototype(node, child, childDef, keyAttr, prototype);
                }
            }
        }
    }

    XNode mergePrototype(XNode node, XNode child, IXDefNode childDef, String keyAttr, String prototype) {
        XNode prototypeNode = node.childByAttr(keyAttr, prototype);
        if (prototypeNode == null)
            throw new NopException(ERR_XDSL_NOT_FIND_PROTOTYPE_NODE).param(ARG_NODE, child)
                    .param(ARG_ATTR_NAME, keyAttr).param(ARG_VALUE, prototype);

        String basePrototype = prototypeNode.removeAttr(keys.PROTOTYPE).asString();
        if (!StringHelper.isEmpty(basePrototype)) {
            prototypeNode = mergePrototype(node, prototypeNode, childDef, keyAttr, basePrototype);
        }

        prototypeNode = prototypeNode.cloneInstance();
        merge(prototypeNode, child, childDef, true);
        child.replaceBy(prototypeNode);
        return prototypeNode;
    }
}