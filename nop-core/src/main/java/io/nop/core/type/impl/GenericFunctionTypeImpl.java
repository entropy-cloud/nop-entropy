/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.type.impl;

import io.nop.api.core.util.Guard;
import io.nop.commons.type.StdDataType;
import io.nop.core.type.GenericTypeKind;
import io.nop.core.type.IFunctionType;
import io.nop.core.type.IGenericType;
import io.nop.core.type.IRawTypeResolver;
import io.nop.core.type.ITypeScope;
import io.nop.core.type.utils.GenericTypeHelper;

import java.util.Collections;
import java.util.List;

public class GenericFunctionTypeImpl extends AbstractGenericType implements IFunctionType {
    private final List<IGenericType> typeParams;
    private final List<String> argNames;
    private final List<IGenericType> argTypes;
    private final IGenericType returnType;

    private String typeName;

    public GenericFunctionTypeImpl(List<IGenericType> typeParams, List<String> argNames, List<IGenericType> argTypes,
                                   IGenericType returnType) {
        this.typeParams = Guard.notNull(typeParams, "typeParams");
        this.argNames = Guard.notNull(argNames, "argNames");
        this.argTypes = Guard.notNull(argTypes, "argTypes");
        this.returnType = Guard.notNull(returnType, "returnType");
        if (argNames.size() != argTypes.size())
            throw new IllegalArgumentException("nop.core.func-type.arg-names-size-not-match-with-arg-types-size:"
                    + argNames.size() + "," + argTypes.size());
    }

    public GenericFunctionTypeImpl(List<String> argNames, List<IGenericType> argTypes, IGenericType returnType) {
        this(Collections.emptyList(), argNames, argTypes, returnType);
    }

    @Override
    public boolean isResolved() {
        for (IGenericType typeParam : typeParams) {
            if (!typeParam.isResolved())
                return false;
        }

        for (IGenericType argType : argTypes) {
            if (!argType.isResolved())
                return false;
        }

        return returnType.isResolved();
    }

    @Override
    public void resolve(IRawTypeResolver resolver) {
        typeParams.forEach(type -> type.resolve(resolver));
        argTypes.forEach(type -> type.resolve(resolver));
        returnType.resolve(resolver);
    }

    @Override
    public IGenericType refine(ITypeScope resolver) {
        List<IGenericType> refinedParams = GenericTypeHelper.refineTypes(typeParams, resolver);
        List<IGenericType> refinedArgs = GenericTypeHelper.refineTypes(argTypes, resolver);
        IGenericType refinedReturn = returnType.refine(resolver);
        if (refinedParams != typeParams || refinedArgs != argTypes || returnType != refinedReturn)
            return new GenericFunctionTypeImpl(refinedParams, argNames, refinedArgs, refinedReturn);
        return this;
    }

    @Override
    public GenericTypeKind getKind() {
        return GenericTypeKind.FUNCTION;
    }

    @Override
    public List<IGenericType> getTypeParameters() {
        return typeParams;
    }

    @Override
    public List<IGenericType> getFuncArgTypes() {
        return argTypes;
    }

    @Override
    public List<String> getFuncArgNames() {
        return argNames;
    }

    @Override
    public IGenericType getFuncReturnType() {
        return returnType;
    }

    public String getTypeName() {
        if (typeName != null)
            return typeName;

        StringBuilder sb = new StringBuilder();
        if (!typeParams.isEmpty()) {
            sb.append('<');
            for (int i = 0, n = typeParams.size(); i < n; i++) {
                IGenericType var = typeParams.get(i);
                sb.append(var.toString());
                if (i != n - 1)
                    sb.append(',');
            }
            sb.append('>');
        }
        sb.append('(');
        for (int i = 0, n = argTypes.size(); i < n; i++) {
            sb.append(argNames.get(i));
            sb.append(':');
            sb.append(argTypes.get(i));
            if (i != n - 1)
                sb.append(',');
        }
        sb.append(')');
        sb.append("=>");
        sb.append(returnType);

        typeName = sb.toString();
        return typeName;
    }

    @Override
    public String getRawTypeName() {
        StringBuilder sb = new StringBuilder();

        sb.append('(');
        for (int i = 0, n = argTypes.size(); i < n; i++) {
            sb.append(argNames.get(i));
            sb.append(':');
            sb.append(argTypes.get(i).getRawTypeName());
            if (i != n - 1)
                sb.append(',');
        }
        sb.append(')');
        sb.append("=>");
        sb.append(returnType.getRawTypeName());

        return sb.toString();
    }

    @Override
    public StdDataType getStdDataType() {
        return StdDataType.ANY;
    }
}
