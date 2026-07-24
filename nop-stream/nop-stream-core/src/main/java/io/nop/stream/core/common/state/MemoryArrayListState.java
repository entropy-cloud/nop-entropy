/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state;

import io.nop.stream.core.common.state.backend.IOperatorStateBackend;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class MemoryArrayListState<T> implements ListState<T>, Serializable {

    private static final long serialVersionUID = 1L;

    private final ListStateDescriptor<T> descriptor;
    private final IOperatorStateBackend backend;
    private final List<T> list;

    public MemoryArrayListState(ListStateDescriptor<T> descriptor, IOperatorStateBackend backend) {
        this.descriptor = descriptor;
        this.backend = backend;
        Object existing = backend.getRawState(descriptor.getName());
        if (existing instanceof List) {
            this.list = new ArrayList<>((List<T>) existing);
        } else {
            this.list = new ArrayList<>();
        }
    }

    @Override
    public Iterable<T> get() throws IOException {
        return Collections.unmodifiableList(new ArrayList<>(list));
    }

    @Override
    public void add(T value) throws IOException {
        list.add(value);
        backend.putRawState(descriptor.getName(), new ArrayList<>(list));
    }

    @Override
    public void addAll(Iterable<T> values) throws IOException {
        for (T value : values) {
            list.add(value);
        }
        backend.putRawState(descriptor.getName(), new ArrayList<>(list));
    }

    @Override
    public void update(Iterable<T> values) throws IOException {
        list.clear();
        for (T value : values) {
            list.add(value);
        }
        backend.putRawState(descriptor.getName(), new ArrayList<>(list));
    }

    @Override
    public void clear() {
        list.clear();
        backend.putRawState(descriptor.getName(), new ArrayList<>(list));
    }

    public List<T> getCopyOfList() {
        return new ArrayList<>(list);
    }
}
