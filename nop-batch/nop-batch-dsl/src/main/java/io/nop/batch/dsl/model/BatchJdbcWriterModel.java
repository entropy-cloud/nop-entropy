package io.nop.batch.dsl.model;

import io.nop.batch.dsl.model._gen._BatchJdbcWriterModel;

import java.util.HashMap;
import java.util.Map;

public class BatchJdbcWriterModel extends _BatchJdbcWriterModel {
    public BatchJdbcWriterModel() {

    }

    public Map<String, String> getFromNameMap() {
        if (!this.hasFields())
            return null;

        Map<String, String> ret = new HashMap<>();
        for (BatchWriteFieldModel field : getFields()) {
            String from = field.getFrom();
            if (from != null && !from.equals(field.getName())) {
                ret.put(field.getName(), field.getFrom());
            }
        }
        return ret.isEmpty() ? null : ret;
    }
}
