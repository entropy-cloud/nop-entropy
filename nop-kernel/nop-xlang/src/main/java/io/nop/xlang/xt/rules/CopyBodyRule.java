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
import io.nop.xlang.xt.IXTransformContext;

public class CopyBodyRule extends AbstractSelectorRule {
    public CopyBodyRule(IXSelector<XNode> xpath, boolean mandatory) {
        super(xpath, mandatory);
    }

    @Override
    public void apply(XNode parent, XNode node, IXTransformContext context) {
        XNode selected = selectNode(node, context);
        if (selected == null)
            return;

        for (XNode child : selected.getChildren()) {
            context.getOutput().addChild(child.cloneInstance());
        }
    }
}
