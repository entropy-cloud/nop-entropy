/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdef.domain;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.validate.IValidationErrorCollector;
import io.nop.core.lang.xml.XNode;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.xdef.IStdDomainHandler;
import io.nop.xlang.xdef.XDefConstants;

import static io.nop.xlang.XLangErrors.ARG_PROP_NAME;
import static io.nop.xlang.XLangErrors.ARG_STD_DOMAIN;
import static io.nop.xlang.XLangErrors.ERR_XDEF_STD_DOMAIN_NOT_SUPPORT_PROP;

public class XJsonDomainHandler implements IStdDomainHandler {
    @Override
    public String getName() {
        return XDefConstants.STD_DOMAIN_XJSON;
    }

    @Override
    public IGenericType getGenericType(boolean mandatory, String options) {
        return PredefinedGenericTypes.ANY_TYPE;
    }

    @Override
    public boolean supportXmlChild() {
        return true;
    }

    @Override
    public Object parseXmlChild(String options, XNode body, XLangCompileTool cp) {
        return body.toXJson();
    }

    @Override
    public boolean isFullXmlNode() {
        return true;
    }

    @Override
    public Object parseProp(String options, SourceLocation loc, String propName, Object text,
                            XLangCompileTool cp) {
        throw new NopException(ERR_XDEF_STD_DOMAIN_NOT_SUPPORT_PROP).loc(loc).param(ARG_STD_DOMAIN, getName())
                .param(ARG_PROP_NAME, propName);
    }

    @Override
    public void validate(SourceLocation loc, String propName, Object value, IValidationErrorCollector collector) {

    }
}