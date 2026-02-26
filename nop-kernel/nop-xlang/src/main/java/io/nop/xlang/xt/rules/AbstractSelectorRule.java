/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xt.rules;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.xml.IXSelector;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.xt.IXTransformRule;
import io.nop.xlang.xt.IXTransformContext;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static io.nop.xlang.XLangErrors.ARG_NODE;
import static io.nop.xlang.XLangErrors.ARG_XPATH;
import static io.nop.xlang.XLangErrors.ERR_XT_MANDATORY_NODE_NOT_FOUND;

public abstract class AbstractSelectorRule implements IXTransformRule {
    protected final IXSelector<XNode> xpath;
    protected final boolean mandatory;

    protected AbstractSelectorRule(IXSelector<XNode> xpath, boolean mandatory) {
        this.xpath = xpath;
        this.mandatory = mandatory;
    }

    protected XNode selectNode(XNode node, IXTransformContext context) {
        if (xpath == null)
            return node;
        context.setThisNode(node);
        Object selected = xpath.select(node);
        if (selected == null && mandatory) {
            throw new NopException(ERR_XT_MANDATORY_NODE_NOT_FOUND)
                    .param(ARG_XPATH, xpath)
                    .param(ARG_NODE, node);
        }
        return selected instanceof XNode ? (XNode) selected : node;
    }

    @SuppressWarnings("unchecked")
    protected List<XNode> selectNodes(XNode node, IXTransformContext context) {
        if (xpath == null)
            return Collections.singletonList(node);
        context.setThisNode(node);
        Collection<?> result = xpath.selectAll(node);
        if (result == null || result.isEmpty())
            return Collections.emptyList();
        return (List<XNode>) result;
    }

    public IXSelector<XNode> getXpath() {
        return xpath;
    }

    public boolean isMandatory() {
        return mandatory;
    }
}
