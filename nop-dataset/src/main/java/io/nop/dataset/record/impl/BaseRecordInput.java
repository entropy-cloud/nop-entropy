/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dataset.record.impl;

import io.nop.dataset.record.IRecordInput;
import io.nop.dataset.record.IRecordResourceMeta;

import java.util.Collections;
import java.util.List;

public class BaseRecordInput<T> implements IRecordInput<T> {
    private final List<T> records;
    private final IRecordResourceMeta meta;
    private int readCount;

    public BaseRecordInput(List<T> records, IRecordResourceMeta meta) {
        this.records = records == null ? Collections.emptyList() : records;
        this.meta = meta;
    }

    @Override
    public long getReadCount() {
        return readCount;
    }

    @Override
    public IRecordResourceMeta getMeta() {
        return meta;
    }

    @Override
    public long getTotalCount() {
        return records.size();
    }

    @Override
    public long skip(long count) {
        long beginCount = readCount;
        readCount += count;
        if (readCount > records.size()) {
            readCount = records.size();
        }
        return readCount - beginCount;
    }

    @Override
    public void close() {

    }

    @Override
    public boolean hasNext() {
        return readCount < records.size();
    }

    @Override
    public T next() {
        readCount++;
        return records.get(readCount);
    }
}
