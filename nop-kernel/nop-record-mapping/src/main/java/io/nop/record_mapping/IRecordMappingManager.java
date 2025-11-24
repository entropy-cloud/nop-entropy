package io.nop.record_mapping;

import io.nop.record_mapping.model.RecordMappingConfig;

public interface IRecordMappingManager {
    IRecordMapping getRecordMapping(String mappingName);

    RecordMappingConfig getRecordMappingConfig(String mappingName);
}
