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

package io.nop.stream.runtime.operators.windowing;

import io.nop.stream.core.common.state.AggregatingState;
import io.nop.stream.core.common.state.AggregatingStateDescriptor;
import io.nop.stream.core.common.state.KeyedStateStore;
import io.nop.stream.core.common.state.ListState;
import io.nop.stream.core.common.state.ListStateDescriptor;
import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.state.ReducingState;
import io.nop.stream.core.common.state.ReducingStateDescriptor;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.IKeyedStateBackend;

/**
 * Keyed-state view scoped to a single window namespace. Every accessor re-points the backend at
 * the window namespace before delegating, so callers never see state belonging to another window.
 */
class PerWindowKeyedStateStore<K> implements KeyedStateStore {
    private final IKeyedStateBackend<K> backend;
    private final String namespace;

    PerWindowKeyedStateStore(IKeyedStateBackend<K> backend, String namespace) {
        this.backend = backend;
        this.namespace = namespace;
    }

    private void setNamespace() {
        backend.setCurrentNamespace(namespace);
    }

    @Override
    public <T> ValueState<T> getState(ValueStateDescriptor<T> stateProperties) {
        setNamespace();
        return new NamespaceAwareValueState<>(namespace, backend.getState(stateProperties), backend);
    }

    @Override
    public <T> ListState<T> getListState(ListStateDescriptor<T> stateProperties) {
        setNamespace();
        return new NamespaceAwareListState<>(namespace, backend.getListState(stateProperties), backend);
    }

    @Override
    public <T> ReducingState<T> getReducingState(ReducingStateDescriptor<T> stateProperties) {
        setNamespace();
        return new NamespaceAwareReducingState<>(namespace, backend.getReducingState(stateProperties), backend);
    }

    @Override
    public <IN, ACC, OUT> AggregatingState<IN, OUT> getAggregatingState(
            AggregatingStateDescriptor<IN, ACC, OUT> stateProperties) {
        setNamespace();
        return new NamespaceAwareAggregatingState<>(namespace, backend.getAggregatingState(stateProperties), backend);
    }

    @Override
    public <UK, UV> MapState<UK, UV> getMapState(MapStateDescriptor<UK, UV> stateProperties) {
        setNamespace();
        return new NamespaceAwareMapState<>(namespace, backend.getMapState(stateProperties), backend);
    }
}
