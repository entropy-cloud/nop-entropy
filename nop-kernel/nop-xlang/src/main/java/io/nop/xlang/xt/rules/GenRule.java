/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xt.rules;

import io.nop.core.lang.xml.IXNodeGenerator;
import io.nop.core.lang.xml.IXSelector;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.xt.IXTransformContext;

public class GenRule extends AbstractSelectorRule {
    private final IXNodeGenerator generator;

    public GenRule(IXSelector<XNode> xpath, IXNodeGenerator generator) {
        super(xpath, false);
        this.generator = generator;
    }

    @Override
    public void apply(XNode parent, XNode node, IXTransformContext context) {
        XNode selected = selectNode(node, context);
        if (selected == null)
            return;

        XNode generated = generator.generateNode(context.getEvalScope());
        if (generated != null) {
            if (generated.isDummyNode()) {
                // 多根输出被包在 dummy 节点中，展开为其子节点
                context.getOutput().getCurrentNode().appendChildren(generated.detachChildren());
            } else {
                context.getOutput().addChild(generated);
            }
        }
    }

    public IXNodeGenerator getGenerator() {
        return generator;
    }
}
