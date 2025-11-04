/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.functional.bijection;

import java.util.function.Function;

public class Bijections {
    static final IBijection<Object, Object> _identity = new IdentityBijection();

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <A> IBijection<A, A> identity() {
        return (IBijection) _identity;
    }

    /**
     * 将两个函数配对形成一个双射
     *
     * @param f1
     * @param f2
     * @return
     */
    public static <A, B> IBijection<A, B> pair(Function<A, B> f1, Function<B, A> f2) {
        return new PairBijection<>(f1, f2);
    }

    /**
     * 将一个双射反向
     *
     * @param bij
     * @return
     */
    public static <B, A> IBijection<B, A> invert(IBijection<A, B> bij) {
        if (bij instanceof InvertBijection) {
            return ((InvertBijection) bij).getOriginal();
        }
        return new InvertBijection<>(bij);
    }

    /**
     * 将两个双射组合成一个新的双射。正向映射时先调用b1, 再调用b2, 逆向映射时先调用b2再调用b1
     *
     * @param b1
     * @param b2
     * @return
     */
    public static <A, B, C> IBijection<A, C> compose(IBijection<A, B> b1, IBijection<B, C> b2) {
        return new ComposeBijection<>(b1, b2);
    }

    public static <T, R> Function<T, T> adapt(Function<R, R> fn, IBijection<T, R> b) {
        return new AdaptFunction<>(b, fn);
    }

    static class AdaptFunction<T, R> implements Function<T, T> {
        final IBijection<T, R> b;
        final Function<R, R> fn;

        public AdaptFunction(IBijection<T, R> b, Function<R, R> fn) {
            this.b = b;
            this.fn = fn;
        }

        @Override
        public T apply(T t) {
            R r = b.apply(t);
            r = fn.apply(r);
            return b.invert(r);
        }

    }

    static class PairBijection<A, B> implements IBijection<A, B> {
        final Function<A, B> f1;
        final Function<B, A> f2;

        public PairBijection(Function<A, B> f1, Function<B, A> f2) {
            this.f1 = f1;
            this.f2 = f2;
        }

        @Override
        public B apply(A a) {
            return f1.apply(a);
        }

        @Override
        public A invert(B b) {
            return f2.apply(b);
        }
    }

    static class IdentityBijection implements IBijection<Object, Object> {
        @Override
        public Object apply(Object a) {
            return a;
        }

        @Override
        public Object invert(Object b) {
            return b;
        }
    }

    static class InvertBijection<B, A> implements IBijection<B, A> {
        private final IBijection<A, B> bij;

        public InvertBijection(IBijection<A, B> bij) {
            this.bij = bij;
        }

        public IBijection<A, B> getOriginal() {
            return bij;
        }

        @Override
        public A apply(B b) {
            return bij.invert(b);
        }

        @Override
        public B invert(A a) {
            return bij.apply(a);
        }
    }

    static class ComposeBijection<A, B, C> implements IBijection<A, C> {
        private final IBijection<A, B> b1;
        private final IBijection<B, C> b2;

        public ComposeBijection(IBijection<A, B> b1, IBijection<B, C> b2) {
            this.b1 = b1;
            this.b2 = b2;
        }

        @Override
        public C apply(A a) {
            B b = b1.apply(a);
            return b2.apply(b);
        }

        @Override
        public A invert(C c) {
            B b = b2.invert(c);
            return b1.invert(b);
        }
    }
}