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
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.dataset.record.IRecordOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

public class HeaderListRecordOutput<R> implements IRecordOutput<List<Object>> {

    private final List<R> records = new ArrayList<>();
    private final BiFunction<List<String>, List<Object>, R> rowBuilder;
    private boolean headersWritten;
    private long writeCount;
    private int headerRowCount;
    private List<String> headers;
    private List<String> headerLabels;
    private IEvalFunction headersNormalizer;

    private List<List<String>> headerRows = new ArrayList<>();

    public HeaderListRecordOutput(int headerRowCount, BiFunction<List<String>, List<Object>, R> rowBuilder) {
        this.headerRowCount = headerRowCount;
        this.rowBuilder = rowBuilder;
    }

    public void setHeaders(List<String> headers) {
        this.headers = headers;
    }

    public void setHeaderLabels(List<String> headerLabels) {
        this.headerLabels = headerLabels;
    }

    public void setHeadersNormalizer(IEvalFunction headersNormalizer) {
        this.headersNormalizer = headersNormalizer;
    }

    public List<R> getResult() {
        return records;
    }

    public long getWriteCount() {
        return writeCount;
    }

    @Override
    public void write(List<Object> record) {
        if (headerRowCount > writeCount) {
            List<String> row = CollectionHelper.toStringList(record);
            headerRows.add(row);
            writeCount++;
            return;
        }

        if (!headersWritten) {
            normalizeHeaders();
            headersWritten = true;
        }

        doWriteRecord(record);
        writeCount++;
    }

    private void normalizeHeaders() {
        if (this.headers == null || this.headers.isEmpty()) {
            if (!headerRows.isEmpty())
                this.headers = headerRows.get(headerRows.size() - 1);

            if (headersNormalizer != null) {
                this.headers = (List<String>) this.headersNormalizer.call2(null, headers, headerRows, EvalExprProvider.newEvalScope());
            }
        }
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
