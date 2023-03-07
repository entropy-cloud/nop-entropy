/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.collections.bit;

public interface ILongBitSet extends IBitSet {
    void setLong(long bitIndex);

    void setLong(long fromIndex, long toIndex);

    boolean getLong(long bitIndex);

    long countLong(long fromIndex, long toIndex);

    void clearLong(long bitIndex);

    void clearLong(long fromIndex, long toIndex);

    long cardinalityLong();

    long sizeLong();

    ILongBitSet cloneInstance();
}