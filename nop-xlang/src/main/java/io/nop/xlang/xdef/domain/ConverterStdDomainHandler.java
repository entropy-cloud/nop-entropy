/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xdef.domain;

import io.nop.api.core.convert.ITypeConverter;
import io.nop.api.core.convert.IdentityTypeConverter;
import io.nop.api.core.convert.SysConverterRegistry;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.type.StdDataType;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.xdef.IStdDomainOptions;

import static io.nop.xlang.XLangErrors.ARG_PROP_NAME;
import static io.nop.xlang.XLangErrors.ARG_STD_DOMAIN;
import static io.nop.xlang.XLangErrors.ARG_VALUE;
import static io.nop.xlang.XLangErrors.ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN;

public class ConverterStdDomainHandler extends SimpleStdDomainHandler {
    private final String name;
    private final IGenericType mandatoryType;
    private final IGenericType type;
    private final ITypeConverter converter;
    private final boolean fixedType;

    public ConverterStdDomainHandler(String name, boolean fixedType, IGenericType mandatoryType, IGenericType type,
                                     ITypeConverter converter) {
        this.name = name;
        this.type = type;
        this.converter = converter;
        this.mandatoryType = mandatoryType;
        this.fixedType = fixedType;
    }

    public static ConverterStdDomainHandler stdTypeHandler(StdDataType type) {
        ITypeConverter converter = SysConverterRegistry.instance().getConverterByType(type.getJavaClass());
        IGenericType genericType = PredefinedGenericTypes.getPredefinedTypeForJavaType(type.getJavaClass());
        if (converter == null)
            converter = IdentityTypeConverter.INSTANCE;
        Guard.notNull(genericType, "genericType");

        IGenericType mandatoryType = PredefinedGenericTypes.getPredefinedTypeForJavaType(type.getMandatoryJavaClass());

        return new ConverterStdDomainHandler(type.getName(), true, mandatoryType, genericType, converter);
    }

    @Override
    public boolean isFixedType() {
        return fixedType;
    }

    @Override
    public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
        return mandatory ? mandatoryType : type;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object text,
                            XLangCompileTool cp) {
        return converter.convert(text, err -> {
            throw new NopException(ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN).loc(loc).param(ARG_PROP_NAME, propName)
                    .param(ARG_STD_DOMAIN, getName()).param(ARG_VALUE, text);
        });
    }
}
