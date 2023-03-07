/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.delta;

import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.XDefOverride;
import io.nop.xlang.xdsl.XDslKeys;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static io.nop.commons.functional.IEqualsChecker.STRING_EQUALS;
import static io.nop.commons.util.CollectionHelper.removeFrom;

@SuppressWarnings("PMD.UnusedFormalParameter")
public class DeltaDiffer implements IDeltaDiffer {
    private final XDslKeys keys;
    private final XDefOverride defOverride;

    public DeltaDiffer(XDslKeys keys, XDefOverride defOverride) {
        this.keys = keys;
        this.defOverride = defOverride;
    }

    public DeltaDiffer(XDslKeys keys) {
        this(keys, XDefOverride.MERGE);
    }

    @Override
    public void diff(XNode xa, XNode xb, IXDefNode defNode, boolean forPrototype) {
        XDefOverride overrideA = OverrideHelper.getOverride(xa, keys, forPrototype, defNode, defOverride);

        switch (overrideA) {
            case REMOVE:
                diffRemove(xa, xb, defNode, forPrototype);
                break;
            case REPLACE:
                diffReplace(xa, xb, defNode, forPrototype);
                break;
            case PREPEND:
                diffPrepend(xa, xb, defNode, forPrototype);
                break;
            case APPEND:
                diffAppend(xa, xb, defNode, forPrototype);
                break;
            case MERGE:
                diffMerge(xa, xb, defNode, forPrototype, false);
                break;
            case MERGE_REPLACE:
                diffMergeReplace(xa, xb, defNode, forPrototype);
                break;
            case BOUNDED_MERGE:
                diffMerge(xa, xb, defNode, forPrototype, true);
                break;
            case MERGE_SUPER:
                diffMergeSuper(xa, xb, defNode, forPrototype);
                break;
            default:
        }
    }

    private void diffRemove(XNode xa, XNode xb, IXDefNode defNode, boolean forPrototype) {
        markRemoved(xa, forPrototype);
    }

    private void diffReplace(XNode xa, XNode xb, IXDefNode defNode, boolean forPrototype) {
        xa.setAttr(keys.getOverride(forPrototype), XDefOverride.REPLACE.toString());
    }

    private void diffPrepend(XNode xa, XNode xb, IXDefNode defNode, boolean forPrototype) {
        XDefOverride overrideB = OverrideHelper.getOverride(xb, keys, forPrototype, defNode, defOverride);
        if (overrideB == XDefOverride.PREPEND) {
            if (endsWith(xa.getChildren(), xb.getChildren())) {
                removeDuplicateAttr(xa, xb);
                removeFrom(xa.getChildren(), xa.getChildCount() - xb.getChildCount(), xb.getChildCount());
                return;
            }
        }
        diffReplace(xa, xb, defNode, forPrototype);
    }

    private void diffAppend(XNode xa, XNode xb, IXDefNode defNode, boolean forPrototype) {
        XDefOverride overrideB = OverrideHelper.getOverride(xb, keys, forPrototype, defNode, defOverride);
        if (overrideB == XDefOverride.APPEND) {
            if (startsWith(xa.getChildren(), xb.getChildren())) {
                removeDuplicateAttr(xa, xb);
                removeFrom(xa.getChildren(), 0, xb.getChildCount());
                return;
            }
        }
        diffReplace(xa, xb, defNode, forPrototype);
    }

    private void removeDuplicateAttr(XNode xa, XNode xb) {
        String uniqueAttr = xa.uniqueAttr();
        xa.removeAttrsIf((name, vl) -> {
            if (uniqueAttr != null && uniqueAttr.equals(name))
                return false;

            Object vb = xb.getAttr(name);
            return STRING_EQUALS.isEquals(vl.getValue(), vb);
        });
    }

    private boolean startsWith(List<XNode> listA, List<XNode> listB) {
        if (listA.size() < listB.size()) {
            return false;
        }
        return isSame(listA, 0, listB);
    }

    private boolean endsWith(List<XNode> listA, List<XNode> listB) {
        if (listA.size() < listB.size()) {
            return false;
        }
        return isSame(listA, listA.size() - listB.size(), listB);
    }

    private boolean isSame(List<XNode> listA, int fromA, List<XNode> listB) {
        if (fromA < 0)
            return false;

        if (fromA + listB.size() > listA.size())
            return false;

        for (int i = 0; i < listB.size(); i++) {
            if (!listA.get(i + fromA).isXmlEquals(listB.get(i)))
                return false;
        }
        return true;
    }

    private void diffMergeReplace(XNode xa, XNode xb, IXDefNode defNode, boolean forPrototype) {
        removeDuplicateAttr(xa, xb);
    }

