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

import java.util.Map;

import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.backend.IKeyedStateBackend;

/**
 * Map-state handle that re-points the backend at its fixed window namespace before every
 * operation, so reads and writes always land in the namespace the handle was created for.
 */
class NamespaceAwareMapState<UK, UV> implements MapState<UK, UV> {
    private final String namespace;
    private final MapState<UK, UV> delegate;
    private final IKeyedStateBackend<?> backend;

    NamespaceAwareMapState(String namespace, MapState<UK, UV> delegate, IKeyedStateBackend<?> backend) {
        this.namespace = namespace;
        this.delegate = delegate;
        this.backend = backend;
    }

    @Override
    public UV get(UK key) {
        backend.setCurrentNamespace(namespace);
        return delegate.get(key);
    }

    @Override
    public void put(UK key, UV value) {
        backend.setCurrentNamespace(namespace);
        delegate.put(key, value);
    }

    @Override
    public void putAll(Map<UK, UV> map) {
        backend.setCurrentNamespace(namespace);
        delegate.putAll(map);
    }

    @Override
    public void remove(UK key) {
        backend.setCurrentNamespace(namespace);
        delegate.remove(key);
    }

    @Override
    public boolean contains(UK key) {
        backend.setCurrentNamespace(namespace);
        return delegate.contains(key);
    }

    @Override
    public Iterable<Map.Entry<UK, UV>> entries() {
        backend.setCurrentNamespace(namespace);
        return delegate.entries();
    }

    @Override
    public Iterable<UK> keys() {
        backend.setCurrentNamespace(namespace);
        return delegate.keys();
    }

    @Override
    public Iterable<UV> values() {
        backend.setCurrentNamespace(namespace);
        return delegate.values();
    }

    @Override
    public java.util.Iterator<Map.Entry<UK, UV>> iterator() {
        backend.setCurrentNamespace(namespace);
        return delegate.iterator();
    }

    @Override
    public boolean isEmpty() {
        backend.setCurrentNamespace(namespace);
        return delegate.isEmpty();
    }

    @Override
    public void clear() {
        backend.setCurrentNamespace(namespace);
        delegate.clear();
    }
}
