/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.eval.functions;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

public interface PredicateEx<T, U> extends Predicate<T>, BiPredicate<T, U> {
    default boolean test(T t) {
        return test(t, null);
    }

    default PredicateEx<T, U> negate() {
        return new NegatePredicate<>(this);
    }

    class NegatePredicate<T, U> implements PredicateEx<T, U> {
        private final PredicateEx<T, U> predicate;

        public NegatePredicate(PredicateEx<T, U> predicate) {
            this.predicate = predicate;
        }

        @Override
        public PredicateEx<T, U> negate() {
            return predicate;
        }

        @Override
        public boolean test(T t, U u) {
            return !predicate.test(t, u);
        }

        @Override
        public boolean test(T t) {
            return predicate.test(t);
        }
    }
}