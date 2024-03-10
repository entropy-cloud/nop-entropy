/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.functional.predicate;

import jakarta.annotation.Nonnull;
import java.util.function.Predicate;

/**
 * 结构化的Predicate, 可以反向分析
 *
 * @param <T>
 */
public class AndPredicate<T> implements Predicate<T> {
    private final Predicate<? super T> predicate;
    private final Predicate<? super T> other;

    public AndPredicate(Predicate<? super T> predicate, Predicate<? super T> other) {
        this.predicate = predicate;
        this.other = other;
    }

    public Predicate<? super T> getPredicate() {
        return predicate;
    }

    public Predicate<? super T> getOther() {
        return other;
    }

    @Override
    public boolean test(T t) {
        return predicate.test(t) && other.test(t);
    }

    @Nonnull
    @Override
    public Predicate<T> and(@Nonnull Predicate<? super T> other) {
        return new AndPredicate<>(this, other);
    }

    @Nonnull
    @Override
    public Predicate<T> negate() {
        return new NotPredicate<>(this);
    }

    @Nonnull
    @Override
    public Predicate<T> or(@Nonnull Predicate<? super T> other) {
        return new OrPredicate<>(this, other);
    }
}