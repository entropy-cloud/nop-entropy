/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.rocksdb;

import java.nio.ByteBuffer;

import io.nop.stream.core.common.state.StateMigrationFunction;
import io.nop.stream.core.common.state.TtlContext;
import io.nop.stream.core.common.state.backend.MigratableKeyedState;

import io.nop.stream.core.exceptions.StreamException;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksIterator;

/**
 * item 30 convergence: shared base of every RocksDB keyed-state class. Holds
 * the backend reference, the column-family handle and the TTL sidecar slot,
 * and carries the single whole-value migration implementation
 * {@link #applyValueMigration} (previously verbatim in five classes). The
 * list-element migration lives in {@link RocksDBListState}; the map migration
 * stays in {@link RocksDBMapState} (inner-map shape).
 */
abstract class AbstractRocksDBState implements RocksDbTtlAware, MigratableKeyedState {

    final RocksDBKeyedStateBackend<?> backend;
    final ColumnFamilyHandle cfHandle;

    TtlContext<ByteBuffer> ttl;

    AbstractRocksDBState(RocksDBKeyedStateBackend<?> backend, ColumnFamilyHandle cfHandle) {
        this.backend = backend;
        this.cfHandle = cfHandle;
    }

    @Override
    public void bindTtl(TtlContext<ByteBuffer> ctx) {
        this.ttl = ctx;
    }

    @Override
    public TtlContext<ByteBuffer> ttlContext() {
        return ttl;
    }

    @Override
    public ColumnFamilyHandle cfHandle() {
        return cfHandle;
    }

    /**
     * Single point for the whole-value migration shape: stream the state's
     * column family through {@link #applyMigrationStreaming} with a per-row
     * value migrator (deserialize as {@code valueType} &rarr; migrate &rarr;
     * serialize). The column-family key encoding is schema-agnostic, so keys
     * are preserved. Correctness of the migrated object is the user's
     * responsibility (Stage 33 accumulator-migration caveat).
     */
    @SuppressWarnings("unchecked")
    static void applyValueMigration(RocksDBKeyedStateBackend<?> backend, ColumnFamilyHandle cfHandle,
                                    StateMigrationFunction<?, ?> migration, Class<?> valueType, String stateLabel) {
        StateMigrationFunction<Object, Object> fn = (StateMigrationFunction<Object, Object>) migration;
        applyMigrationStreaming(backend, cfHandle, stateLabel, storedValue -> {
            Object old = RocksDBValueSerDe.deserialize(storedValue, valueType);
            if (old == null) {
                return null;
            }
            return RocksDBValueSerDe.serialize(fn.migrate(old));
        });
    }

    /**
     * Per-row migration callback for {@link #applyMigrationStreaming}:
     * deserialize the stored bytes, migrate, serialize back to the new
     * schema's bytes. Returning {@code null} drops the entry (no write-back —
     * the pre-fix "deserialized to null &rarr; skip" semantics).
     */
    interface RowMigrator {
        byte[] migrateRow(byte[] storedValue) throws Exception;
    }

    /**
     * CRA 8.43 R4-03 W-05 (MA-14=C5-004, merged C5-008/C5-009): single-pass
     * streaming migration shared by every RocksDB keyed-state class. Iterates
     * the column family ONCE and processes each row in isolation —
     * read &rarr; {@code rowMigrator.migrateRow} &rarr; write back under the
     * same key — with NO cross-row collection: the only heap residency at any
     * instant is the current row. The RocksIterator holds an implicit snapshot
     * taken at creation, so writing back to the same column family during the
     * iteration cannot corrupt or shift the iteration view (verified audit
     * fact, C5-004). Restore-time migration of a large column family no longer
     * materializes the whole CF as keys+values lists (2x raw bytes for value
     * shapes, 3-5x for deserialized list object graphs) — the OOM-the-restore
     * face is closed. An entry whose {@code migrateRow} returns {@code null}
     * is dropped; unchecked exceptions from the migrator propagate unchanged
     * (pre-fix semantics), checked ones are wrapped typed with the state
     * label.
     */
    static void applyMigrationStreaming(RocksDBKeyedStateBackend<?> backend, ColumnFamilyHandle cfHandle,
                                        String stateLabel, RowMigrator rowMigrator) {
        try (RocksIterator it = backend.getDb().newIterator(cfHandle)) {
            for (it.seekToFirst(); it.isValid(); it.next()) {
                byte[] key = it.key();
                byte[] migrated = rowMigrator.migrateRow(it.value());
                if (migrated != null) {
                    backend.getDb().put(cfHandle, key, migrated);
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new StreamException("Failed to migrate " + stateLabel, e);
        }
    }
}
