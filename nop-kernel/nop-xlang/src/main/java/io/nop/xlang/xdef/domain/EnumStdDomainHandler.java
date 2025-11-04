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
import io.nop.commons.util.StringHelper;
import io.nop.core.dict.EnumDictLoader;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.core.type.impl.GenericRawTypeReferenceImpl;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.xdef.IStdDomainHandler;
import io.nop.xlang.xdef.XDefConstants;

import java.util.List;

import static io.nop.xlang.XLangErrors.ARG_ALLOWED_NAMES;
import static io.nop.xlang.XLangErrors.ARG_CLASS_NAME;
import static io.nop.xlang.XLangErrors.ARG_DICT_NAME;
import static io.nop.xlang.XLangErrors.ARG_OPTIONS;
import static io.nop.xlang.XLangErrors.ARG_PROP_NAME;
import static io.nop.xlang.XLangErrors.ARG_STD_DOMAIN;
import static io.nop.xlang.XLangErrors.ARG_VALUE;
import static io.nop.xlang.XLangErrors.ERR_XDEF_ENUM_VALUE_NOT_IN_OPTIONS;
import static io.nop.xlang.XLangErrors.ERR_XDEF_ILLEGAL_CLASS_NAME_FOR_ENUM_DOMAIN;
import static io.nop.xlang.XLangErrors.ERR_XDEF_INVALID_ENUM_VALUE_FOR_PROP;

public class EnumStdDomainHandler implements IStdDomainHandler {

    @Override
    public String getName() {
        return XDefConstants.STD_DOMAIN_ENUM;
    }


    public IGenericType parseOptions(SourceLocation loc, String options) {
        if (options != null && options.indexOf('|') >= 0)
            return PredefinedGenericTypes.STRING_TYPE;

        if (!StringHelper.isValidClassName(options))
            throw new NopException(ERR_XDEF_ILLEGAL_CLASS_NAME_FOR_ENUM_DOMAIN).loc(loc)
                    .param(ARG_STD_DOMAIN, this.getName()).param(ARG_CLASS_NAME, options);
        IGenericType type = PredefinedGenericTypes.getPredefinedType(options);
        if (type == null)
            type = new GenericRawTypeReferenceImpl(options);
        return type;
    }

    @Override
    public IGenericType getGenericType(boolean mandatory, String options) {
        if (options != null)
            return parseOptions(null, options);

        return null;
    }

    @Override
    public Object parseProp(String options, SourceLocation loc, String propName, Object text,
                            XLangCompileTool cp) {
        if (options == null)
            return null;

        if (options.indexOf('|') >= 0) {
            List<String> list = StringHelper.stripedSplit(options, '|');
            if (!list.contains((String) text))
                throw new NopException(ERR_XDEF_ENUM_VALUE_NOT_IN_OPTIONS).loc(loc).param(ARG_PROP_NAME, propName)
                        .param(ARG_VALUE, text).param(ARG_OPTIONS, options);
            return text;

        }
        DictBean dict = EnumDictLoader.INSTANCE.loadDict(null, options, null);
        if (dict.getOptionByValue(text) == null) {
            if(text.equals("none"))
                return null;

            throw new NopException(ERR_XDEF_INVALID_ENUM_VALUE_FOR_PROP).loc(loc).param(ARG_PROP_NAME, propName)
                    .when(dict.getOptions().size() < 50, e -> {
                        e.param(ARG_ALLOWED_NAMES, dict.getLabels());
                    }).param(ARG_VALUE, text).param(ARG_DICT_NAME, options);
        }

        return text;
    }

    public IFunctionModel getFactoryMethod(String className) {
        IClassModel classModel = ReflectionManager.instance().loadClassModel(className);
        return classModel.getFactoryMethod();
    }

    @Override
    public void validate(SourceLocation loc, String propName, Object value, IValidationErrorCollector collector) {

    }
}