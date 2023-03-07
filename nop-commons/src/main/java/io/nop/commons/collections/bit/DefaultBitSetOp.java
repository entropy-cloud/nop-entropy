/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.collections.bit;

import java.util.Collection;
import java.util.Iterator;

public class DefaultBitSetOp implements IBitSetOp {
    public static final DefaultBitSetOp INSTANCE = new DefaultBitSetOp();

    @Override
    public IBitSet and(IBitSet bs1, IBitSet bs2) {
        IBitSet ret = bs1.cloneInstance();
        ret.and(bs2);
        return ret;
    }

    @Override
    public IBitSet or(IBitSet bs1, IBitSet bs2) {
        IBitSet ret = bs1.cloneInstance();
        ret.or(bs2);
        return ret;
    }

    @Override
    public IBitSet xor(IBitSet bs1, IBitSet bs2) {
        IBitSet ret = bs1.cloneInstance();
        ret.xor(bs2);
        return ret;
    }

    @Override
    public IBitSet andNot(IBitSet bs1, IBitSet bs2) {
        IBitSet ret = bs1.cloneInstance();
        ret.andNot(bs2);
        return ret;
    }

    @Override
    public IBitSet and(Collection<? extends IBitSet> bsList) {
        if (bsList == null || bsList.isEmpty()) {
            return null;
        }

        Iterator<? extends IBitSet> it = bsList.iterator();
        IBitSet ret = it.next().cloneInstance();
        while (it.hasNext()) {
            ret.and(it.next());
        }
        return ret;
    }

    @Override
    public IBitSet or(Collection<? extends IBitSet> bsList) {
        if (bsList == null || bsList.isEmpty()) {
            return null;
        }
        Iterator<? extends IBitSet> it = bsList.iterator();
        IBitSet ret = it.next().cloneInstance();
        while (it.hasNext()) {
            ret.or(it.next());
        }
        return ret;
    }
}
