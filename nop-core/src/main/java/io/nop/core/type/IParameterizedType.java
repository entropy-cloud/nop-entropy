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
 * {@code
 * List<String>对应ParameterizedType<PredefinedType>
 * Map<String,MyObject<String>>应ParameterizedType<PredefinedType,ParameterizedType<PredefinedType>>
 * }
 */
public interface IParameterizedType extends IGenericType {
    IGenericType getRawType();

    List<IGenericType> getTypeParameters();
}
