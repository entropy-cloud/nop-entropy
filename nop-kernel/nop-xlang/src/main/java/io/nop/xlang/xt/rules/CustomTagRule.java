/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xt.rules;

import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.xml.IXSelector;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.xt.IXTransformRule;
import io.nop.xlang.xt.IXTransformContext;

import java.util.Map;

public class CustomTagRule extends AbstractSelectorRule {
    private final String tagName;
    private final Map<String, IEvalAction> attrs;
    private final IEvalAction xtAttrs;
    private final IXTransformRule bodyRule;

    public CustomTagRule(String tagName,
                         IXSelector<XNode> xpath,
                         Map<String, IEvalAction> attrs,
                         IEvalAction xtAttrs,
                         IXTransformRule bodyRule) {
        super(xpath, false);
        this.tagName = tagName;
        this.attrs = attrs;
        this.xtAttrs = xtAttrs;
        this.bodyRule = bodyRule;
    }

    @Override
    public void apply(XNode parent, XNode node, IXTransformContext context) {
        XNode selected = selectNode(node, context);
        if (selected == null)
            return;

        XNode outputNode = context.getOutput().newOutputNode(tagName);

        context.getEvalScope().setLocalValue("node", selected);
        context.getEvalScope().setLocalValue("context", context);
        context.getEvalScope().setLocalValue("params", context.getParameters());

        if (attrs != null) {
            for (Map.Entry<String, IEvalAction> entry : attrs.entrySet()) {
                Object value = entry.getValue().invoke(context.getEvalScope());
                if (value != null) {
                    outputNode.setAttr(entry.getKey(), value);
                }
            }
        }

        if (xtAttrs != null) {
            Object value = xtAttrs.invoke(context.getEvalScope());
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> attrsMap = (Map<String, Object>) value;
                for (Map.Entry<String, Object> entry : attrsMap.entrySet()) {
                    if (!outputNode.hasAttr(entry.getKey())) {
                        outputNode.setAttr(entry.getKey(), entry.getValue());
                    }
                }
            }
        }

        context.getOutput().addChild(outputNode);
        context.getOutput().pushNode(outputNode);

        try {
            if (bodyRule != null) {
                IXTransformContext childContext = context.childContext(selected, outputNode);
                bodyRule.apply(outputNode, selected, childContext);
            }
        } finally {
            context.getOutput().popNode();
        }
    }

    public String getTagName() {
        return tagName;
    }

    public Map<String, IEvalAction> getAttrs() {
        return attrs;
    }

    public IEvalAction getXtAttrs() {
        return xtAttrs;
    }

    public IXTransformRule getBodyRule() {
        return bodyRule;
    }
}
