package io.nop.record.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.record.mapping.RecordMappingContext;
import io.nop.record.model._gen._RecordFieldMappingConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.function.Supplier;

import static io.nop.record.RecordErrors.ARG_FIELD_NAME;
import static io.nop.record.RecordErrors.ARG_MAPPING_NAME;
import static io.nop.record.RecordErrors.ERR_RECORD_FIELD_MAPPING_NOT_FOUND;

public class RecordFieldMappingConfig extends _RecordFieldMappingConfig {
    private RecordMappingConfig resolvedMapping;
    private RecordMappingConfig resolvedItemMapping;
    private IClassModel itemClassModel;
    private IClassModel classModel;

    public RecordFieldMappingConfig() {

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
                        .param(ARG_FIELD_NAME, getTo());
            setResolvedMapping(mapping);
        }

        if (this.getItemMapping() != null) {
            RecordMappingConfig mapping = mappings.getMapping(this.getItemMapping());
            if (mapping == null)
                throw new NopException(ERR_RECORD_FIELD_MAPPING_NOT_FOUND).source(this).param(ARG_MAPPING_NAME, this.getItemMapping())
                        .param(ARG_FIELD_NAME, getTo());
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

        if (classModel != null)
            return classModel::newInstance;

        if (collection)
            return ArrayList::new;
        return null;
    }
}