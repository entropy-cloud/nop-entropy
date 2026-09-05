/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.memory;

import java.io.IOException;

import io.nop.stream.core.common.state.AggregatingStateDescriptor;
import io.nop.stream.core.common.state.InternalAppendingState;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;

/**
 * item 21 D-2 convergence: the internal aggregating state is the public
 * {@link MemoryAggregatingState} with a namespaced storage key plus the raw
 * accumulator accessors; storage methods, TTL behavior and the whole-storage
 * migration are inherited from the single implementation. The IOException
 * wrappers preserve the S-5 contract: module-convention StreamExceptions
 * (with error codes) rethrow as-is, everything else wraps as IOException.
 */
class MemoryInternalAggregatingState<K, N, IN, ACC, OUT>
        extends MemoryAggregatingState<IN, ACC, OUT>
        implements InternalAppendingState<K, N, IN, ACC, OUT> {
    private static final long serialVersionUID = 1L;

    private transient N currentNamespace;

    MemoryInternalAggregatingState(MemoryKeyedStateBackend<?> backend,
            AggregatingStateDescriptor<IN, ACC, OUT> descriptor) {
        super(backend, descriptor);
    }

    @Override
    public void setCurrentNamespace(N namespace) {
        this.currentNamespace = namespace;
    }

    @Override
    public N getCurrentNamespace() {
        return currentNamespace;
    }

    @Override
    protected TypedNamespaceAndKey storageKey() {
        if (currentNamespace == null) {
            throw new StreamException(ERR_STREAM_STATE_ERROR)
                    .param(ARG_DETAIL,
                            "currentNamespace is null. Call setCurrentNamespace() before accessing state.");
        }
        return new TypedNamespaceAndKey(currentNamespace, backend.routeKey(backend.getCurrentKey()));
    }

    @Override
    public ACC getAccumulator() throws Exception {
        TypedNamespaceAndKey key = storageKey();
        if (ttl != null && ttl.readEviction(key, storage)) {
            return null;
        }
        ACC acc = storage.get(key);
        if (ttl != null && acc != null) {
            ttl.recordRead(key);
        }
        return acc;
    }

    @Override
    public void setAccumulator(ACC accumulator) throws Exception {
        TypedNamespaceAndKey key = storageKey();
        storage.put(key, accumulator);
        if (ttl != null) {
            ttl.recordWrite(key);
        }
    }

    @Override
    public OUT get() throws IOException {
        try {
            return super.get();
        } catch (StreamException e) {
            // S-5 (2026-09-01 core audit): module-convention exceptions already carry
            // error codes + params — rethrow as-is instead of erasing into IOException.
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to get aggregated state", e);
        }
    }

    @Override
    public void add(IN value) throws IOException {
        try {
            super.add(value);
        } catch (StreamException e) {
            // S-5 (2026-09-01 core audit): see get() — preserve error codes.
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to add to aggregated state", e);
        }
    }
}
