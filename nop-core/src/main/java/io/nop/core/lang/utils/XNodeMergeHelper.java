/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.utils;

import io.nop.core.CoreConstants;
import io.nop.core.lang.xml.XNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class XNodeMergeHelper {
    /**
     * 判断节点a是否包含节点b的所有属性
     */
    public static boolean containsAllAttrs(XNode a, XNode b) {
        if (a.getAttrCount() < b.getAttrCount())
            return false;

        for (String name : b.getAttrNames()) {
            Object bValue = b.getAttr(name);
            Object aValue = a.getAttr(name);
            if (!Objects.equals(aValue, bValue))
                return false;
        }
        return true;
    }

    /**
     * 将节点b的内容合并到节点a中。如果已经存在，则忽略，否则插入节点和属性。 合并过程会尽量寻找a和b的重合部分，减少新建的内容。
     */
    public static XNode mergeIfAbsent(XNode a, XNode b) {
        b.forEachAttr((name, vl) -> {
            if (!a.hasAttr(name) && !name.equals(CoreConstants.ATTR_XML_MULTIPLE)) {
                a.setAttr(name, vl);
            }
        });

        if (!b.hasChild()) {
            if (!a.hasContent()) {
                a.content(b.content());
            }
        } else if (!a.hasBody()) {
            a.appendChildren(b.cloneChildren());
        } else if (!a.hasContent()) {
            // 合并子节点
            List<XNode> aChildren = new ArrayList<>(a.getChildren());
            for (XNode bChild : b.getChildren()) {
                int[] match = findBestMatch(aChildren, bChild);
                if (match[0] >= 0) {
                    XNode aChild = aChildren.remove(match[0]);
                    mergeIfAbsent(aChild, bChild);
                } else {
                    a.appendChild(bChild.cloneInstance());
                }
            }
        }
        return a;
    }

    public static void normalizeMerged(XNode node) {
        node.forEachNode(child -> child.removeAttr(CoreConstants.ATTR_XML_MULTIPLE));
    }

    static int[] findBestMatch(List<XNode> children, XNode node) {
        int matchCount = 0;
        int index = -1;

        boolean multiple = node.attrBoolean(CoreConstants.ATTR_XML_MULTIPLE);

        for (int i = 0, n = children.size(); i < n; i++) {
            XNode child = children.get(i);
            if (!child.getTagName().equals(node.getTagName()))
                continue;

            if (!multiple)
                return new int[]{i, 1};

            int m = calcMatchCount(child, node);
            if (m > matchCount) {
                matchCount = m;
                index = i;
            }
        }
        return new int[]{index, matchCount};
    }

    /**
     * 计算两个节点重叠部分的分值。如果标签名不同，则认为不重叠
     */
    static int calcMatchCount(XNode a, XNode b) {
        if (!a.getTagName().equals(b.getTagName())) {
            return 0;
        }

        int ret = 0;

        for (String name : b.getAttrNames()) {
            Object aValue = a.getAttr(name);
            Object bValue = b.getAttr(name);
            if (Objects.equals(aValue, bValue)) {
                ret++;
            }
        }

        if (b.hasContent()) {
            if (b.contentText().equals(a.contentText())) {
                ret++;
            }
        } else if (a.hasChild()) {
            ret += calcChildMatchCount(a, b);
        }

        return ret;
    }

    static int calcChildMatchCount(XNode a, XNode b) {
        List<XNode> list = new ArrayList<>(a.getChildren());
        int ret = 0;
        for (XNode child : b.getChildren()) {
            int[] match = findBestMatch(list, child);
            if (match[0] >= 0) {
                list.remove(match[0]);
                ret += match[1];
            }
        }
        return ret;
    }
}