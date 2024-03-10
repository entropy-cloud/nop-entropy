/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.type;

import io.nop.api.core.exceptions.NopException;

/**
 * 仅仅记录类型名称。GenericType构成树形结构，通过RawTypeReference可以表达递归类型。
 *
 * <pre>{@code
 *   List<A extends List<A>>
 *     对应 RawType<TypeVariableBound extends RawTypeReference<TypeVariable>>
 * }</pre>
 */
public interface IRawTypeReference extends IGenericType {
    String getTypeName();

    /**
     * 当isResolved()为true时，这里返回rawType，否则抛出异常
     */
    IGenericType getResolvedType() throws NopException;
}