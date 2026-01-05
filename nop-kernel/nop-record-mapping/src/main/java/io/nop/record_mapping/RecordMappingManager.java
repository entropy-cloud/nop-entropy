package io.nop.record_mapping;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.record_mapping.impl.RecordMappingManagerImpl;

@GlobalInstance
public class RecordMappingManager {
    private static IRecordMappingManager _INSTANCE = new RecordMappingManagerImpl();

    public static IRecordMappingManager instance() {
        return _INSTANCE;
    }

    public static void registerInstance(IRecordMappingManager manager) {
        _INSTANCE = manager;
    }
}
