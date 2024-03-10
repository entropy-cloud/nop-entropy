/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.utils;

import io.nop.commons.collections.SafeOrderedComparator;
import io.nop.commons.type.StdDataType;
import io.nop.core.lang.xml.XNode;

import java.util.Comparator;

public class XNodeAttrComparator implements Comparator<XNode> {
    private final String attrName;
    private final StdDataType dataType;

    public XNodeAttrComparator(String attrName, StdDataType dataType) {
        this.attrName = attrName;
        this.dataType = dataType;
    }

    public XNodeAttrComparator(String attrName) {
        this(attrName, null);
    }

    @Override
    public int compare(XNode o1, XNode o2) {
        Object v1 = o1.getAttr(attrName);
        Object v2 = o2.getAttr(attrName);
        if (dataType != null) {
            v1 = dataType.convert(v1);
            v2 = dataType.convert(v2);
        }
        return SafeOrderedComparator.DEFAULT.compare(v1, v2);
    }

}
