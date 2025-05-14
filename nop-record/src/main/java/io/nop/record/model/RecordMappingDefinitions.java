package io.nop.record.model;

import io.nop.api.core.util.INeedInit;
import io.nop.record.mapping.IRecordMapping;
import io.nop.record.mapping.ModelBasedRecordMapping;
import io.nop.record.model._gen._RecordMappingDefinitions;

import java.util.HashMap;
import java.util.Map;

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
    }

    public IRecordMapping getCompiledMapping(String name) {
        return compiledMappings.get(name);
    }
}
