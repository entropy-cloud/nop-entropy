/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xdef.domain;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.validate.IValidationErrorCollector;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.handler.CollectJObjectHandler;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.xdef.IStdDomainHandler;
import io.nop.xlang.xdef.IStdDomainOptions;
import io.nop.xlang.xdef.XDefConstants;

import static io.nop.xlang.XLangErrors.ARG_PROP_NAME;
import static io.nop.xlang.XLangErrors.ARG_STD_DOMAIN;
import static io.nop.xlang.XLangErrors.ERR_XDEF_STD_DOMAIN_NOT_ALLOW_NODE_CONTENT;
import static io.nop.xlang.XLangErrors.ERR_XDEF_STD_DOMAIN_NOT_SUPPORT_PROP;

public class XJsonListDomainHandler implements IStdDomainHandler {
    @Override
    public String getName() {
        return XDefConstants.STD_DOMAIN_XJSON_LIST;
    }

    @Override
    public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
        return PredefinedGenericTypes.LIST_ANY_TYPE;
    }

    @Override
    public boolean supportXmlChild() {
        return true;
    }

    @Override
    public Object parseXmlChild(IStdDomainOptions options, XNode body, XLangCompileTool cp) {
        if (body.hasContent())
            throw new NopException(ERR_XDEF_STD_DOMAIN_NOT_ALLOW_NODE_CONTENT).loc(body.getLocation())
                    .param(ARG_STD_DOMAIN, getName());
        if (!body.hasChild())
            return null;

        CollectJObjectHandler handler = new CollectJObjectHandler();
        for (XNode child : body.getChildren()) {
            child.process(handler);
        }
        return handler.getList();
    }

    @Override
    public boolean isFullXmlNode() {
        return false;
    }

    @Override
    public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object text,
                            XLangCompileTool cp) {
        throw new NopException(ERR_XDEF_STD_DOMAIN_NOT_SUPPORT_PROP).loc(loc).param(ARG_STD_DOMAIN, getName())
                .param(ARG_PROP_NAME, propName);
    }

    @Override
    public void validate(SourceLocation loc, String propName, Object value, IValidationErrorCollector collector) {
        
    }
}