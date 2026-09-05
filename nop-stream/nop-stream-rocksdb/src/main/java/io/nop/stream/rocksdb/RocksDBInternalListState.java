/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.rocksdb;

import io.nop.stream.core.common.state.InternalListState;
import io.nop.stream.core.common.state.ListStateDescriptor;

import io.nop.stream.core.exceptions.StreamException;

import org.rocksdb.ColumnFamilyHandle;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;

/**
 * item 30 convergence: the internal list state is the public
 * {@link RocksDBListState} with a namespaced storage key — every storage
 * method, TTL behavior and the list-element migration are inherited from the
 * single implementation.
 */
class RocksDBInternalListState<K, N, T> extends RocksDBListState<T>
        implements InternalListState<K, N, T> {

    private transient N currentNamespace;

    RocksDBInternalListState(RocksDBKeyedStateBackend<K> backend, ColumnFamilyHandle cfHandle,
                             ListStateDescriptor<T> descriptor) {
        super(backend, cfHandle, descriptor);
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
    protected byte[] storageKey() {
        if (currentNamespace == null) {
            throw new StreamException(ERR_STREAM_STATE_ERROR)
                    .param(ARG_DETAIL, "currentNamespace is null. Call setCurrentNamespace() before accessing state.");
        }
        return backend.buildStorageKey(currentNamespace, backend.getCurrentKey());
    }
}
