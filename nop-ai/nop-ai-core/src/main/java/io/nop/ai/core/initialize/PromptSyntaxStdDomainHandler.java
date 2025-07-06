/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.core.initialize;

import io.nop.ai.core.prompt.node.IPromptSyntaxNode;
import io.nop.ai.core.prompt.node.PromptSyntaxParser;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.validate.IValidationErrorCollector;
import io.nop.core.lang.xml.XNode;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.type.IGenericType;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.xdef.IStdDomainHandler;
import io.nop.xlang.xdef.XDefConstants;

import static io.nop.xlang.XLangErrors.ARG_PROP_NAME;
import static io.nop.xlang.XLangErrors.ARG_STD_DOMAIN;
import static io.nop.xlang.XLangErrors.ARG_VALUE;
import static io.nop.xlang.XLangErrors.ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN;

public class PromptSyntaxStdDomainHandler implements IStdDomainHandler {
    public static PromptSyntaxStdDomainHandler INSTANCE = new PromptSyntaxStdDomainHandler();

    @Override
    public String getName() {
        return XDefConstants.STD_DOMAIN_PROMPT_SYNTAX;
    }

    @Override
    public IGenericType getGenericType(boolean mandatory, String options) {
        return ReflectionManager.instance().buildRawType(IPromptSyntaxNode.class);
    }

    @Override
    public Object parseProp(String options, SourceLocation loc, String propName, Object text, XLangCompileTool cp) {
        String source = (String) text;

        try {
            IPromptSyntaxNode expr = new PromptSyntaxParser().enableInclude(true).parseFromText(loc, source);
            return expr;
        } catch (Exception e) {
            throw new NopException(ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN, e).loc(loc).param(ARG_PROP_NAME, propName)
                    .param(ARG_STD_DOMAIN, getName()).param(ARG_VALUE, text);
        }
    }

    @Override
    public Object parseXmlChild(String options, XNode body, XLangCompileTool cp) {
        return parseProp(options, body.content().getLocation(), "body", body.contentText(), cp);
    }

    @Override
    public void validate(SourceLocation loc, String propName, Object value, IValidationErrorCollector collector) {
        try {
            parseProp(null, loc, propName, value, null);
        } catch (Exception e) {
            collector.addException(e);
        }
    }
}