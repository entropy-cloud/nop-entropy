/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.record.initializer;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.validate.IValidationErrorCollector;
import io.nop.core.lang.xml.XNode;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.type.IGenericType;
import io.nop.record.match.IPeekMatchRule;
import io.nop.record.match.PeekMatchRuleParser;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.xdef.IStdDomainHandler;
import io.nop.xlang.xdef.XDefConstants;

import static io.nop.xlang.XLangErrors.ARG_PROP_NAME;
import static io.nop.xlang.XLangErrors.ARG_STD_DOMAIN;
import static io.nop.xlang.XLangErrors.ARG_VALUE;
import static io.nop.xlang.XLangErrors.ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN;

public class PeekMatchRuleStdDomainHandler implements IStdDomainHandler {
    public static PeekMatchRuleStdDomainHandler INSTANCE = new PeekMatchRuleStdDomainHandler();

    @Override
    public String getName() {
        return XDefConstants.STD_DOMAIN_PEEK_MATCH_RULE;
    }

    @Override
    public IGenericType getGenericType(boolean mandatory, String options) {
        return ReflectionManager.instance().buildRawType(IPeekMatchRule.class);
    }

    @Override
    public Object parseProp(String options, SourceLocation loc, String propName, Object text, XLangCompileTool cp) {
        String source = (String) text;


        try {
            return PeekMatchRuleParser.parseMatchRule(source);
        } catch (Exception e) {
            throw newPropError(loc, propName, source).cause(e);
        }
    }

    protected NopException newPropError(SourceLocation loc, String propName, String text) {
        return new NopException(ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN).loc(loc).param(ARG_PROP_NAME, propName)
                .param(ARG_STD_DOMAIN, getName()).param(ARG_VALUE, text);
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