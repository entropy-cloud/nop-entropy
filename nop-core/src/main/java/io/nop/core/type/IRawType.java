/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.type;

import java.util.List;

/**
 * 对应于Java Class对象
 */
public interface IRawType extends IGenericType {

    Class<?> getRawClass();

    /**
     * 返回List<TypeVariableBound>
     */
    List<IGenericType> getTypeParameters();

    IGenericType getSuperType();

    List<IGenericType> getInterfaces();
}