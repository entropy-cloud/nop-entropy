/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.rocksdb;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.nop.core.lang.json.JsonTool;

import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.state.StateDescriptor;
import io.nop.stream.core.common.state.StateMigrationFunction;
import io.nop.stream.core.common.state.TtlContext;
import io.nop.stream.core.common.state.backend.MigratableKeyedState;

import io.nop.stream.core.exceptions.StreamException;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

class RocksDBMapState<UK, UV> implements MapState<UK, UV>, RocksDbTtlAware, MigratableKeyedState {

    private final RocksDBKeyedStateBackend<?> backend;
    final ColumnFamilyHandle cfHandle;
    MapStateDescriptor<UK, UV> descriptor;
    private TtlContext<ByteBuffer> ttl;

    RocksDBMapState(RocksDBKeyedStateBackend<?> backend, ColumnFamilyHandle cfHandle,
                    MapStateDescriptor<UK, UV> descriptor) {
        this.backend = backend;
        this.cfHandle = cfHandle;
        this.descriptor = descriptor;
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

    @Override
    public StateDescriptor<?> getMigrationDescriptor() {
        return descriptor;
    }

    /**
     * Stage 33: full-scan migration. Each column-family entry is one map value
     * (storage key = base composite key + map-key suffix). Iterate every entry,
     * deserialize the value as the old value type, pass through {@code migrate},
     * and write back under the same full key. Map keys are not migrated.
     */
    @Override
    @SuppressWarnings("unchecked")
    public void applyMigration(StateMigrationFunction<?, ?> migration) {
        StateMigrationFunction<Object, Object> fn = (StateMigrationFunction<Object, Object>) migration;
        List<byte[]> keys = new ArrayList<>();
        List<byte[]> values = new ArrayList<>();
        try (RocksIterator it = backend.getDb().newIterator(cfHandle)) {
            for (it.seekToFirst(); it.isValid(); it.next()) {
                keys.add(it.key());
                values.add(it.value());
            }
        }
        try {
            for (int i = 0; i < keys.size(); i++) {
                Object old = RocksDBValueSerDe.deserialize(values.get(i), descriptor.getValueType());
                if (old == null) {
                    continue;
                }
                Object migrated = fn.migrate(old);
                backend.getDb().put(cfHandle, keys.get(i), RocksDBValueSerDe.serialize(migrated));
            }
        } catch (RocksDBException e) {
            throw new StreamException("Failed to migrate RocksDB MapState", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void replaceDescriptor(StateDescriptor<?> newDescriptor) {
        this.descriptor = (MapStateDescriptor<UK, UV>) newDescriptor;
    }

    /**
     * Evict the whole map (all entries sharing the base composite key) if the TTL unit has
     * expired. Returns the base key bytes.
     */
    private byte[] evictMapIfExpired() {
        byte[] baseKey = backend.buildStorageKeyForCurrent();
        if (ttl != null && ttl.isExpired(ByteBuffer.wrap(baseKey))) {
            backend.deleteByPrefix(cfHandle, baseKey);
            ttl.removeTimestamp(ByteBuffer.wrap(baseKey));
        }
        return baseKey;
    }

    private byte[] buildFullKey(UK mapKey) {
        byte[] baseKey = backend.buildStorageKeyForCurrent();
        byte[] mapKeyBytes = mapKey != null
                ? JsonTool.serialize(mapKey, false).getBytes(StandardCharsets.UTF_8)
                : RocksDBKeyEncoder.EMPTY_BYTES;
        byte[] result = new byte[baseKey.length + 4 + mapKeyBytes.length];
        System.arraycopy(baseKey, 0, result, 0, baseKey.length);
        int off = baseKey.length;
        result[off] = (byte) ((mapKeyBytes.length >>> 24) & 0xFF);
        result[off + 1] = (byte) ((mapKeyBytes.length >>> 16) & 0xFF);
        result[off + 2] = (byte) ((mapKeyBytes.length >>> 8) & 0xFF);
        result[off + 3] = (byte) (mapKeyBytes.length & 0xFF);
        System.arraycopy(mapKeyBytes, 0, result, off + 4, mapKeyBytes.length);
        return result;
    }

    @SuppressWarnings("unchecked")
    private UK extractMapKey(byte[] fullKey, int baseLen) {
        int mapKeyLen = ((fullKey[baseLen] & 0xFF) << 24)
                | ((fullKey[baseLen + 1] & 0xFF) << 16)
                | ((fullKey[baseLen + 2] & 0xFF) << 8)
                | (fullKey[baseLen + 3] & 0xFF);
        byte[] mapKeyBytes = new byte[mapKeyLen];
        System.arraycopy(fullKey, baseLen + 4, mapKeyBytes, 0, mapKeyLen);
        if (mapKeyBytes.length == 0) {
            return null;
        }
        String json = new String(mapKeyBytes, StandardCharsets.UTF_8);
        Class<?> keyClass = descriptor.getKeyClass();
        if (keyClass != null && keyClass != Object.class) {
            return (UK) JsonTool.parseBeanFromText(json, keyClass);
        }
        return (UK) JsonTool.parseNonStrict(json);
    }

    @Override
    public UV get(UK key) {
        try {
            byte[] baseKey = backend.buildStorageKeyForCurrent();
            ByteBuffer baseBuf = ByteBuffer.wrap(baseKey);
            if (ttl != null && ttl.isExpired(baseBuf)) {
                backend.deleteByPrefix(cfHandle, baseKey);
                ttl.removeTimestamp(baseBuf);
                return null;
            }
            byte[] bytes = backend.getDb().get(cfHandle, buildFullKey(key));
            if (ttl != null && bytes != null) {
                if (!ttl.hasTimestamp(baseBuf)) {
                    ttl.grantFreshWindow(baseBuf);
                } else {
                    ttl.recordRead(baseBuf);
                }
            }
            return RocksDBValueSerDe.deserialize(bytes, descriptor.getValueType());
        } catch (Exception e) {
            throw new StreamException("Failed to read MapState", e);
        }
    }

    @Override
    public void put(UK key, UV value) {
        try {
            byte[] baseKey = evictMapIfExpired();
            backend.getDb().put(cfHandle, buildFullKey(key), RocksDBValueSerDe.serialize(value));
            if (ttl != null) {
                ttl.recordWrite(ByteBuffer.wrap(baseKey));
            }
        } catch (Exception e) {
            throw new StreamException("Failed to write MapState", e);
        }
    }

    @Override
    public void putAll(Map<UK, UV> map) {
        try {
            byte[] baseKey = evictMapIfExpired();
            for (Map.Entry<UK, UV> entry : map.entrySet()) {
                backend.getDb().put(cfHandle, buildFullKey(entry.getKey()),
                        RocksDBValueSerDe.serialize(entry.getValue()));
            }
            if (ttl != null) {
                ttl.recordWrite(ByteBuffer.wrap(baseKey));
            }
        } catch (Exception e) {
            throw new StreamException("Failed to write MapState", e);
        }
    }

    @Override
    public void remove(UK key) {
        try {
            evictMapIfExpired();
            backend.getDb().delete(cfHandle, buildFullKey(key));
        } catch (Exception e) {
            throw new StreamException("Failed to remove from MapState", e);
        }
    }

    @Override
    public boolean contains(UK key) {
        try {
            byte[] baseKey = backend.buildStorageKeyForCurrent();
            if (ttl != null && ttl.isExpired(ByteBuffer.wrap(baseKey))) {
                return false;
            }
            return backend.getDb().get(cfHandle, buildFullKey(key)) != null;
        } catch (Exception e) {
            throw new StreamException("Failed to check MapState", e);
        }
    }

    private Map<UK, UV> collectMap() {
        byte[] baseKey = backend.buildStorageKeyForCurrent();
        ByteBuffer baseBuf = ByteBuffer.wrap(baseKey);
        if (ttl != null && ttl.isExpired(baseBuf)) {
            backend.deleteByPrefix(cfHandle, baseKey);
            ttl.removeTimestamp(baseBuf);
            return new LinkedHashMap<>();
        }
        Map<UK, UV> result = new LinkedHashMap<>();
        try (RocksIterator it = backend.getDb().newIterator(cfHandle)) {
            it.seek(baseKey);
            while (it.isValid()) {
                byte[] fullKey = it.key();
                if (!startsWith(fullKey, baseKey)) {
                    break;
                }
                UK mapKey = extractMapKey(fullKey, baseKey.length);
                UV value = RocksDBValueSerDe.deserialize(it.value(), descriptor.getValueType());
                result.put(mapKey, value);
                it.next();
            }
        }
        if (ttl != null && !result.isEmpty()) {
            if (!ttl.hasTimestamp(baseBuf)) {
                ttl.grantFreshWindow(baseBuf);
            } else {
                ttl.recordRead(baseBuf);
            }
        }
        return result;
    }

    @Override
    public Iterable<Map.Entry<UK, UV>> entries() {
        return collectMap().entrySet();
    }

    @Override
    public Iterable<UK> keys() {
        return collectMap().keySet();
    }

    @Override
    public Iterable<UV> values() {
        return collectMap().values();
    }

    @Override
    public Iterator<Map.Entry<UK, UV>> iterator() {
        return collectMap().entrySet().iterator();
    }

    @Override
    public boolean isEmpty() {
        byte[] baseKey = backend.buildStorageKeyForCurrent();
        if (ttl != null && ttl.isExpired(ByteBuffer.wrap(baseKey))) {
            return true;
        }
        try (RocksIterator it = backend.getDb().newIterator(cfHandle)) {
            it.seek(baseKey);
            return !it.isValid() || !startsWith(it.key(), baseKey);
        }
    }

    @Override
    public void clear() {
        byte[] baseKey = backend.buildStorageKeyForCurrent();
        backend.deleteByPrefix(cfHandle, baseKey);
        if (ttl != null) {
            ttl.removeTimestamp(ByteBuffer.wrap(baseKey));
        }
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
