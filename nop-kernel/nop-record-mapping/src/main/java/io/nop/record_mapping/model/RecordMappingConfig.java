package io.nop.record_mapping.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.record_mapping.model._gen._RecordMappingConfig;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.nop.record_mapping.RecordMappingErrors.ARG_ALLOWED_FIELD_NAMES;
import static io.nop.record_mapping.RecordMappingErrors.ARG_FIELD_NAME;
import static io.nop.record_mapping.RecordMappingErrors.ERR_RECORD_UNKNOWN_FIELD;
import static io.nop.record_mapping.RecordMappingErrors.ERR_RECORD_UNKNOWN_FROM_FIELD;

public class RecordMappingConfig extends _RecordMappingConfig {
    private IClassModel toClassModel;
    private RecordMappingConfig resolvedBaseMapping;

    private Map<String, RecordFieldMappingConfig> fromFields = new HashMap<>();

    public RecordMappingConfig() {

    }

    public RecordMappingConfig getResolvedBaseMapping() {
        return resolvedBaseMapping;
    }

    public void setResolvedBaseMapping(RecordMappingConfig resolvedBaseMapping) {
        this.resolvedBaseMapping = resolvedBaseMapping;
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

            if (field.getFrom() != null)
                this.fromFields.putIfAbsent(field.getFrom(), field);

            if (field.getAlias() != null) {
                for (String alias : field.getAlias()) {
                    this.fromFields.putIfAbsent(alias, field);
                }
            }
        }
    }

    public Set<String> getFieldNames() {
        return getFields().stream().map(RecordFieldMappingConfig::getName).collect(Collectors.toSet());
    }

    public Set<String> getFieldFroms() {
        return fromFields.keySet();
    }

    public RecordFieldMappingConfig requireField(String name) {
        RecordFieldMappingConfig field = getField(name);
        if (field == null)
            throw new NopException(ERR_RECORD_UNKNOWN_FIELD)
                    .param(ARG_FIELD_NAME, name)
                    .param(ARG_ALLOWED_FIELD_NAMES, this.getFields().size());
        return field;
    }

    public RecordFieldMappingConfig getFieldByFrom(String from) {
        RecordFieldMappingConfig field = fromFields.get(from);
        return field;
    }

    public RecordFieldMappingConfig requireFieldByFrom(SourceLocation loc, String from) {
        RecordFieldMappingConfig field = fromFields.get(from);
        if (field == null)
            throw new NopException(ERR_RECORD_UNKNOWN_FROM_FIELD)
                    .loc(loc)
                    .param(ARG_FIELD_NAME, from)
                    .param(ARG_ALLOWED_FIELD_NAMES, fromFields.keySet());
        return field;
    }
}
