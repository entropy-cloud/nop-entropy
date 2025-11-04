/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdef.domain;

import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.xlang.xdef.XDefConstants;

public class XJsonMapDomainHandler extends XJsonDomainHandler {
    @Override
    public String getName() {
        return XDefConstants.STD_DOMAIN_XJSON_MAP;
    }

    @Override
    public IGenericType getGenericType(boolean mandatory, String options) {
        return PredefinedGenericTypes.MAP_STRING_ANY_TYPE;
    }
}