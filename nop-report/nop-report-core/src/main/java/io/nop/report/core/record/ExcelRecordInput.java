package io.nop.report.core.record;

import io.nop.core.lang.xml.parse.IXNodeParser;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IResource;
import io.nop.dataset.record.IRecordInput;

import java.io.IOException;
import java.util.List;

public class ExcelRecordInput<T> implements IRecordInput<T> {
    private final IResource resource;
    private final ExcelIOConfig config;

    private List<String> headers;
    private List<String> headerLabels;

    private long readCount;

    public ExcelRecordInput(IResource resource, ExcelIOConfig config) {
        this.resource = resource;
        this.config = config;
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
    public long getReadCount() {
        return readCount;
    }

    @Override
    public void close() throws IOException {

    }

    @Override
    public boolean hasNext() {
        return false;
    }

    @Override
    public T next() {
        return null;
    }
}
