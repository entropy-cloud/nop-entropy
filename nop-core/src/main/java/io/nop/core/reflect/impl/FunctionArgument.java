/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.impl;

import io.nop.api.core.convert.ITypeConverter;
import io.nop.api.core.convert.IdentityTypeConverter;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICloneable;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.IFunctionArgument;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;

public class FunctionArgument extends AnnotatedElement implements IFunctionArgument, ICloneable {
    private static final long serialVersionUID = 5242328988054427185L;

    private String name;
    private IGenericType type;
    private Class<?> rawClass;
    private ITypeConverter converter = IdentityTypeConverter.INSTANCE;
    private boolean nullable = true;

    public FunctionArgument() {
    }

    public FunctionArgument(String name, IGenericType type) {
        this.setName(name);
        this.setType(type);
    }

    public static FunctionArgument arg(String name, IGenericType type) {
        return new FunctionArgument(name, type);
    }

    // static FunctionArgument newObjectArgs() {
    // FunctionArgument arg = new FunctionArgument("args", PredefinedGenericTypes.ARRAY_ANY_TYPE);
    // arg.freeze(true);
    // return arg;
    // }

    public FunctionArgument cloneInstance() {
        FunctionArgument arg = new FunctionArgument();
        arg.setName(name);
        arg.setType(type);
        arg.setConverter(converter);
        arg.setNullable(nullable);
        arg.addAnnotations(this.getAnnotations());
        return arg;
    }

    public boolean isNullable() {
        return nullable;
    }

    public void setNullable(boolean nullable) {
        checkReadonly();
        this.nullable = nullable;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        checkReadonly();
        this.name = name;
    }

    @Override
    public IGenericType getType() {
        return type;
    }

    public void setType(IGenericType type) {
        checkReadonly();
        if (type == null) {
            type = PredefinedGenericTypes.ANY_TYPE;
        }
        this.type = type;
        this.rawClass = type.getRawClass();
    }

    @Override
    public Class<?> getRawClass() {
        return rawClass;
    }

    public ITypeConverter getConverter() {
        return converter;
    }

    public void setConverter(ITypeConverter converter) {
        checkReadonly();
        this.converter = converter;
    }

    @Override
    public Object castArg(Object value, IEvalScope scope) {
        return converter.convertEx(scope, value, NopException::new);
    }
}