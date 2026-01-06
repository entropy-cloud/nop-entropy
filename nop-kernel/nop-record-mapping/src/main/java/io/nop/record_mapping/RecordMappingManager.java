package io.nop.record_mapping;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.record_mapping.impl.RecordMappingManagerImpl;

@GlobalInstance
public class RecordMappingManager {
    private static IRecordMappingManager _instance = new RecordMappingManagerImpl();

    public static IRecordMappingManager instance() {
        return _instance;
    }

    public static void registerInstance(IRecordMappingManager manager) {
        _instance = manager;
    }
}
