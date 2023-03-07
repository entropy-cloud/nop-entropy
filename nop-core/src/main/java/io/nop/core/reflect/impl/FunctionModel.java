/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.impl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.reflect.IFunctionArgument;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.type.IFunctionType;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.core.type.impl.GenericFunctionTypeImpl;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;

public class FunctionModel extends AnnotatedElement implements IFunctionModel {

    private static final long serialVersionUID = 4233783034313538243L;

    private SourceLocation location;
    private String name;
    private String implName;
    private int modifiers = Modifier.PUBLIC;
    private List<FunctionArgument> args = Collections.emptyList();
    private IGenericType returnType = PredefinedGenericTypes.ANY_TYPE;

    private Class<?> declaringClass;

    private IGenericType asyncReturnType = returnType;
    private IEvalFunction invoker;
    private boolean varArgs;
    private Class<?>[] argRawTypes;
    private boolean returnNullable = true;
    private boolean deterministic;
    private boolean macro;
    private boolean deprecated;
    private Class<?> varArgElementClass;
    private int minArgCount;
    private int maxArgCount;
    // private boolean templateStringMethod;

    private IFunctionType functionType;

    private List<Class<?>> exceptionTypes = Collections.emptyList();

    public FunctionModel() {
    }

    public SourceLocation getLocation() {
        return location;
    }

    public void setLocation(SourceLocation location) {
        this.location = location;
    }

    public String toString() {
        return FunctionModel.class.getSimpleName() + ":" + getName() + "(" + getArgCount() + ")";
    }

    public FunctionModel cloneInstance() {
        FunctionModel ret = new FunctionModel();
        ret.setName(name);
        ret.setImplName(implName);
        ret.setModifiers(modifiers);
        ret.setArgs(args);
        ret.setReturnType(returnType);
        ret.setInvoker(invoker);
        ret.setVarArgs(varArgs);
        ret.setDeclaringClass(declaringClass);
        ret.setReturnNullable(returnNullable);
        ret.setDeterministic(deterministic);
        ret.setMacro(macro);
        ret.setDeprecated(deprecated);
        ret.setExceptionTypes(exceptionTypes);
        ret.addAnnotations(getAnnotations());
        ret.setMinArgCount(minArgCount);
        ret.setMaxArgCount(maxArgCount);
        return ret;
    }

    @Override
    public int getMinArgCount() {
        return minArgCount;
    }

    public void setMinArgCount(int minArgCount) {
        this.minArgCount = minArgCount;
    }

    @Override
    public int getMaxArgCount() {
        if (maxArgCount == 0) {
            if (varArgs)
                return 100;
            return args.size();
        }
        return maxArgCount;
    }

    public void setMaxArgCount(int maxArgCount) {
        this.maxArgCount = maxArgCount;
    }

    @Override
    public Class<?> getDeclaringClass() {
        return declaringClass;
    }

    public void setDeclaringClass(Class<?> declaringClass) {
        this.declaringClass = declaringClass;
    }

    @Override
    public List<Class<?>> getExceptionTypes() {
        return exceptionTypes;
    }

    public void setExceptionTypes(List<Class<?>> exceptionTypes) {
        this.exceptionTypes = exceptionTypes;
    }

    @Override
    public boolean isMacro() {
        return macro;
    }

    public void setMacro(boolean macro) {
        checkReadonly();
        this.macro = macro;
    }

    public boolean isEvalMethod() {
        return invoker instanceof EvalMethodInvoker;
    }

    public boolean isHelperMethod() {
        return invoker instanceof HelperMethodInvoker;
    }

    @Override
    public boolean isDeprecated() {
        return deprecated;
    }

    public void setDeprecated(boolean deprecated) {
        checkReadonly();
        this.deprecated = deprecated;
    }

    @Override
    public boolean isDeterministic() {
        return deterministic;
    }

    public void setDeterministic(boolean deterministic) {
        checkReadonly();
        this.deterministic = deterministic;
    }

    public boolean isReturnNullable() {
        return returnNullable;
    }

    public void setReturnNullable(boolean returnNullable) {
        checkReadonly();
        this.returnNullable = returnNullable;
    }

    public IGenericType getAsyncReturnType() {
        return asyncReturnType;
    }

    @Override
    public IFunctionType getFunctionType() {
        if (functionType == null) {
            List<String> argNames = new ArrayList<>(this.args.size());
            List<IGenericType> argTypes = new ArrayList<>(this.args.size());
            for (IFunctionArgument arg : args) {
                argNames.add(arg.getName());
                argTypes.add(arg.getType());
            }
            functionType = new GenericFunctionTypeImpl(Collections.emptyList(),
                    CollectionHelper.immutableList(argNames), CollectionHelper.immutableList(argTypes), returnType);
        }
        return functionType;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        checkReadonly();
        this.name = name;
    }

    public String getImplName() {
        if (implName == null)
            return name;
        return implName;
    }

    public void setImplName(String implName) {
        checkReadonly();
        this.implName = implName;
    }

    @Override
    public Class<?> getReturnClass() {
        return returnType.getRawClass();
    }

    @Override
    public int getModifiers() {
        return modifiers;
    }

    public void setModifiers(int modifiers) {
        checkReadonly();
        this.modifiers = modifiers;
    }

    @Override
    public List<FunctionArgument> getArgs() {
        return args;
    }

    public void setArgs(List<FunctionArgument> arguments) {
        checkReadonly();
        this.args = arguments;
    }

    @Override
    public Class<?>[] getArgRawTypes() {
        if (argRawTypes == null) {
            argRawTypes = buildArgRawTypes();
        }
        return argRawTypes;
    }

    Class<?>[] buildArgRawTypes() {
        Class<?>[] types = new Class[args.size()];
        for (int i = 0, n = args.size(); i < n; i++) {
            types[i] = args.get(i).getRawClass();
        }
        return types;
    }

    @Override
    public IGenericType getReturnType() {
        return returnType;
    }

    public void setReturnType(IGenericType returnType) {
        checkReadonly();
        this.returnType = returnType;
        if (isAsync()) {
            this.asyncReturnType = returnType.getGenericType(CompletionStage.class).getTypeParameters().get(0);
        } else {
            this.asyncReturnType = returnType;
        }
    }

    @JsonIgnore
    @Override
    public IEvalFunction getInvoker() {
        return invoker;
    }

    public void setInvoker(IEvalFunction invoker) {
        checkReadonly();
        this.invoker = invoker;
    }

    @Override
    public boolean isVarArgs() {
        return varArgs;
    }

    public void setVarArgs(boolean varArgs) {
        checkReadonly();
        this.varArgs = varArgs;
    }

    @Override
    public Class<?> getVarArgElementClass() {
        if (varArgElementClass == null) {
            if (!varArgs)
                return null;
            varArgElementClass = getArgs().get(args.size() - 1).getRawClass().getComponentType();
        }
        return varArgElementClass;
    }
}