/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.memory;

import java.io.Serializable;
import java.util.Objects;

class TypedNamespaceAndKey implements Serializable {
    private static final long serialVersionUID = 1L;

    final Object namespace;
    final Object key;

    TypedNamespaceAndKey(Object namespace, Object key) {
        this.namespace = namespace;
        this.key = key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TypedNamespaceAndKey that = (TypedNamespaceAndKey) o;
        if (namespace != null && that.namespace != null
                && !namespace.getClass().equals(that.namespace.getClass())) {
            return false;
        }
        if (key != null && that.key != null
                && !key.getClass().equals(that.key.getClass())) {
            return false;
        }
        return Objects.equals(namespace, that.namespace) &&
                Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        int nsHash = namespace != null ? Objects.hash(namespace.getClass(), namespace) : 0;
        int keyHash = key != null ? Objects.hash(key.getClass(), key) : 0;
        return 31 * nsHash + keyHash;
    }

    @Override
    public String toString() {
        return "TypedNamespaceAndKey{" +
                "namespace=" + namespace +
                ", key=" + key +
                '}';
    }
}
