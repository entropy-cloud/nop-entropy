/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.crud;

import io.nop.core.context.IEvalContext;
import io.nop.core.lang.xml.IXNodeGenerator;
import io.nop.core.lang.xml.XNode;

public class BizFilterNodeGenerator implements IXNodeGenerator {
    private final XNode node;

    public BizFilterNodeGenerator(XNode node) {
        this.node = node;
    }

    @Override
    public XNode generateNode(IEvalContext context) {
        return node.cloneInstance();
    }
}
