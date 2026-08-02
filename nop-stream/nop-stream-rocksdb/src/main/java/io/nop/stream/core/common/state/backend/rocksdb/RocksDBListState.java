/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.rocksdb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.nop.stream.core.common.state.ListState;
import io.nop.stream.core.common.state.ListStateDescriptor;

import io.nop.stream.core.exceptions.StreamException;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;

class RocksDBListState<T> implements ListState<T> {

    private final RocksDBKeyedStateBackend<?> backend;
    final ColumnFamilyHandle cfHandle;
    final ListStateDescriptor<T> descriptor;

    RocksDBListState(RocksDBKeyedStateBackend<?> backend, ColumnFamilyHandle cfHandle,
                     ListStateDescriptor<T> descriptor) {
        this.backend = backend;
        this.cfHandle = cfHandle;
        this.descriptor = descriptor;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Iterable<T> get() throws IOException {
        byte[] key = backend.buildStorageKeyForCurrent();
        try {
            byte[] bytes = backend.getDb().get(cfHandle, key);
            if (bytes == null) {
                return Collections.emptyList();
            }
            List<T> result = (List<T>) RocksDBValueSerDe.deserializeList(bytes, descriptor.getValueType());
            return result;
        } catch (Exception e) {
            throw new IOException("Failed to read ListState", e);
        }
    }

    @Override
    public void add(T value) throws IOException {
        byte[] key = backend.buildStorageKeyForCurrent();
        try {
            List<T> list = readList(key);
            list.add(value);
            writeList(key, list);
        } catch (RocksDBException e) {
            throw new IOException("Failed to add to ListState", e);
        }
    }

    @Override
    public void addAll(Iterable<T> values) throws IOException {
        byte[] key = backend.buildStorageKeyForCurrent();
        try {
            List<T> list = readList(key);
            for (T value : values) {
                list.add(value);
            }
            writeList(key, list);
        } catch (RocksDBException e) {
            throw new IOException("Failed to addAll to ListState", e);
        }
    }

    @Override
    public void update(Iterable<T> values) throws IOException {
        byte[] key = backend.buildStorageKeyForCurrent();
        List<T> newList = new ArrayList<>();
        for (T value : values) {
            newList.add(value);
        }
        try {
            writeList(key, newList);
        } catch (RocksDBException e) {
            throw new IOException("Failed to update ListState", e);
        }
    }

    @Override
    public void clear() {
        byte[] key = backend.buildStorageKeyForCurrent();
        try {
            backend.getDb().delete(cfHandle, key);
        } catch (RocksDBException e) {
            throw new StreamException("Failed to clear ListState", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<T> readList(byte[] key) throws RocksDBException {
        byte[] bytes = backend.getDb().get(cfHandle, key);
        if (bytes == null) {
            return new ArrayList<>();
        }
        return (List<T>) RocksDBValueSerDe.deserializeList(bytes, descriptor.getValueType());
    }

    private void writeList(byte[] key, List<T> list) throws RocksDBException {
        backend.getDb().put(cfHandle, key, RocksDBValueSerDe.serialize(list));
    }
}
