/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dataset.record.impl;

import io.nop.commons.util.CollectionHelper;
import io.nop.dataset.record.IRecordInput;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class RecordInputImpls {
    public static <T> long defaultSkip(IRecordInput<T> input, long count) {
        long n = 0;
        while (n < count && input.hasNext()) {
            input.next();
            n++;
        }
        return n;
    }

    public static <T, R> List<R> defaultReadBatch(IRecordInput<T> input, int maxCount, Function<T, R> fn) {
        List<R> ret = new ArrayList<>();
        defaultReadBatch(input, maxCount, fn, ret::add);
        return ret;
    }

    public static <T, R> List<R> defaultReadBatch(IRecordInput<T> input, int maxCount,
                                                  Predicate<T> filter, Function<T, R> fn) {
        List<R> ret = new ArrayList<>();
        defaultReadBatch(input, maxCount, filter, fn, ret::add);
        return ret;
    }

    public static <T, R> void defaultReadBatch(IRecordInput<T> input, int maxCount, Function<T, R> fn, Consumer<R> ret) {
        int n = 0;
        while (input.hasNext()) {
            T record = input.next();
            ret.accept(fn.apply(record));
            n++;
            if (maxCount >= 0 && n >= maxCount) {
                break;
            }
        }
    }

    public static <T, R> void defaultReadBatch(IRecordInput<T> input, int maxCount,
                                               Predicate<T> filter,
                                               Function<T, R> fn,
                                               Consumer<R> ret) {
        int n = 0;
        while (input.hasNext()) {
            T record = input.next();
            if (!filter.test(record))
                continue;
            ret.accept(fn.apply(record));
            n++;
            if (maxCount >= 0 && n >= maxCount) {
                break;
            }
        }
    }

    public static <T, R> List<R> defaultReadAll(IRecordInput<T> input, Function<T, R> fn) {
        List<R> list = CollectionHelper.newList(input.getRemainingCount());
        while (input.hasNext()) {
            list.add(fn.apply(input.next()));
        }
        return list;
    }
}