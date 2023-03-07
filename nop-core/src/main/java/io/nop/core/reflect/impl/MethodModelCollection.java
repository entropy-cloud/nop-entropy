/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FreezeHelper;
import io.nop.commons.util.ArrayHelper;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.IMethodModelCollection;

import java.util.ArrayList;
import java.util.List;

import static io.nop.core.CoreErrors.ARG_MODEL;
import static io.nop.core.CoreErrors.ERR_REFLECT_MODEL_IS_READONLY;

public class MethodModelCollection implements IMethodModelCollection {
    static final IFunctionModel[] EMPTY_METHODS = new IFunctionModel[0];
    public static final MethodModelCollection EMPTY = createEmpty();

    private List<IFunctionModel> methods = new ArrayList<>(1);
    // 当对应参数个数的方法只有唯一一个时，对应条目才不为null
    private IFunctionModel[] methodByArgCounts = EMPTY_METHODS;

    /**
     * 只有当方法名对应的方法是唯一一个时，才不为空
     */
    private IFunctionModel uniqueMethod;

    /**
     * 如果minCountForVarArgMethod不是Integer.MAX_VALUE，则表示存在带有varArgs参数的函数，此函数的参数个数为varArgCount
     */
    private int minCountForVarArgMethod = Integer.MAX_VALUE;

    private IFunctionModel varArgMethod;

    private boolean readonly;

    private static MethodModelCollection createEmpty() {
        MethodModelCollection mc = new MethodModelCollection();
        mc.freeze(true);
        return mc;
    }

    @Override
    public boolean frozen() {
        return !readonly;
    }

    @Override
    public void freeze(boolean cascade) {
        if (frozen())
            return;

        readonly = true;
        FreezeHelper.freezeItems(methods, true);
    }

    protected void checkReadonly() {
        if (readonly)
            throw new NopException(ERR_REFLECT_MODEL_IS_READONLY).param(ARG_MODEL, this);
    }

    public List<IFunctionModel> getMethods() {
        return methods;
    }

    public void setMethods(List<? extends IFunctionModel> methods) {
        for (IFunctionModel method : methods) {
            addMethod(method);
        }
    }

    /**
     * 按照从特殊到一般的顺序对方法进行排序 1. 参数个数少的排在前面 2. 公开方法排在前面 3. 参数类型更特殊的排在前面
     */
    public void sortMethods() {
        methods.sort(this::compareMethod);
    }

    int compareMethod(IFunctionModel m1, IFunctionModel m2) {
        int c1 = m1.getArgCount();
        int c2 = m2.getArgCount();
        if (c1 < c2)
            return -1;
        if (c1 > c2)
            return 1;

        boolean p1 = m1.isPublic();
        boolean p2 = m2.isPublic();
        if (p1 && !p2)
            return -1;
        if (!p1 && p2)
            return 1;

        // 当m1的所有参数类型都比m2的参数类型更特殊时，返回-1
        int cmp = 0;
        for (int i = 0; i < c1; i++) {
            Class t1 = m1.getArgs().get(i).getRawClass();
            Class t2 = m2.getArgs().get(i).getRawClass();
            if (t2.isAssignableFrom(t1)) {
                if (cmp > 0)
                    return 0;
                cmp = -1;
            } else if (t1.isAssignableFrom(t2)) {
                if (cmp < 0)
                    return 0;
                cmp = 1;
            }
        }
        return cmp;
    }

    public IFunctionModel getUniqueMethod() {
        return uniqueMethod;
    }

    public IFunctionModel getUniqueMethod(int argCount) {
        if (uniqueMethod != null) {
            if (minCountForVarArgMethod <= argCount)
                return uniqueMethod;
            return uniqueMethod.getArgCount() == argCount ? uniqueMethod : null;
        }

        IFunctionModel method = ArrayHelper.get(methodByArgCounts, argCount);
        if (method != null)
            return method;

        if (varArgMethod != null && minCountForVarArgMethod <= argCount)
            return varArgMethod;

        return null;
    }

