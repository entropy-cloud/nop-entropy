/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect;

import io.nop.api.core.util.IFreezable;

import java.util.List;

/**
 * 具有同样名称的method构成一个集合
 */
public interface IMethodModelCollection extends IFreezable {
    List<? extends IFunctionModel> getMethods();

    /**
     * 如果同名的参数只有一个，则返回这个函数，否则返回null
     *
     * @return
     */
    IFunctionModel getUniqueMethod();

    /**
     * 如果具有指定参数个数的函数只有一个，则返回该函数，否则返回null
     *
     * @param argCount 函数的参数个数
     */
    IFunctionModel getUniqueMethod(int argCount);

    IFunctionModel getExactMatchMethod(Class... argTypes);

    IFunctionModel getMethodForArgTypes(Class... argTypes);

    IFunctionModel getMethodForArgValues(Object... argValues);
}