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
import io.nop.core.CoreConstants;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.xdef.IStdDomainOptions;

import static io.nop.xlang.XLangErrors.ARG_PROP_NAME;
import static io.nop.xlang.XLangErrors.ARG_STD_DOMAIN;
import static io.nop.xlang.XLangErrors.ARG_VALUE;
import static io.nop.xlang.XLangErrors.ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN;

public abstract class CheckStdDomainHandler extends StringStdDomainHandler {
    @Override
    public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object value,
                            XLangCompileTool cp) {
        String text = value.toString();
        if(CoreConstants.XML_PROP_BODY.equals(propName))
            text = text.trim();
        if (!isValid(text)) {
            throw new NopException(ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN).loc(loc).param(ARG_PROP_NAME, propName)
                    .param(ARG_STD_DOMAIN, getName()).param(ARG_VALUE, text);
        }
        return text;
    }

    public boolean isFixedType() {
        return true;
    }

    protected abstract boolean isValid(String text);

}