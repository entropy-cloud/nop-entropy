/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.type;

import jakarta.annotation.Nonnull;

public interface IWildcardType extends ITypeWithBound {
    /**
     * 对应 ? extends upperBound。如果没有明确声明，则UpperBound为Object类型
     */
    @Nonnull
    IGenericType getUpperBound();

    /**
     * 对应 ? super lowerBound。没有明确声明lower bound，则返回null
     */
    IGenericType getLowerBound();
}
