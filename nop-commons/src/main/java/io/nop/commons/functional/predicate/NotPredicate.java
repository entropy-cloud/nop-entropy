/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.functional.predicate;

import javax.annotation.Nonnull;
import java.util.function.Predicate;

public class NotPredicate<T> implements Predicate<T> {
    private final Predicate<T> predicate;

    public NotPredicate(Predicate<T> predicate) {
        this.predicate = predicate;
    }

    public Predicate<T> getPredicate() {
        return predicate;
    }

    @Override
    public boolean test(T t) {
        return !predicate.test(t);
    }

    @Nonnull
    @Override
    public Predicate<T> and(@Nonnull Predicate<? super T> other) {
        return new AndPredicate<>(this, other);
    }

    @Nonnull
    @Override
    public Predicate<T> negate() {
        return predicate;
    }

    @Nonnull
    @Override
    public Predicate<T> or(@Nonnull Predicate<? super T> other) {
        return new OrPredicate<>(this, other);
    }
}