    private void diffMergeSuper(XNode xa, XNode xb, IXDefNode defNode, boolean forPrototype) {

        String superName = keys.getSuper(forPrototype);
        XNode superA = xa.find(node -> node.getTagName().equals(superName));
        if (superA == null) {
            diffReplace(xa, xb, defNode, forPrototype);
            return;
        }

        // 暂时不处理其他情况

        /*
         * XDefOverride overrideB = OverrideHelper.getOverride(xb, keys, forPrototype, defNode);
         *
         * if (overrideB == XDefOverride.APPEND) { int superIndex = superA.childIndex(); List<XNode> children =
         * superA.getParent().getChildren(); if (isSame(children, superIndex + 1, xb.getChildren())) {
         * removeFrom(children, superIndex + 1, xb.getChildCount()); return; } } else if (overrideB ==
         * XDefOverride.PREPEND) { int superIndex = superA.childIndex(); List<XNode> children =
         * superA.getParent().getChildren(); if (isSame(children, superIndex - xb.getChildCount(), xb.getChildren())) {
         * removeFrom(children, superIndex - xb.getChildCount(), xb.getChildCount()); return; } } else if (overrideB ==
         * XDefOverride.MERGE_SUPER) { XNode superB = xb.find(node -> node.getTagName().equals(keys.SUPER)); if (superB
         * != null) { int superBLevel = superB.getTreeLevel(); int bLevel = xb.getTreeLevel(); int superALevel =
         * superA.getTreeLevel(); int aLevel = xa.getTreeLevel(); if (bLevel - superBLevel >= aLevel - superALevel) {
         * XNode child = xb.getParent(bLevel - superBLevel); if (child.isXmlEquals(xa)) {
         * child.replaceBy(XNode.make(superName)); return; } } } }
         *
         * diffReplace(xa, xb, defNode, forPrototype);
         */
    }

    private void diffMerge(XNode xa, XNode xb, IXDefNode defNode, boolean forPrototype, boolean bounded) {
        removeDuplicateAttr(xa, xb);
        if (xa.hasContent()) {
            if (STRING_EQUALS.isEquals(xa.getContentValue(), xb.getContentValue())) {
                xa.content(ValueWithLocation.NULL_VALUE);
            }
            return;
        } else if (xb.hasContent()) {
            // xb具有内容，但是合并后的节点xa没有内容
            markMergeReplace(xa, forPrototype);
            return;
        }

        if (!xb.hasChild()) {
            return;
        }

        ChildNodeMap mapB = new ChildNodeMap(xb, true, defNode, keys);
        if (!xa.hasChild()) {
            // xb具有子节点，但是合并后的节点xa没有子节点
            markMergeReplace(xa, forPrototype);
            return;
        }

        ChildNodeMap mapA = new ChildNodeMap(xa, false, defNode, keys);
        xb.detachChildren();

        List<ChildNodeMap.NodeData> removed = new ArrayList<>();
        // 按照key进行diff
        diffMergeMap(xa, mapA.byTags, mapB.byTags, forPrototype, bounded, removed);
        diffMergeMap(xa, mapA.byKeys, mapB.byKeys, forPrototype, bounded, removed);
    }

    private void diffMergeMap(XNode xa, Map<String, ChildNodeMap.NodeData> mapA,
                              Map<String, ChildNodeMap.NodeData> mapB, boolean forPrototype, boolean bounded,
                              List<ChildNodeMap.NodeData> removed) {
        if (mapB == null || mapB.isEmpty())
            return;

        if (mapA == null || mapA.isEmpty()) {
            // bounded-merge合并模式下不存在就表示删除
            if (!bounded) {
                markAllRemoved(mapB.values(), forPrototype, removed);
            }
            return;
        }

        for (Map.Entry<String, ChildNodeMap.NodeData> entry : mapA.entrySet()) {
            String key = entry.getKey();
            ChildNodeMap.NodeData dataB = mapB.get(key);

            // 完全新增的节点不需要处理
            if (dataB == null)
                continue;

            ChildNodeMap.NodeData dataA = entry.getValue();
            if (dataA.node != null) {
                dataB.aIndex = dataA.aIndex;
                diff(dataA.node, dataB.node, dataA.defChild, forPrototype);
            } else {
                diffMergeMap(xa, dataA.uniques, dataB.uniques, forPrototype, bounded, removed);
            }
        }

        if (!bounded) {
            // 考虑被删除的节点
            for (Map.Entry<String, ChildNodeMap.NodeData> entry : mapA.entrySet()) {
                String key = entry.getKey();
                ChildNodeMap.NodeData dataA = mapB.get(key);
                if (dataA != null)
                    continue;

                markRemoved(entry.getValue(), forPrototype, removed);
            }
        }
    }

    private void markMergeReplace(XNode xa, boolean forPrototype) {
        xa.setAttr(keys.getOverride(forPrototype), XDefOverride.MERGE_REPLACE.toString());
    }

    void markAllRemoved(Collection<ChildNodeMap.NodeData> list, boolean forPrototype,
                        List<ChildNodeMap.NodeData> removed) {
        if (list != null) {
            for (ChildNodeMap.NodeData data : list) {
                markRemoved(data, forPrototype, removed);
            }
        }
    }

    void markRemoved(ChildNodeMap.NodeData data, boolean forPrototype, List<ChildNodeMap.NodeData> removed) {
        if (data.node != null) {
            markRemoved(data.node, forPrototype);
            removed.add(data);
        } else {
            for (ChildNodeMap.NodeData uniqueData : data.uniques.values()) {
                markRemoved(uniqueData, forPrototype, removed);
            }
        }
    }

    void markRemoved(XNode node, boolean forPrototype) {
        // 保留唯一标识属性
        String uniqueAttr = node.uniqueAttr();
        if (uniqueAttr != null) {
            ValueWithLocation vl = node.attrValueLoc(uniqueAttr);
            node.clearAttrs();
            node.attrValueLoc(uniqueAttr, vl);
        }
        node.setAttr(keys.getOverride(forPrototype), XDefOverride.REMOVE.toString());
        node.clearBody();
    }
}