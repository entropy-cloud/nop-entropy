/**
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 */
package io.nop.core.resource.record.jsonl;

import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.dataset.record.IRecordOutput;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;

public class JsonlRecordOutput<T> implements IRecordOutput<T> {

    private final Writer writer;
    private long writeCount;

    public JsonlRecordOutput(IResource resource, String encoding, boolean supportZip) {
        this.writer = ResourceHelper.toWriter(resource, encoding, supportZip);
    }

    public JsonlRecordOutput(Writer out) {
        this.writer = out;
    }

    public long getWriteCount() {
        return writeCount;
    }

    @Override
    public void writeBatch(Collection<? extends T> records) throws IOException {
        if (records == null || records.isEmpty())
            return;
        for (T record : records) {
            write(record);
        }
    }

    @Override
    public void write(T record) throws IOException {
        String json = JsonTool.stringify(record);
        writer.write(json);
        writer.write('\n');
        writeCount++;
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
