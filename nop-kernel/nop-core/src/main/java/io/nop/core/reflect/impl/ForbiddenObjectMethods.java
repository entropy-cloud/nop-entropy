/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 禁止访问Object对象上的一些方法。例如notify/wait等锁相关的方法。
 */
public class ForbiddenObjectMethods {
    private static final Map<String, List<MethodInvokerKey>> methods = new HashMap<>();

    static {
        addMethod("notify");
        addMethod("getClass");
        addMethod("notifyAll");
        addMethod("wait");
        addMethod("wait", long.class);
        addMethod("wait", long.class, int.class);
        addMethod("finalize");
    }

    public static void addMethod(String name, Class<?>... argTypes) {
        List<MethodInvokerKey> list = methods.computeIfAbsent(name, k -> new ArrayList<>());
        list.add(new MethodInvokerKey(false, name, argTypes));
    }

    public static boolean contains(String name, Class<?>... argTypes) {
        List<MethodInvokerKey> list = methods.get(name);
        if (list == null)
            return false;
        for (MethodInvokerKey method : list) {
            if (Arrays.equals(method.getArgTypes(), argTypes))
                return true;
        }
        return false;
    }
}
