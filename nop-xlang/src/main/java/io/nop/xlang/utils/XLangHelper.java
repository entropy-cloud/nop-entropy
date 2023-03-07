/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.utils;

import io.nop.commons.collections.iterator.IntRangeIterator;
import io.nop.commons.collections.iterator.LoopVarStatus;

import java.util.Iterator;

public class XLangHelper {
    public static <T> LoopVarStatus<T> itemsLoop(Iterable<T> items) {
        if (items == null)
            return null;
        return new LoopVarStatus<>(items.iterator(), false);
    }

    public static LoopVarStatus<Integer> stepLoop(int begin, int end, int step) {
        Iterator<Integer> it = new IntRangeIterator(begin, end, step);
        return new LoopVarStatus<>(it, true);
    }
}
