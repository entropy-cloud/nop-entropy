/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpkg;

import io.nop.core.reflect.IFunctionModel;

import java.util.Map;

public interface IPackageDefinition {
    Map<String, ? extends IConstantDefinition> getConstants();

    default IConstantDefinition getConstant(String name) {
        return getConstants().get(name);
    }

    Map<String, IFunctionModel> getFunctions();

    default IFunctionModel getFunction(String name) {
        return getFunctions().get(name);
    }
}
