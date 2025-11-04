package io.nop.dataset.record.support;

import io.nop.dataset.record.IRecordResourceMeta;

import java.util.List;
import java.util.Map;

public class ProjectRecordResourceMeta implements IRecordResourceMeta {
    private final IRecordResourceMeta source;
    private final List<String> fields;

    public ProjectRecordResourceMeta(IRecordResourceMeta source, List<String> fields) {
        this.source = source;
        this.fields = fields;
    }

    @Override
    public List<String> getHeaders() {
        return fields;
    }

    @Override
    public Map<String, Object> getHeaderMeta() {
        return source.getHeaderMeta();
    }

    @Override
    public Map<String, Object> getTrailerMeta() {
        return source.getTrailerMeta();
    }
}
