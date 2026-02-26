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

public class CompositeRule implements IXTransformRule {
    private final List<IXTransformRule> rules;

    public CompositeRule(List<IXTransformRule> rules) {
        this.rules = rules;
    }

    @Override
    public void apply(XNode parent, XNode node, IXTransformContext context) {
        for (IXTransformRule rule : rules) {
            rule.apply(parent, node, context);
        }
    }

    public List<IXTransformRule> getRules() {
        return rules;
    }
}
