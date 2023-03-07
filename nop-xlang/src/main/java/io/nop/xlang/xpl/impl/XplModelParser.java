/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpl.impl;

import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.parse.AbstractResourceParser;
import io.nop.xlang.api.ExprEvalAction;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XplModel;
import io.nop.xlang.ast.XLangOutputMode;
import io.nop.xlang.feature.XModelInclude;

public class XplModelParser extends AbstractResourceParser<XplModel> {
    private XLangOutputMode outputMode = XLangOutputMode.html;

    public XplModelParser outputModel(XLangOutputMode outputMode) {
        this.outputMode = outputMode;
        return this;
    }

    @Override
    protected XplModel doParseResource(IResource resource) {
        XNode node = XModelInclude.instance().loadActiveNodeFromResource(resource);
        ExprEvalAction action = XLang.newCompileTool().allowUnregisteredScopeVar(true).compileTag(node, outputMode);
        return action == null ? null : action.toXplModel();
    }
}