    public IFunctionModel getExactMatchMethod(Class[] argTypes) {
        for (int i = 0, n = methods.size(); i < n; i++) {
            IFunctionModel method = methods.get(i);
            if (method.isExactlyMatch(argTypes))
                return method;
        }
        return null;
    }

    /**
     * 如果同样类型的方法已经存在，则直接替换，否则增加
     */
    public void mergeMethod(IFunctionModel mtd) {
        checkReadonly();

        Class[] argTypes = mtd.getArgRawTypes();
        for (int i = 0, n = methods.size(); i < n; i++) {
            IFunctionModel method = methods.get(i);
            if (method == mtd)
                return;

            if (method.isExactlyMatch(argTypes)) {
                methods.set(i, mtd);
                if (uniqueMethod == method) {
                    uniqueMethod = mtd;
                    if (method.isVarArgs()) {
                        minCountForVarArgMethod = method.getArgCount() - 1;
                    } else {
                        minCountForVarArgMethod = Integer.MAX_VALUE;
                    }
                }
                IFunctionModel um = ArrayHelper.get(methodByArgCounts, mtd.getArgCount());
                if (um == method) {
                    methodByArgCounts[mtd.getArgCount()] = mtd;
                }
                return;
            }
        }
        addMethod(mtd);
    }

    @Override
    public IFunctionModel getMethodForArgTypes(Class... argTypes) {
        for (int i = 0, n = methods.size(); i < n; i++) {
            IFunctionModel method = methods.get(i);
            if (method.isAllowArgTypes(argTypes))
                return method;
        }
        return null;
    }

    @Override
    public IFunctionModel getMethodForArgValues(Object... argValues) {
        for (int i = 0, n = methods.size(); i < n; i++) {
            IFunctionModel method = methods.get(i);
            if (method.isAllowArgValues(argValues))
                return method;
        }
        return null;
    }

    public void addMethod(IFunctionModel method) {
        checkReadonly();

        if (methods.isEmpty()) {
            uniqueMethod = method;
            if (method.isVarArgs()) {
                minCountForVarArgMethod = method.getArgCount() - 1;
                varArgMethod = method;
            }
            methods.add(method);
            return;
        }

        if (uniqueMethod != null) {
            if (uniqueMethod.isVarArgs()) {
                methodByArgCounts = ArrayHelper.assign(this.methodByArgCounts, uniqueMethod.getArgCount() - 1,
                        uniqueMethod);
            }
            methodByArgCounts = ArrayHelper.assign(this.methodByArgCounts, uniqueMethod.getArgCount(), uniqueMethod);
            uniqueMethod = null;
        }

        if (!hasMethodWithArgCount(method.getArgCount())) {
            this.methodByArgCounts = ArrayHelper.assign(this.methodByArgCounts, method.getArgCount(), method);
        } else {
            IFunctionModel oldMethod = ArrayHelper.get(this.methodByArgCounts, method.getArgCount());
            if (oldMethod != null) {
                this.methodByArgCounts[method.getArgCount()] = null;
            }
        }

        if (method.isVarArgs()) {
            if (minCountForVarArgMethod != Integer.MAX_VALUE) {
                this.varArgMethod = null;
                minCountForVarArgMethod = Math.min(minCountForVarArgMethod, method.getArgCount() - 1);
            } else {
                this.varArgMethod = method;
                minCountForVarArgMethod = method.getArgCount() - 1;
            }

            for (int i = minCountForVarArgMethod, n = methodByArgCounts.length; i < n; i++) {
                IFunctionModel fn = methodByArgCounts[i];
                if (fn != null && fn != method) {
                    methodByArgCounts[i] = null;
                }
            }
        }

        methods.add(method);
    }

    boolean hasMethodWithArgCount(int n) {
        if (minCountForVarArgMethod <= n)
            return false;

        for (IFunctionModel mtd : methods) {
            if (mtd.getArgCount() == n)
                return true;
        }
        return false;
    }
}