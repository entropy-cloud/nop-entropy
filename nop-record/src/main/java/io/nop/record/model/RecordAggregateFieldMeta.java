package io.nop.record.model;

import io.nop.record.model._gen._RecordAggregateFieldMeta;

public class RecordAggregateFieldMeta extends _RecordAggregateFieldMeta {
    public RecordAggregateFieldMeta() {

    }

    public String getPropOrFieldName() {
        String prop = getProp();
        if (prop == null)
            prop = getName();
        return prop;
    }
}
