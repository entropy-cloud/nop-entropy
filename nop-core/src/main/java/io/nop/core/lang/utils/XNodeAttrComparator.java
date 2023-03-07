/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.utils;

import io.nop.commons.collections.SafeOrderedComparator;
import io.nop.core.lang.xml.XNode;

import java.util.Comparator;

public class XNodeAttrComparator implements Comparator<XNode> {
    private final String attrName;

    public XNodeAttrComparator(String attrName) {
        this.attrName = attrName;
    }

    @Override
    public int compare(XNode o1, XNode o2) {
        Object v1 = o1.getAttr(attrName);
        Object v2 = o2.getAttr(attrName);
        return SafeOrderedComparator.DEFAULT.compare(v1, v2);
    }

}
