/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.graph;

import java.util.Objects;

public class DefaultEdge<V> implements IEdge<V> {
    private final V source;
    private final V target;

    private IEdge<V> _reverse;

    public String toString() {
        return source + "->" + target;
    }

    private class ReversedEdge implements IEdge<V> {
        @Override
        public V getSource() {
            return target;
        }

        @Override
        public V getTarget() {
            return source;
        }

        @Override
        public IEdge<V> reverse() {
            return DefaultEdge.this;
        }

        public String toString() {
            return getSource() + "->" + getTarget();
        }
    }

    public DefaultEdge(V source, V target) {
        this.source = Objects.requireNonNull(source, "null edge source");
        this.target = Objects.requireNonNull(target, "null edge target");
    }

    public static <V> DefaultEdge<V> of(V source, V target) {
        return new DefaultEdge<>(source, target);
    }

    public IEdge<V> cloneInstance() {
        return this;
    }

    public IEdge<V> reverse() {
        if (_reverse == null)
            _reverse = new ReversedEdge();
        return _reverse;
    }

    @Override
    public V getSource() {
        return source;
    }

    @Override
    public V getTarget() {
        return target;
    }

    @Override
    public int hashCode() {
        return source.hashCode() * 31 + target.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof DefaultEdge && ((DefaultEdge) obj).source.equals(source)
                && ((DefaultEdge) obj).target.equals(target);
    }

    public static <V> IEdgeFactory<V, DefaultEdge<V>> factory() {
        return DefaultEdge::new;
    }
}