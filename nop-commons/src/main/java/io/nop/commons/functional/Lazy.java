/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nop.commons.functional;

import java.util.function.Function;
import java.util.function.Supplier;

// 代码来源： https://mp.weixin.qq.com/s/e-9hrjWK513VJqqyeGLxrQ

/**
 * 为了方便与标准的 Java 函数式接口交互，Lazy 也实现了 Supplier
 */
public class Lazy<T> implements Supplier<T> {

    protected final Supplier<? extends T> supplier;

    // 利用 value 属性缓存 supplier 计算后的值
    protected T value;

    private Lazy(Supplier<? extends T> supplier) {
        this.supplier = supplier;
    }

    public static <T> Lazy<T> of(Supplier<? extends T> supplier) {
        return new Lazy<>(supplier);
    }

    public static <T> Lazy<T> valueOrError(Supplier<? extends T> supplier) {
        return new LazyValueOrError<>(supplier);
    }

    public T get() {
        if (value == null) {
            T newValue = supplier.get();

            if (newValue == null) {
                throw new IllegalStateException("Lazy value can not be null!");
            }

            value = newValue;
        }

        return value;
    }

    public <S> Lazy<S> map(Function<? super T, ? extends S> function) {
        return Lazy.of(() -> function.apply(get()));
    }

    public <S> Lazy<S> flatMap(Function<? super T, Lazy<? extends S>> function) {
        return Lazy.of(() -> function.apply(get()).get());
    }

    static class LazyValueOrError<T> extends Lazy<T> {
        private RuntimeException exception;

        public LazyValueOrError(Supplier<? extends T> supplier) {
            super(supplier);
        }

        @Override
        public T get() {
            if (exception != null)
                throw exception;

            try {
                return super.get();
            } catch (RuntimeException e) {
                this.exception = e;
                throw e;
            }
        }
    }
}