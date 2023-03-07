/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.api;

import io.nop.api.core.util.ICancellable;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.ReflectionManager;

import java.util.HashMap;
import java.util.Map;

public class DefaultFunctionProvider implements IFunctionProvider {
    private final Map<String, IFunctionModel> functions = new HashMap<>();

    @Override
    public void registerFunction(String funcName, IFunctionModel fn) {
        functions.put(funcName, fn);
    }

    @Override
    public void unregisterFunction(String funcName, IFunctionModel fn) {
        functions.remove(funcName, fn);
    }

    @Override
    public IFunctionModel getRegisteredFunction(String funcName) {
        return functions.get(funcName);
    }

    public ICancellable registerStaticFunctions(Class<?> clazz) {
        Cancellable task = new Cancellable();

        IClassModel classModel = ReflectionManager.instance().getClassModel(clazz);
        for (IFunctionModel fn : classModel.getStaticMethods()) {
            if (fn.isPublic()) {
                registerFunction(fn.getName(), fn);
                task.appendOnCancelTask(() -> {
                    unregisterFunction(fn.getName(), fn);
                });
            }
        }
        return task;
    }
}
