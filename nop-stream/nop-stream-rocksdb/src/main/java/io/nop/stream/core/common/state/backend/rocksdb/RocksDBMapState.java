/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.rocksdb;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import io.nop.core.lang.json.JsonTool;

import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.MapStateDescriptor;

import io.nop.stream.core.exceptions.StreamException;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksIterator;

class RocksDBMapState<UK, UV> implements MapState<UK, UV> {

    private final RocksDBKeyedStateBackend<?> backend;
    final ColumnFamilyHandle cfHandle;
    final MapStateDescriptor<UK, UV> descriptor;

    RocksDBMapState(RocksDBKeyedStateBackend<?> backend, ColumnFamilyHandle cfHandle,
                    MapStateDescriptor<UK, UV> descriptor) {
        this.backend = backend;
        this.cfHandle = cfHandle;
        this.descriptor = descriptor;
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
            byte[] bytes = backend.getDb().get(cfHandle, buildFullKey(key));
            return RocksDBValueSerDe.deserialize(bytes, descriptor.getValueType());
        } catch (Exception e) {
            throw new StreamException("Failed to read MapState", e);
        }
    }

    @Override
    public void put(UK key, UV value) {
        try {
            backend.getDb().put(cfHandle, buildFullKey(key), RocksDBValueSerDe.serialize(value));
        } catch (Exception e) {
            throw new StreamException("Failed to write MapState", e);
        }
    }

    @Override
    public void putAll(Map<UK, UV> map) {
        for (Map.Entry<UK, UV> entry : map.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void remove(UK key) {
        try {
            backend.getDb().delete(cfHandle, buildFullKey(key));
        } catch (Exception e) {
            throw new StreamException("Failed to remove from MapState", e);
        }
    }

    @Override
    public boolean contains(UK key) {
        try {
            return backend.getDb().get(cfHandle, buildFullKey(key)) != null;
        } catch (Exception e) {
            throw new StreamException("Failed to check MapState", e);
        }
    }

    private Map<UK, UV> collectMap() {
        byte[] baseKey = backend.buildStorageKeyForCurrent();
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
        try (RocksIterator it = backend.getDb().newIterator(cfHandle)) {
            it.seek(baseKey);
            return !it.isValid() || !startsWith(it.key(), baseKey);
        }
    }

    @Override
    public void clear() {
        byte[] baseKey = backend.buildStorageKeyForCurrent();
        java.util.List<byte[]> toDelete = new java.util.ArrayList<>();
        try (RocksIterator it = backend.getDb().newIterator(cfHandle)) {
            it.seek(baseKey);
            while (it.isValid()) {
                byte[] fullKey = it.key();
                if (!startsWith(fullKey, baseKey)) {
                    break;
                }
                toDelete.add(fullKey);
                it.next();
            }
        }
        try {
            for (byte[] key : toDelete) {
                backend.getDb().delete(cfHandle, key);
            }
        } catch (Exception e) {
            throw new StreamException("Failed to clear MapState", e);
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
