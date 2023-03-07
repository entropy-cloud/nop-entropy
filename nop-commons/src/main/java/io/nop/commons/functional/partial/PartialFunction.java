/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.functional.partial;

import java.util.function.Function;
import java.util.function.Predicate;

public class PartialFunction<A, B> implements IPartialFunction<A, B> {
    private final Predicate<A> filter;
    private final Function<A, B> function;

    public PartialFunction(Predicate<A> filter, Function<A, B> function) {
        this.filter = filter;
        this.function = function;
    }

    public Predicate<A> getFilter() {
        return filter;
    }

    public Function<A, B> getFunction() {
        return function;
    }

    @Override
    public boolean isDefinedAt(A a) {
        return filter.test(a);
    }

    @Override
    public B apply(A a) {
        return function.apply(a);
    }
}