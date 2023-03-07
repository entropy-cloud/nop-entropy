/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.functional.lattice;

import io.nop.api.core.util.ICloneable;

public interface ILattice<T> extends ICloneable {
    T bot();

    T value();

    void merge(T e);

    void assign(T e);

    ILattice<T> cloneInstance();
}