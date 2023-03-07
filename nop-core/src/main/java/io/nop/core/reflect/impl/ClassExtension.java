/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.impl;

import io.nop.core.reflect.IFunctionModel;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ClassExtension {
    private List<IFunctionModel> helperMethods = new ArrayList<>();
    private List<WeakReference<Class>> extendedClasses = new ArrayList<>();

    public synchronized void addHelperMethods(List<IFunctionModel> methods) {
        helperMethods.addAll(methods);
    }

    public synchronized boolean removeHelperMethods(List<IFunctionModel> methods) {
        return helperMethods.removeAll(methods);
    }

    public synchronized List<IFunctionModel> getHelperMethods() {
        return new ArrayList<>(helperMethods);
    }

    public synchronized void recordExtendedClass(Class extended) {
        Iterator<WeakReference<Class>> it = extendedClasses.iterator();
        while (it.hasNext()) {
            WeakReference<Class> ref = it.next();
            Class clazz = ref.get();
            if (clazz == null) {
                it.remove();
            } else {
                if (extended == clazz)
                    return;
            }
        }
        extendedClasses.add(new WeakReference<>(extended));
    }

    public synchronized List<Class> getExtendedClasses() {
        List<Class> ret = new ArrayList<>(extendedClasses.size());
        Iterator<WeakReference<Class>> it = extendedClasses.iterator();
        while (it.hasNext()) {
            WeakReference<Class> ref = it.next();
            Class clazz = ref.get();
            if (clazz == null) {
                it.remove();
            } else {
                ret.add(clazz);
            }
        }
        return ret;
    }
}