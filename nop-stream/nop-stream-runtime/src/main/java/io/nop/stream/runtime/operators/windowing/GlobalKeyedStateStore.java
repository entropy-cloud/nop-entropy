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
 * Keyed-state view scoped to the backend-wide default namespace, shared across all windows of a
 * key. Every accessor re-points the backend at the default namespace before delegating.
 */
class GlobalKeyedStateStore<K> implements KeyedStateStore {
    private final IKeyedStateBackend<K> backend;

    GlobalKeyedStateStore(IKeyedStateBackend<K> backend) {
        this.backend = backend;
    }

    private void setNamespace() {
        backend.setCurrentNamespace(IKeyedStateBackend.DEFAULT_NAMESPACE);
    }

    @Override
    public <T> ValueState<T> getState(ValueStateDescriptor<T> stateProperties) {
        setNamespace();
        return new NamespaceAwareValueState<>(IKeyedStateBackend.DEFAULT_NAMESPACE, backend.getState(stateProperties), backend);
    }

    @Override
    public <T> ListState<T> getListState(ListStateDescriptor<T> stateProperties) {
        setNamespace();
        return new NamespaceAwareListState<>(IKeyedStateBackend.DEFAULT_NAMESPACE, backend.getListState(stateProperties), backend);
    }

    @Override
    public <T> ReducingState<T> getReducingState(ReducingStateDescriptor<T> stateProperties) {
        setNamespace();
        return new NamespaceAwareReducingState<>(IKeyedStateBackend.DEFAULT_NAMESPACE, backend.getReducingState(stateProperties), backend);
    }

    @Override
    public <IN, ACC, OUT> AggregatingState<IN, OUT> getAggregatingState(
            AggregatingStateDescriptor<IN, ACC, OUT> stateProperties) {
        setNamespace();
        return new NamespaceAwareAggregatingState<>(IKeyedStateBackend.DEFAULT_NAMESPACE, backend.getAggregatingState(stateProperties), backend);
    }

    @Override
    public <UK, UV> MapState<UK, UV> getMapState(MapStateDescriptor<UK, UV> stateProperties) {
        setNamespace();
        return new NamespaceAwareMapState<>(IKeyedStateBackend.DEFAULT_NAMESPACE, backend.getMapState(stateProperties), backend);
    }
}
