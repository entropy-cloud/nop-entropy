/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xt.rules;

import io.nop.core.lang.xml.IXSelector;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.xt.IXTransformRule;
import io.nop.xlang.xt.IXTransformContext;

import java.util.List;

public class EachRule extends AbstractSelectorRule {
    private final IXTransformRule bodyRule;

    public EachRule(IXSelector<XNode> xpath, IXTransformRule bodyRule) {
        super(xpath, false);
        this.bodyRule = bodyRule;
    }

    @Override
    public void apply(XNode parent, XNode node, IXTransformContext context) {
        List<XNode> nodes = selectNodes(node, context);
        for (XNode selected : nodes) {
            IXTransformContext childContext = context.childContext(selected);
            if (bodyRule != null) {
                bodyRule.apply(parent, selected, childContext);
            }
        }
    }

    public IXTransformRule getBodyRule() {
        return bodyRule;
    }
}
