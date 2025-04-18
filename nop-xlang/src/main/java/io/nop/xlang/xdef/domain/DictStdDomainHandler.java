/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdef.domain;

import io.nop.api.core.beans.DictBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.validate.IValidationErrorCollector;
import io.nop.commons.type.StdDataType;
import io.nop.core.dict.DictProvider;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.xdef.IStdDomainHandler;
import io.nop.xlang.xdef.XDefConstants;

import static io.nop.xlang.XLangErrors.ARG_ALLOWED_VALUES;
import static io.nop.xlang.XLangErrors.ARG_DICT_NAME;
import static io.nop.xlang.XLangErrors.ARG_PROP_NAME;
import static io.nop.xlang.XLangErrors.ARG_VALUE;
import static io.nop.xlang.XLangErrors.ERR_XDEF_INVALID_ENUM_VALUE_FOR_PROP;

public class DictStdDomainHandler implements IStdDomainHandler {

    @Override
    public String getName() {
        return XDefConstants.STD_DOMAIN_DICT;
    }

    @Override
    public IGenericType getGenericType(boolean mandatory, String options) {
        DictBean dict = DictProvider.instance().getDict(null, options, null, null);
        if (dict == null || dict.getValueType() == null)
            return PredefinedGenericTypes.STRING_TYPE;

        StdDataType dataType = StdDataType.fromStdName(dict.getValueType());
        if (dataType == null)
            return PredefinedGenericTypes.STRING_TYPE;
        Class<?> clazz = mandatory ? dataType.getMandatoryJavaClass() : dataType.getJavaClass();
        return PredefinedGenericTypes.getPredefinedTypeForJavaType(clazz);
    }

    @Override
    public Object parseProp(String options, SourceLocation loc, String propName, Object text,
                            XLangCompileTool cp) {
        if (options == null)
            return null;

        DictBean dict = DictProvider.instance().requireDict(null, options, null, null);
        if (dict.getOptionByValue(text) == null)
            throw new NopException(ERR_XDEF_INVALID_ENUM_VALUE_FOR_PROP).loc(loc).param(ARG_PROP_NAME, propName)
                    .when(dict.getOptionCount() < 50, e -> {
                        e.param(ARG_ALLOWED_VALUES, dict.getValues());
                    }).param(ARG_VALUE, text).param(ARG_DICT_NAME, options);

        return text;
    }

    @Override
    public void validate(SourceLocation loc, String propName, Object value, IValidationErrorCollector collector) {

    }
}