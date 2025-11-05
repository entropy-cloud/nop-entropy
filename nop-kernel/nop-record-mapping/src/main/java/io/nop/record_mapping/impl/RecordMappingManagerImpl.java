package io.nop.record_mapping.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.record_mapping.IRecordMapping;
import io.nop.record_mapping.IRecordMappingManager;
import io.nop.record_mapping.model.RecordMappingDefinitions;

import static io.nop.record_mapping.RecordMappingErrors.ARG_MAPPING_NAME;
import static io.nop.record_mapping.RecordMappingErrors.ERR_RECORD_MAPPING_NOT_FOUND;

public class RecordMappingManagerImpl implements IRecordMappingManager {
    @Override
    public IRecordMapping getRecordMapping(String mappingName) {
        String path = getMappingPath(mappingName);
        RecordMappingDefinitions defs = loadMappingsFromPath(path);
        String name = StringHelper.lastPart(mappingName, '.');
        IRecordMapping mapping = defs.getCompiledMapping(name);
        if (mapping == null)
            throw new NopException(ERR_RECORD_MAPPING_NOT_FOUND).param(ARG_MAPPING_NAME, mappingName);
        return mapping;
    }

    public RecordMappingDefinitions loadMappingsFromPath(String path) {
        return (RecordMappingDefinitions) ResourceComponentManager.instance().loadComponentModel(path);
    }

    String getMappingPath(String mappingName) {
        Guard.checkArgument(StringHelper.isValidClassName(mappingName));

        int pos = mappingName.lastIndexOf('.');
        String prefix = mappingName.substring(0, pos).replace('.', '/');
        return "resolve-record-mappings:" + prefix;
    }
}
