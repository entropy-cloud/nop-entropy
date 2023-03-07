/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.functional.bijection;

import java.util.function.Function;

/**
 * 数学上的双向映射
 *
 * @author canonical_entropy@163.com
 */
public interface IBijection<A, B> extends Function<A, B> {
    B apply(A a);

    A invert(B b);
}