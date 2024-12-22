/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.record.list;

import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.dataset.record.IRecordOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

public class HeaderListRecordOutput<R> implements IRecordOutput<List<Object>> {
    private List<String> headers;
    private final List<R> records = new ArrayList<>();
    private final BiFunction<List<String>, List<Object>, R> rowBuilder;
    private boolean headersWritten;
    private long writeCount;
    private int skipCount;

    public HeaderListRecordOutput(int skipCount, BiFunction<List<String>, List<Object>, R> rowBuilder) {
        this.skipCount = skipCount;
        this.rowBuilder = rowBuilder;
    }

    public List<R> getResult() {
        return records;
    }

    public long getWriteCount() {
        return writeCount;
    }

    @Override
    public void write(List<Object> record) {
        if (skipCount > writeCount) {
            writeCount++;
            return;
        }

        if (!headersWritten) {
            this.headers = CollectionHelper.toStringList(record);
            headersWritten = true;
            return;
        }

        doWriteRecord(record);
        writeCount++;
    }

    private String toString(Object value) {
        return StringHelper.toString(value, "");
    }


    private void doWriteRecord(List<Object> record) {
        R row = rowBuilder.apply(headers, record);
        if (row != null)
            records.add(row);
    }

    @Override
    public void flush() {

    }

    @Override
    public void close() {
    }
}