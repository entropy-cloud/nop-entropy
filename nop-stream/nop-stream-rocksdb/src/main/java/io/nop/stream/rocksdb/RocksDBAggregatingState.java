/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.rocksdb;

import java.nio.ByteBuffer;

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.state.AggregatingState;
import io.nop.stream.core.common.state.AggregatingStateDescriptor;
import io.nop.stream.core.common.state.StateDescriptor;
import io.nop.stream.core.common.state.StateMigrationFunction;

import io.nop.stream.core.exceptions.StreamException;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;

class RocksDBAggregatingState<IN, ACC, OUT> extends AbstractRocksDBState
        implements AggregatingState<IN, OUT> {

    AggregatingStateDescriptor<IN, ACC, OUT> descriptor;

    /**
     * The DB round-trip type of the accumulator value. The window descriptor
     * path records {@code Object.class} (generic erasure at the
     * WindowedStreamImpl call-site); an Object-typed round-trip turns a
     * {@code long[]} accumulator into an ArrayList on read (the user function's
     * {@code add} then ClassCastExceptions). Resolve the real accumulator type
     * from the live function's {@code createAccumulator()} once at construction
     * (null-returning functions — e.g. the reduce wrapper — keep the recorded
     * type; JSON-native accumulators round-trip either way).
     */
    final Class<?> storageValueType;

    RocksDBAggregatingState(RocksDBKeyedStateBackend<?> backend, ColumnFamilyHandle cfHandle,
                            AggregatingStateDescriptor<IN, ACC, OUT> descriptor) {
        super(backend, cfHandle);
        this.descriptor = descriptor;
        this.storageValueType = resolveStorageValueType(descriptor);
    }

    /**
     * item 30 convergence (RK-5 family alignment): the failure of the live
     * accumulator-type inference is logged, never silent (previously only the
     * internal twin logged).
     */
    static Class<?> resolveStorageValueType(AggregatingStateDescriptor<?, ?, ?> descriptor) {
        Class<?> type = descriptor.getValueType();
        if (type == Object.class && descriptor.getAggregateFunction() != null) {
            try {
                Object accumulator = descriptor.getAggregateFunction().createAccumulator();
                if (accumulator != null) {
                    type = accumulator.getClass();
                }
            } catch (Exception e) {
                // Keep the recorded (generic) type; JSON-native accumulators
                // round-trip correctly either way — but the failure must be
                // observable (item 11 RK-5).
                org.slf4j.LoggerFactory.getLogger(RocksDBAggregatingState.class).warn(
                        "Failed to resolve storage value type from aggregate function {}; keeping {}",
                        descriptor.getAggregateFunction().getClass().getName(), type.getName(), e);
            }
        }
        return type;
    }

    /**
     * Storage key for the current access; {@link RocksDBInternalAggregatingState}
     * overrides with its namespaced key (fail-fast guard).
     */
    protected byte[] storageKey() {
        return backend.buildStorageKeyForCurrent();
    }

    @Override
    public StateDescriptor<?> getMigrationDescriptor() {
        return descriptor;
    }

    /**
     * Stage 33 accumulator-state migration surface. The RocksDB aggregating
     * state stores the opaque ACC (serialized as JSON). This method iterates
     * every entry, deserializes the ACC, passes it through {@code migrate},
     * and writes it back. Correctness of the migrated ACC is the user's
     * responsibility; the platform does not validate accumulator-migration
     * semantics.
     */
    @Override
    public void applyMigration(StateMigrationFunction<?, ?> migration) {
        applyValueMigration(backend, cfHandle, migration, storageValueType, "RocksDB AggregatingState");
    }

    @Override
    @SuppressWarnings("unchecked")
    public void replaceDescriptor(StateDescriptor<?> newDescriptor) {
        this.descriptor = (AggregatingStateDescriptor<IN, ACC, OUT>) newDescriptor;
    }

    @Override
    @SuppressWarnings("unchecked")
    public OUT get() throws Exception {
        byte[] key = storageKey();
        ByteBuffer keyBuf = ByteBuffer.wrap(key);
        if (ttl != null && ttl.isExpired(keyBuf)) {
            backend.getDb().delete(cfHandle, key);
            ttl.removeTimestamp(keyBuf);
            return null;
        }
        byte[] bytes = backend.getDb().get(cfHandle, key);
        if (ttl != null && bytes != null) {
            if (!ttl.hasTimestamp(keyBuf)) {
                ttl.grantFreshWindow(keyBuf);
            } else {
                ttl.recordRead(keyBuf);
            }
        }
        if (bytes == null) {
            return null;
        }
        ACC accumulator = (ACC) RocksDBValueSerDe.deserialize(bytes, storageValueType);
        return descriptor.getAggregateFunction().getResult(accumulator);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void add(IN value) throws Exception {
        byte[] key = storageKey();
        ByteBuffer keyBuf = ByteBuffer.wrap(key);
        AggregateFunction<IN, ACC, OUT> aggFn = descriptor.getAggregateFunction();
        if (ttl != null && ttl.isExpired(keyBuf)) {
            backend.getDb().delete(cfHandle, key);
            ttl.removeTimestamp(keyBuf);
        }
        ACC accumulator;
        byte[] existing = backend.getDb().get(cfHandle, key);
        if (existing != null) {
            accumulator = (ACC) RocksDBValueSerDe.deserialize(existing, storageValueType);
        } else {
            accumulator = aggFn.createAccumulator();
        }
        accumulator = aggFn.add(value, accumulator);
        backend.getDb().put(cfHandle, key, RocksDBValueSerDe.serialize(accumulator));
        if (ttl != null) {
            ttl.recordWrite(keyBuf);
        }
    }

    @Override
    public void clear() {
        byte[] key = storageKey();
        try {
            backend.getDb().delete(cfHandle, key);
            if (ttl != null) {
                ttl.removeTimestamp(ByteBuffer.wrap(key));
            }
        } catch (RocksDBException e) {
            throw new StreamException("Failed to clear AggregatingState", e);
        }
    }
}
