/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.record.csv;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.bytes.ByteString;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.dataset.record.IRecordOutput;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class CsvRecordOutput<T> implements IRecordOutput<T> {
    private List<String> headers;
    private List<String> headerLabels;

    private final CSVPrinter writer;
    private boolean headersWritten;
    private long writeCount;

    public CsvRecordOutput(IResource resource, String encoding, CSVFormat format,
                           boolean supportZip) {
        Writer out = ResourceHelper.toWriter(resource, encoding, supportZip);
        try {
            this.writer = new CSVPrinter(out, format);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }


    public long getWriteCount() {
        return writeCount;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public void setHeaders(List<String> headers) {
        this.headers = headers;
    }

    public List<String> getHeaderLabels() {
        return headerLabels;
    }

    public void setHeaderLabels(List<String> headerLabels) {
        this.headerLabels = headerLabels;
    }

    @Override
    public void writeBatch(Collection<? extends T> records) throws IOException {
        if (records == null || records.isEmpty()) {
            if (!headersWritten) {
                if (!CollectionHelper.isEmpty(headers)) {
                    try {
                        writeHeaders(null);
                    } catch (IOException e) {
                        throw NopException.adapt(e);
                    }
                    headersWritten = true;
                }
            }
            return;
        }

        if (!headersWritten) {
            writeHeaders(CollectionHelper.first(records));
            headersWritten = true;
        }

        for (T record : records) {
            write(record);
        }
    }

    @Override
    public void write(T record) throws IOException {
        if (!headersWritten) {
            writeHeaders(record);
            headersWritten = true;
        }

        if (headers == null) {
            writer.printRecord((Iterable<?>) record);
            writeCount++;
            return;
        }

        Object[] row = new String[headers.size()];
        int index = 0;
        for (String header : headers) {
            Object value = BeanTool.getComplexProperty(record, header);
            row[index++] = toString(value);
        }
        writer.printRecord(row);
        writeCount++;
    }

    private String toString(Object value) {
        if (value instanceof ByteString)
            return ((ByteString) value).hex();
        return StringHelper.toString(value, "");
    }

    private void writeHeaders(T record) throws IOException {
        if (headers == null || headers.isEmpty()) {
            if (record instanceof Map) {
                headers = CollectionHelper.toStringList(((Map<?, ?>) record).keySet());
            } else if (record != null && !(record instanceof Collection)) {
                headers = new ArrayList<>(BeanTool.getReadableComplexPropNames(record.getClass()));
            }
        }
        if (headers != null) {
            if (headerLabels != null) {
                writer.printRecord(headerLabels.toArray());
            } else {
                writer.printRecord(headers.toArray());
            }
        }
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}