/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dataset.record.impl;

import io.nop.dataset.record.IRecordInput;
import io.nop.commons.util.CollectionHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class RecordInputImpls {
    public static <T> long defaultSkip(IRecordInput<T> input, long count) {
        long n = 0;
        while (n < count && input.hasNext()) {
            input.next();
            n++;
        }
        return n;
    }

    public static <T> List<T> defaultReadBatch(IRecordInput<T> input, int maxCount) {
        List<T> ret = new ArrayList<>();
        defaultReadBatch(input, maxCount, ret::add);
        return ret;
    }

    public static <T> void defaultReadBatch(IRecordInput<T> input, int maxCount, Consumer<T> ret) {
        int n = 0;
        while (input.hasNext()) {
            T record = input.next();
            ret.accept(record);
            n++;
            if (maxCount >= 0 && n >= maxCount) {
                break;
            }
        }
    }

    public static <T> List<T> defaultReadAll(IRecordInput<T> input) {
        List<T> list = CollectionHelper.newList(input.getRemainingCount());
        while (input.hasNext()) {
            list.add(input.next());
        }
        return list;
    }
}