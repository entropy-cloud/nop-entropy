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

package io.nop.stream.cep.operator;

import io.nop.api.core.annotations.core.Internal;
import static com.google.common.base.Preconditions.checkNotNull;

import io.nop.stream.core.common.functions.RuntimeContext;
import io.nop.stream.core.common.state.KeyedStateStore;

/**
 * A wrapper class for the {@link RuntimeContext}.
 *
 * <p>This context wraps a RuntimeContext and optionally a {@link KeyedStateStore} to provide
 * state access for the CEP pattern process function and iterative condition function.
 */
@Internal
class CepRuntimeContext implements RuntimeContext {

    private final RuntimeContext runtimeContext;
    private final KeyedStateStore keyedStateStore;

    CepRuntimeContext(final RuntimeContext runtimeContext) {
        this.runtimeContext = checkNotNull(runtimeContext);
        this.keyedStateStore = null;
    }

    CepRuntimeContext(final RuntimeContext runtimeContext, final KeyedStateStore keyedStateStore) {
        this.runtimeContext = checkNotNull(runtimeContext);
        this.keyedStateStore = keyedStateStore;
    }

    public RuntimeContext getWrappedRuntimeContext() {
        return runtimeContext;
    }

    public KeyedStateStore getKeyedStateStore() {
        return keyedStateStore;
    }
}
