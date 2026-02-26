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

import static io.nop.xlang.XLangErrors.ARG_MAPPING_ID;
import static io.nop.xlang.XLangErrors.ERR_XT_MAPPING_NOT_FOUND;

public class ApplyMappingRule extends AbstractSelectorRule {
    private final String mappingId;
    private final IXTransformRule bodyRule;

    public ApplyMappingRule(String mappingId, IXSelector<XNode> xpath, boolean mandatory, IXTransformRule bodyRule) {
        super(xpath, mandatory);
        this.mappingId = mappingId;
        this.bodyRule = bodyRule;
    }

    @Override
    public void apply(XNode parent, XNode node, IXTransformContext context) {
        XNode selected = selectNode(node, context);
        if (selected == null)
            return;

        if (context.getMapping(mappingId) == null) {
            throw new NopException(ERR_XT_MAPPING_NOT_FOUND)
                    .param(ARG_MAPPING_ID, mappingId);
        }

        String tagName = selected.getTagName();
        IXTransformRule rule = context.getRuleForTag(mappingId, tagName);

        IXTransformContext childContext = context.childContext(selected);
        if (rule != null) {
            rule.apply(parent, selected, childContext);
        }

        if (bodyRule != null) {
            bodyRule.apply(parent, selected, childContext);
        }
    }

    public String getMappingId() {
        return mappingId;
    }
}
