/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.memory;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;

class MemoryValueState<T> implements ValueState<T>, Serializable {
    private static final long serialVersionUID = 1L;

    MemoryKeyedStateBackend<?> backend;
    final ValueStateDescriptor<T> descriptor;
    final Map<TypedNamespaceAndKey, T> storage = new HashMap<>();

    MemoryValueState(MemoryKeyedStateBackend<?> backend, ValueStateDescriptor<T> descriptor) {
        this.backend = backend;
        this.descriptor = descriptor;
    }

    void rebind(MemoryKeyedStateBackend<?> newBackend) {
        this.backend = newBackend;
    }

    @Override
    public T value() throws IOException {
        T result = storage.get(backend.getTypedNamespaceAndKey());
        return result != null ? result : descriptor.getDefaultValue();
    }

    @Override
    public void update(T value) throws IOException {
        if (value == null) {
            clear();
        } else {
            storage.put(backend.getTypedNamespaceAndKey(), value);
        }
    }

    @Override
    public void clear() {
        storage.remove(backend.getTypedNamespaceAndKey());
    }
}
