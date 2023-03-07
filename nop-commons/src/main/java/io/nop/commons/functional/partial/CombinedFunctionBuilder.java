/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.functional.partial;

import io.nop.api.core.beans.ApiRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

import static io.nop.api.core.util.Guard.notNull;

public class CombinedFunctionBuilder {
    private List<IPartialFunction<?, ?>> functions = new ArrayList<>();

    public static CombinedFunctionBuilder create() {
        return new CombinedFunctionBuilder();
    }

    public CombinedFunction build() {
        return new CombinedFunction(functions);
    }

    public <A, B> CombinedFunctionBuilder match(Class<A> clazz, Function<A, B> fn) {
        notNull(clazz, "class is null");
        return addFunction(a -> clazz.isInstance(a), fn);
    }

    public <A, B> CombinedFunctionBuilder match(Class<A> clazz, Predicate<A> filter, Function<A, B> fn) {
        notNull(clazz, "class is null");
        notNull(filter, "filter is null");

        return addFunction(a -> {
            return clazz.isInstance(a) && filter.test(a);
        }, fn);
    }

    public <A, B> CombinedFunctionBuilder matchRequest(Class<A> clazz, Function<ApiRequest<A>, B> fn) {
        notNull(clazz, "class is null");

        return addFunction(a -> {
            return a != null && clazz.isInstance(a.getData());
        }, fn);
    }

    public <A, B> CombinedFunctionBuilder matchEquals(A matched, Function<A, B> fn) {
        return addFunction(a -> Objects.equals(a, matched), fn);
    }

    public <A, B> CombinedFunctionBuilder matchEquals(A matched, Predicate<A> filter, Function<A, B> fn) {
        return addFunction(a -> Objects.equals(a, matched) && filter.test(a), fn);
    }

    public <A, B> CombinedFunctionBuilder matchAny(Function<A, B> fn) {
        notNull(fn, "function is null");
        return addFunction(a -> true, fn);
    }

    public <A, B> CombinedFunctionBuilder matchAny(Predicate<A> filter, Function<A, B> fn) {
        notNull(filter, "filter is null");
        return addFunction(filter, fn);
    }

    private <A, B> CombinedFunctionBuilder addFunction(Predicate<A> filter, Function<A, B> fn) {
        notNull(fn, "function is null");
        this.functions.add(new PartialFunction<A, B>(filter, fn));
        return this;
    }
}