package io.nop.record_mapping.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.path.AntPathMatcher;
import io.nop.commons.path.ICompiledPathMatcher;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.record_mapping.model._gen._RecordPatternFieldConfig;

import static io.nop.record_mapping.RecordMappingErrors.*;

public class RecordPatternFieldConfig extends _RecordPatternFieldConfig implements IRecordFieldMappingConfig {
    private RecordMappingConfig resolvedMapping;
    private RecordMappingConfig resolvedItemMapping;
    private IClassModel itemClassModel;
    private IClassModel classModel;
    private ICompiledPathMatcher compiledPattern;

    public RecordPatternFieldConfig() {

    }

    public Object getNormalizedDefaultValue() {
        return null;
    }

    public RecordMappingConfig getResolvedMapping() {
        return resolvedMapping;
    }

    public void setResolvedMapping(RecordMappingConfig resolvedMapping) {
        this.resolvedMapping = resolvedMapping;
    }

    public RecordMappingConfig getResolvedItemMapping() {
        return resolvedItemMapping;
    }

    public void setResolvedItemMapping(RecordMappingConfig resolvedItemMapping) {
        this.resolvedItemMapping = resolvedItemMapping;
    }

    public void setItemClassModel(IClassModel itemClassModel) {
        this.itemClassModel = itemClassModel;
    }

    public void setClassModel(IClassModel classModel) {
        this.classModel = classModel;
    }

    public ICompiledPathMatcher getCompiledPattern() {
        return compiledPattern;
    }

    public IClassModel getItemClassModel() {
        return itemClassModel;
    }

    public IClassModel getClassModel() {
        return classModel;
    }

    public void init(RecordMappingDefinitions mappings) {
        if (this.getMapping() != null) {
            RecordMappingConfig mapping = mappings.getMapping(this.getMapping());
            if (mapping == null)
                throw new NopException(ERR_RECORD_FIELD_MAPPING_NOT_FOUND).source(this).param(ARG_MAPPING_NAME, this.getMapping())
                        .param(ARG_FIELD_NAME, getId());
            setResolvedMapping(mapping);
        }

        if (this.getItemMapping() != null) {
            RecordMappingConfig mapping = mappings.getMapping(this.getItemMapping());
            if (mapping == null)
                throw new NopException(ERR_RECORD_FIELD_MAPPING_NOT_FOUND).source(this).param(ARG_MAPPING_NAME, this.getItemMapping())
                        .param(ARG_FIELD_NAME, getId());
            setResolvedItemMapping(mapping);
        }

        IGenericType type = getType();
        if (type != null) {
            if (type.isMapLike() || type.isCollectionLike()) {
                IGenericType componentType = type.getComponentType();
                if (componentType != PredefinedGenericTypes.ANY_TYPE) {
                    this.itemClassModel = ReflectionManager.instance().getClassModelForType(componentType);
                }
            }
            classModel = ReflectionManager.instance().getClassModelForType(type);
        }

        if (getFromPattern() != null)
            compiledPattern = new AntPathMatcher(".").compile(getFromPattern());
    }
}
