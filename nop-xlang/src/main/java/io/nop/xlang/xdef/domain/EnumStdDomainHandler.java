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
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.core.type.impl.GenericRawTypeReferenceImpl;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.xdef.IStdDomainHandler;
import io.nop.xlang.xdef.IStdDomainOptions;
import io.nop.xlang.xdef.XDefConstants;

import static io.nop.xlang.XLangErrors.ARG_ALLOWED_NAMES;
import static io.nop.xlang.XLangErrors.ARG_CLASS_NAME;
import static io.nop.xlang.XLangErrors.ARG_DICT_NAME;
import static io.nop.xlang.XLangErrors.ARG_PROP_NAME;
import static io.nop.xlang.XLangErrors.ARG_STD_DOMAIN;
import static io.nop.xlang.XLangErrors.ARG_VALUE;
import static io.nop.xlang.XLangErrors.ERR_XDEF_ILLEGAL_CLASS_NAME_FOR_ENUM_DOMAIN;
import static io.nop.xlang.XLangErrors.ERR_XDEF_INVALID_ENUM_VALUE_FOR_PROP;

public class EnumStdDomainHandler implements IStdDomainHandler {

    @Override
    public String getName() {
        return XDefConstants.STD_DOMAIN_ENUM;
    }

    @Override
    public IStdDomainOptions parseOptions(SourceLocation loc, String options) {
        if (!StringHelper.isValidClassName(options))
            throw new NopException(ERR_XDEF_ILLEGAL_CLASS_NAME_FOR_ENUM_DOMAIN).loc(loc)
                    .param(ARG_STD_DOMAIN, this.getName()).param(ARG_CLASS_NAME, options);
        IGenericType type = PredefinedGenericTypes.getPredefinedType(options);
        if (type == null)
            type = new GenericRawTypeReferenceImpl(options);
        return new GenericTypeDomainOptions(type);
    }

    @Override
    public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
        if (options != null)
            return ((GenericTypeDomainOptions) options).getGenericType();

        return null;
    }

    @Override
    public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object text,
                            XLangCompileTool cp) {
        if (options == null)
            return null;

        GenericTypeDomainOptions opts = (GenericTypeDomainOptions) options;
        IFunctionModel factoryMethod = opts.getFactoryMethod();
        if(factoryMethod != null)
            return factoryMethod.call1(null, text, DisabledEvalScope.INSTANCE);

        DictBean dict = opts.loadDictBean();
        if (dict.getOptionByValue(text) == null)
            throw new NopException(ERR_XDEF_INVALID_ENUM_VALUE_FOR_PROP).loc(loc).param(ARG_PROP_NAME, propName)
                    .when(dict.getOptions().size() < 50, e -> {
                        e.param(ARG_ALLOWED_NAMES, dict.getLabels());
                    }).param(ARG_VALUE, text).param(ARG_DICT_NAME, opts.getTypeName());

        return text;
    }

    @Override
    public void validate(SourceLocation loc, String propName, Object value, IValidationErrorCollector collector) {

    }
}