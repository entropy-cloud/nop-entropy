/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.impl;

import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFunctionModel;

import java.util.ArrayList;
import java.util.List;

public class HelperMethodsBuilder {
    private final Class<?> targetClass;
    private final IClassModel helperClass;
    private final String methodPrefix;

    public HelperMethodsBuilder(Class<?> targetClass, IClassModel helperClass,
                                String methodPrefix) {
        this.targetClass = targetClass;
        this.helperClass = helperClass;
        this.methodPrefix = methodPrefix;
    }

    public List<IFunctionModel> build() {
        List<IFunctionModel> mtds = new ArrayList<>();

        for (IFunctionModel method : helperClass.getStaticMethods()) {
            if (isForTarget(method)) {
                IFunctionModel mtd = buildMethod(method);
                mtds.add(mtd);
            }
        }
        return mtds;
    }

    private boolean isForTarget(IFunctionModel method) {
        if (method.getArgCount() <= 0 || !method.isPublic())
            return false;
        Class firstArg = method.getArgs().get(0).getRawClass();
        if (firstArg.isAssignableFrom(targetClass))
            return true;
        return false;
    }

    private IFunctionModel buildMethod(IFunctionModel method) {
        String name = getName(method);
        FunctionModel mtd = new FunctionModel();
        mtd.setModifiers(method.getModifiers());
        mtd.addAnnotations(method.getAnnotations());
        mtd.setName(name);
        mtd.setVarArgs(method.isVarArgs());
        mtd.setReturnType(method.getReturnType());
        List<FunctionArgument> args = (List<FunctionArgument>) method.getArgs().subList(1, method.getArgCount());
        mtd.setArgs(args);
        mtd.setInvoker(new HelperMethodInvoker(method.getInvoker()));
        // mtd.freeze(true);
        return mtd;
    }

    private String getName(IFunctionModel method) {
        String name = method.getName();
        if (methodPrefix != null)
            name = methodPrefix + name;
        return name;
    }
}