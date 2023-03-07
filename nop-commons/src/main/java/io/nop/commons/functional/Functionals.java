/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.functional;

import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

public class Functionals {
    private static final Supplier NULL_SUPPLIER = () -> null;
    private static final Callable NULL_CALLABLE = () -> null;

    private static final Function IDENTITY = x -> x;

    private static final Consumer IGNORE_CONSUMER = item -> {
    };

    private static final IntConsumer IGNORE_INT_CONSUMER = item -> {
    };

    private static final Runnable EMPTY_RUNNABLE = () -> {
    };

    private static final Function<Object, Void> TO_VOID = r -> null;

    public static <T> Function<T, Void> toVoid() {
        return (Function<T, Void>) TO_VOID;
    }

    public static Runnable emptyRunnable() {
        return EMPTY_RUNNABLE;
    }

    public static <T> Function<T, T> identity() {
        return IDENTITY;
    }

    public static <T> Supplier<T> nullSupplier() {
        return NULL_SUPPLIER;
    }

    public static <T> Callable<T> nullCallable() {
        return NULL_CALLABLE;
    }

    public static <T> Consumer<T> ignoreConsumer() {
        return IGNORE_CONSUMER;
    }

    public static IntConsumer ignoreIntConsumer() {
        return IGNORE_INT_CONSUMER;
    }

    public static <T> Callable<T> asCallable(Runnable task, T result) {
        return () -> {
            task.run();
            return result;
        };
    }
}