package io.nop.core.resource.record.list;

import io.nop.core.resource.IResource;
import io.nop.dataset.record.IRecordOutput;
import io.nop.dataset.record.IRecordResourceMeta;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ListRecordOutput<T> implements IRecordOutput<T> {
    private IResource resource;
    private String encoding;
    private List<String> headers;
    private Map<String, Object> headerMeta;
    private Map<String, Object> trailerMeta;
    private IRecordResourceMeta meta;

    private final List<T> ret = new ArrayList<>();

    public ListRecordOutput(IResource resource, String encoding) {
        this.resource = resource;
        this.encoding = encoding;
    }

    public ListRecordOutput() {
    }

    public String getEncoding() {
        return encoding;
    }

    public IResource getResource() {
        return resource;
    }

    @Override
    public long getWriteCount() {
        return ret.size();
    }

    public List<T> getResult() {
        return ret;
    }

    @Override
    public void write(T record) {
        ret.add(record);
    }

    @Override
    public void flush() {

    }

    @Override
    public void close() throws IOException {

    }

    public List<String> getHeaders() {
        return headers;
    }

    @Override
    public void setHeaders(List<String> headers) {
        this.headers = headers;
    }

    public Map<String, Object> getTrailerMeta() {
        return trailerMeta;
    }

    @Override
    public void setTrailerMeta(Map<String, Object> trailerMeta) {
        this.trailerMeta = trailerMeta;
    }

    public Map<String, Object> getHeaderMeta() {
        return headerMeta;
    }

    @Override
    public void setHeaderMeta(Map<String, Object> headerMeta) {
        this.headerMeta = headerMeta;
    }
}
