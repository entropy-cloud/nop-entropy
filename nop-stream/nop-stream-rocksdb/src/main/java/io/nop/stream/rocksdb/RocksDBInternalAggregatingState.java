/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.rocksdb;

import java.io.IOException;
import java.nio.ByteBuffer;

import io.nop.stream.core.common.state.AggregatingStateDescriptor;
import io.nop.stream.core.common.state.InternalAppendingState;

import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;

/**
 * item 30 convergence: the internal aggregating state is the public
 * {@link RocksDBAggregatingState} with a namespaced storage key plus the raw
 * accumulator accessors; storage methods, TTL behavior and the whole-value
 * migration are inherited from the single implementation (the migration now
 * uses the class's own resolved storage type — the former twin drifted to the
 * raw descriptor type, inconsistent with its own read path).
 */
class RocksDBInternalAggregatingState<K, N, IN, ACC, OUT>
        extends RocksDBAggregatingState<IN, ACC, OUT>
        implements InternalAppendingState<K, N, IN, ACC, OUT> {

    private transient N currentNamespace;

    RocksDBInternalAggregatingState(RocksDBKeyedStateBackend<K> backend, ColumnFamilyHandle cfHandle,
                                    AggregatingStateDescriptor<IN, ACC, OUT> descriptor) {
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

    @Override
    @SuppressWarnings("unchecked")
    public ACC getAccumulator() throws Exception {
        byte[] key = storageKey();
        ByteBuffer keyBuf = ByteBuffer.wrap(key);
        if (ttlContext() != null && ttlContext().isExpired(keyBuf)) {
            backend.getDb().delete(cfHandle, key);
            ttlContext().removeTimestamp(keyBuf);
            return null;
        }
        byte[] bytes = backend.getDb().get(cfHandle, key);
        if (ttlContext() != null && bytes != null) {
            if (!ttlContext().hasTimestamp(keyBuf)) {
                ttlContext().grantFreshWindow(keyBuf);
            } else {
                ttlContext().recordRead(keyBuf);
            }
        }
        return (ACC) RocksDBValueSerDe.deserialize(bytes, descriptor.getValueType());
    }

    @Override
    public void setAccumulator(ACC accumulator) throws Exception {
        byte[] key = storageKey();
        backend.getDb().put(cfHandle, key, RocksDBValueSerDe.serialize(accumulator));
        if (ttlContext() != null) {
            ttlContext().recordWrite(ByteBuffer.wrap(key));
        }
    }

    @Override
    public OUT get() throws IOException {
        try {
            return super.get();
        } catch (Exception e) {
            throw new IOException("Failed to get aggregated state", e);
        }
    }

    @Override
    public void add(IN value) throws IOException {
        try {
            super.add(value);
        } catch (RocksDBException e) {
            throw new IOException("Failed to add to InternalAggregatingState", e);
        } catch (IOException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            // unreachable in practice: the only checked exception on this path
            // is RocksDBException (handled above)
            throw new IOException("Failed to add to InternalAggregatingState", e);
        }
    }
}
