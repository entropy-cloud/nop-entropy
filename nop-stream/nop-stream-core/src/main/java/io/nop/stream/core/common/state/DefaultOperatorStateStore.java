/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state;

import io.nop.stream.core.common.state.backend.IOperatorStateBackend;

import java.util.HashMap;
import java.util.Map;

public class DefaultOperatorStateStore implements IOperatorStateStore {

    private final IOperatorStateBackend stateBackend;
    private final Map<String, MemoryArrayListState<Object>> managedStates = new HashMap<>();

    public DefaultOperatorStateStore(IOperatorStateBackend stateBackend) {
        this.stateBackend = stateBackend;
    }

    public IOperatorStateBackend getStateBackend() {
        return stateBackend;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> ListState<T> getListState(ListStateDescriptor<T> descriptor) {
        String name = descriptor.getName();
        MemoryArrayListState<Object> existing = managedStates.get(name);
        if (existing == null) {
            MemoryArrayListState<T> state = new MemoryArrayListState<>(descriptor, stateBackend);
            managedStates.put(name, (MemoryArrayListState<Object>) state);
            return state;
        }
        return (ListState<T>) existing;
    }
}
