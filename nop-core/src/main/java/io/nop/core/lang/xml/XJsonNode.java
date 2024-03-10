/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.xml;

import io.nop.api.core.json.IJsonString;
import io.nop.api.core.util.Guard;

public class XJsonNode implements IJsonString {
    private final XNode node;

    public XJsonNode(XNode node) {
        this.node = Guard.notNull(node, "node");
    }

    public XNode getNode() {
        return node;
    }

    @Override
    public String toString() {
        return node.xml();
    }
}