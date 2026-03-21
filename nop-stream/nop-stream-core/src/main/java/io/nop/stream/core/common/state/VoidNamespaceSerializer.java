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

package io.nop.stream.core.common.state;

import io.nop.stream.core.common.typeutils.TypeSerializer;

/**
 * Serializer for {@link VoidNamespace}.
 * 
 * <p>Since VoidNamespace is a singleton, this serializer is stateless and thread-safe.
 */
public final class VoidNamespaceSerializer implements TypeSerializer<VoidNamespace> {

    private static final long serialVersionUID = 1L;

    public static final VoidNamespaceSerializer INSTANCE = new VoidNamespaceSerializer();

    @Override
    public boolean isImmutableType() {
        return true;
    }

    @Override
    public TypeSerializer<VoidNamespace> duplicate() {
        return this;
    }

    @Override
    public VoidNamespace createInstance() {
        return VoidNamespace.INSTANCE;
    }

    @Override
    public VoidNamespace copy(VoidNamespace from) {
        return VoidNamespace.INSTANCE;
    }

    @Override
    public VoidNamespace copy(VoidNamespace from, VoidNamespace reuse) {
        return VoidNamespace.INSTANCE;
    }

    @Override
    public int getLength() {
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof VoidNamespaceSerializer;
    }

    @Override
    public int hashCode() {
        return VoidNamespaceSerializer.class.hashCode();
    }
}
