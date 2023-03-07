/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.util.objects;

import io.nop.api.core.annotations.data.ImmutableBean;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

@ImmutableBean
public class Pair<K, V> implements Map.Entry<K, V>, Serializable {

    private static final long serialVersionUID = 7241454331958581671L;

    private final K left;
    private final V right;

    protected Pair(K left, V right) {
        this.left = left;
        this.right = right;
    }

    public static <K, V> Pair<K, V> of(K left, V right) {
        return new Pair<>(left, right);
    }

    public static <K, V> Pair<K, V> pair(K left, V right) {
        return new Pair<>(left, right);
    }

    @Override
    public K getKey() {
        return left;
    }

    @Override
    public V getValue() {
        return right;
    }

    public K getLeft() {
        return left;
    }

    public V getRight() {
        return right;
    }

    public K getFirst() {
        return left;
    }

    public V getSecond() {
        return right;
    }

    @Override
    public V setValue(V value) {
        throw new UnsupportedOperationException();
    }

    public int hashCode() {
        return (left == null ? 0 : left.hashCode()) * 37 + (right == null ? 0 : right.hashCode());
    }

    public boolean equals(Object o) {
        if (this == o)
            return true;

        if (!(o instanceof Pair))
            return false;
        Pair<?, ?> pair = (Pair<?, ?>) o;
        return Objects.equals(left, pair.getLeft()) && Objects.equals(right, pair.getRight());
    }

    public String toString() {
        return "[" + left + "," + right + "]";
    }
}