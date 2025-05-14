package io.nop.record.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.record.model._gen._RecordFieldMappingConfig;

import static io.nop.record.RecordErrors.ARG_FIELD_NAME;
import static io.nop.record.RecordErrors.ARG_MAPPING_NAME;
import static io.nop.record.RecordErrors.ERR_RECORD_FIELD_MAPPING_NOT_FOUND;

public class RecordFieldMappingConfig extends _RecordFieldMappingConfig {
    private RecordMappingConfig resolvedMapping;

    public RecordFieldMappingConfig() {

    }

    public RecordMappingConfig getResolvedMapping() {
        return resolvedMapping;
    }

    public void setResolvedMapping(RecordMappingConfig resolvedMapping) {
        this.resolvedMapping = resolvedMapping;
    }


    public void init(RecordMappingDefinitions mappings) {
        if (this.getMapping() != null) {
            RecordMappingConfig mapping = mappings.getMapping(this.getMapping());
            if (mapping == null)
                throw new NopException(ERR_RECORD_FIELD_MAPPING_NOT_FOUND).source(this).param(ARG_MAPPING_NAME, this.getMapping())
                        .param(ARG_FIELD_NAME, getTo());
            setResolvedMapping(mapping);
        }
    }
}
