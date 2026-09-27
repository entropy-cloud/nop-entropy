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

import io.nop.stream.core.common.state.ListState;
import io.nop.stream.core.common.state.backend.IKeyedStateBackend;

/**
 * List-state handle that re-points the backend at its fixed window namespace before every
 * operation, so reads and writes always land in the namespace the handle was created for.
 */
class NamespaceAwareListState<T> implements ListState<T> {
    private final String namespace;
    private final ListState<T> delegate;
    private final IKeyedStateBackend<?> backend;

    NamespaceAwareListState(String namespace, ListState<T> delegate, IKeyedStateBackend<?> backend) {
        this.namespace = namespace;
        this.delegate = delegate;
        this.backend = backend;
    }

    @Override
    public Iterable<T> get() throws java.io.IOException {
        backend.setCurrentNamespace(namespace);
        return delegate.get();
    }

    @Override
    public void add(T value) throws java.io.IOException {
        backend.setCurrentNamespace(namespace);
        delegate.add(value);
    }

    @Override
    public void update(Iterable<T> values) throws java.io.IOException {
        backend.setCurrentNamespace(namespace);
        delegate.update(values);
    }

    @Override
    public void addAll(Iterable<T> values) throws java.io.IOException {
        backend.setCurrentNamespace(namespace);
        delegate.addAll(values);
    }

    @Override
    public void clear() {
        backend.setCurrentNamespace(namespace);
        delegate.clear();
    }
}
