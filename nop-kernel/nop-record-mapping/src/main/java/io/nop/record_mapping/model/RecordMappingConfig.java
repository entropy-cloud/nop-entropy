package io.nop.record_mapping.model;

import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.record_mapping.model._gen._RecordMappingConfig;

import java.util.LinkedHashMap;

public class RecordMappingConfig extends _RecordMappingConfig {
    private IClassModel toClassModel;

    public RecordMappingConfig() {

    }

    public Object newTarget() {
        if (toClassModel == null) {
            String toClassName = getToClass();
            if (toClassName != null) {
                toClassModel = ReflectionManager.instance().loadClassModel(toClassName);
            }
        }
        if (toClassModel == null)
            return new LinkedHashMap<>();
        return toClassModel.newInstance();
    }

    public void init(RecordMappingDefinitions definitions) {
        for (RecordFieldMappingConfig field : getFields()) {
            field.init(definitions);
        }
    }
}
