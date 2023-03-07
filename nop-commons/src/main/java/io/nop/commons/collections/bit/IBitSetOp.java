/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.collections.bit;

import java.util.Collection;

public interface IBitSetOp {
    IBitSet and(IBitSet bs1, IBitSet bs2);

    IBitSet or(IBitSet bs1, IBitSet bs2);

    IBitSet xor(IBitSet bs1, IBitSet bs2);

    IBitSet andNot(IBitSet bs1, IBitSet bs2);

    IBitSet and(Collection<? extends IBitSet> bsList);

    IBitSet or(Collection<? extends IBitSet> bsList);
}