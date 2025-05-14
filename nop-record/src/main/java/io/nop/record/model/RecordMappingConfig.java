package io.nop.record.model;

import io.nop.record.model._gen._RecordMappingConfig;

public class RecordMappingConfig extends _RecordMappingConfig {

    public RecordMappingConfig() {

    }

    public void init(RecordMappingDefinitions definitions) {
        for (RecordFieldMappingConfig field : getFieldMappings()) {
            field.init(definitions);
        }
    }
}
