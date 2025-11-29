package io.nop.record_mapping.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.collections.KeyedList;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.record_mapping.RecordMappingContext;
import io.nop.record_mapping.model._gen._RecordFieldMappingConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.function.Supplier;

import static io.nop.record_mapping.RecordMappingErrors.ARG_FIELD_NAME;
import static io.nop.record_mapping.RecordMappingErrors.ARG_MAPPING_NAME;
import static io.nop.record_mapping.RecordMappingErrors.ERR_RECORD_FIELD_MAPPING_NOT_FOUND;

public class RecordFieldMappingConfig extends _RecordFieldMappingConfig {
    private RecordMappingConfig resolvedMapping;
    private RecordMappingConfig resolvedItemMapping;
    private IClassModel itemClassModel;
    private IClassModel classModel;
    private String objName;

    private Object normalizedDefaultValue;

    public RecordFieldMappingConfig() {

    }

    public Object getNormalizedDefaultValue() {
        return normalizedDefaultValue;
    }

    public String getFromOrName() {
        String from = getFrom();
        if (from != null)
            return from;
        return getName();
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

    public void init(RecordMappingDefinitions mappings) {
        if (this.getMapping() != null) {
            RecordMappingConfig mapping = mappings.getMapping(this.getMapping());
            if (mapping == null)
                throw new NopException(ERR_RECORD_FIELD_MAPPING_NOT_FOUND).source(this).param(ARG_MAPPING_NAME, this.getMapping())
                        .param(ARG_FIELD_NAME, getName());
            setResolvedMapping(mapping);
        }

        if (this.getItemMapping() != null) {
            RecordMappingConfig mapping = mappings.getMapping(this.getItemMapping());
            if (mapping == null)
                throw new NopException(ERR_RECORD_FIELD_MAPPING_NOT_FOUND).source(this).param(ARG_MAPPING_NAME, this.getItemMapping())
                        .param(ARG_FIELD_NAME, getName());
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
            if (this.getDefaultValue() != null)
                this.normalizedDefaultValue = type.getStdDataType().convert(this.getDefaultValue());
        }
    }

    public Supplier<Object> getItemConstructor(Object source, Object target, RecordMappingContext ctx) {
        if (getNewItemExpr() != null)
            return () -> getNewItemExpr().call3(null, source, target, ctx, ctx.getEvalScope());

        if (itemClassModel != null)
            return itemClassModel::newInstance;

        return LinkedHashMap::new;
    }

    public Supplier<Object> getObjectConstructor(boolean collection, Object source, Object target, RecordMappingContext ctx) {
        if (getNewInstanceExpr() != null)
            return () -> getNewInstanceExpr().call3(null, source, target, ctx, ctx.getEvalScope());

        if (collection && getKeyProp() != null) {
            String keyProp = getKeyProp();
            return () -> new KeyedList<>(item -> BeanTool.getProperty(item, keyProp));
        }

        if (classModel != null)
            return classModel::newInstance;

        if (collection)
            return ArrayList::new;
        return null;
    }
}