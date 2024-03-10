/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.common.impl;

import io.nop.core.context.IEvalContext;
import io.nop.core.lang.xml.XNode;
import io.nop.ooxml.common.IOfficePackagePart;

public class XmlOfficePackagePart implements IOfficePackagePart {
    private final String path;
    private final XNode node;

    public XmlOfficePackagePart(String path, XNode node) {
        this.path = path;
        this.node = node;
    }

    public XNode getNode() {
        return node;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public XNode loadXml() {
        return node;
    }

    @Override
    public XNode buildXml(IEvalContext context) {
        return node;
    }
}
