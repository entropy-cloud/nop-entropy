package io.nop.record_mapping.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.INeedInit;
import io.nop.record_mapping.IRecordMapping;
import io.nop.record_mapping.impl.ModelBasedRecordMapping;
import io.nop.record_mapping.model._gen._RecordMappingDefinitions;

import java.util.HashMap;
import java.util.Map;

import static io.nop.record_mapping.RecordMappingErrors.ARG_MAPPING_NAME;
import static io.nop.record_mapping.RecordMappingErrors.ERR_RECORD_MAPPING_NOT_FOUND;

public class RecordMappingDefinitions extends _RecordMappingDefinitions implements INeedInit {
    private Map<String, IRecordMapping> compiledMappings = new HashMap<>();

    public RecordMappingDefinitions() {

    }

    @Override
    public void init() {
        for (RecordMappingConfig mapping : this.getMappings()) {
            mapping.init(this);
            this.compiledMappings.put(mapping.getName(), new ModelBasedRecordMapping(mapping));
        }

        for (RecordMappingConfig mapping : this.getMappings()) {
            if (mapping.getBaseMapping() != null) {
                RecordMappingConfig baseMapping = this.getMapping(mapping.getBaseMapping());
                if (baseMapping == null)
                    throw new NopException(ERR_RECORD_MAPPING_NOT_FOUND).source(this)
                            .param(ARG_MAPPING_NAME, mapping.getBaseMapping());
                mapping.setResolvedBaseMapping(baseMapping);
            }
        }
    }

    public IRecordMapping getCompiledMapping(String name) {
        return compiledMappings.get(name);
    }
}
