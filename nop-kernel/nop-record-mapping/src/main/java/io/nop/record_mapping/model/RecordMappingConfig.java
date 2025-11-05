package io.nop.record_mapping.model;

import io.nop.record_mapping.model._gen._RecordMappingConfig;

public class RecordMappingConfig extends _RecordMappingConfig {

    public RecordMappingConfig() {

    }

    public void init(RecordMappingDefinitions definitions) {
        for (RecordFieldMappingConfig field : getFields()) {
            field.init(definitions);
        }
    }
}
