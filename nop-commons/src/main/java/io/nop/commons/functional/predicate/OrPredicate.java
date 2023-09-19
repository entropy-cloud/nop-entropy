/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.functional.predicate;

import jakarta.annotation.Nonnull;
import java.util.function.Predicate;

public class OrPredicate<T> implements Predicate<T> {
    private final Predicate<? super T> predicate;
    private final Predicate<? super T> other;

    public OrPredicate(Predicate<? super T> predicate, Predicate<? super T> other) {
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
        return predicate.test(t) || other.test(t);
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