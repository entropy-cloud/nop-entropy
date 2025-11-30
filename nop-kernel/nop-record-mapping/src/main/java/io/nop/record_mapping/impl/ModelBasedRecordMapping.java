package io.nop.record_mapping.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.record_mapping.IRecordMapping;
import io.nop.record_mapping.RecordMappingContext;
import io.nop.record_mapping.model.RecordFieldMappingConfig;
import io.nop.record_mapping.model.RecordMappingConfig;

import static io.nop.record_mapping.RecordMappingErrors.ARG_FIELD_NAME;
import static io.nop.record_mapping.RecordMappingErrors.ARG_MAPPING_NAME;
import static io.nop.record_mapping.RecordMappingErrors.ARG_VALUE;
import static io.nop.record_mapping.RecordMappingErrors.ERR_RECORD_FIELD_IS_MANDATORY;
import static io.nop.record_mapping.RecordMappingErrors.ERR_RECORD_FIELD_NOT_COLLECTION_TYPE;

public class ModelBasedRecordMapping implements IRecordMapping {
    private final RecordMappingConfig config;
    private final RecordMappingTool tool;

    public ModelBasedRecordMapping(RecordMappingConfig config) {
        this(config, RecordMappingTool.DEFAULT);
    }

    public ModelBasedRecordMapping(RecordMappingConfig config, RecordMappingTool tool) {
        this.config = config;
        this.tool = tool;
    }

    @Override
    public Object newTarget(RecordMappingContext ctx) {
        return config.newTarget(ctx);
    }

    @Override
    public void map(Object source, Object target, RecordMappingContext ctx) {
        mapObject(config, source, target, ctx);
    }

    public void mapObject(RecordMappingConfig mapping, Object source, Object target, RecordMappingContext ctx) {
        tool.executeForEachField(mapping, source, target, ctx, field -> {
            mapField0(mapping, field, source, target, ctx);
        });
    }

    protected void mapField0(RecordMappingConfig mapping, RecordFieldMappingConfig field, Object source, Object target, RecordMappingContext ctx) {
        if (field.getMapping() != null) {
            mapObjectField(field, source, target, ctx);
        } else {
            String mappingName = mapping.getName();
            // 1. 从源对象上获取值，或者动态计算值
            Object value = tool.getFromValue(field, source, target, ctx);

            if (field.getItemMapping() != null) {
                // 映射Map或者List
                if (value == null) {
                    if (field.isMandatory())
                        throw new NopException(ERR_RECORD_FIELD_IS_MANDATORY).source(field)
                                .param(ARG_FIELD_NAME, field.getName()).param(ARG_MAPPING_NAME, mappingName);
                } else if (value instanceof java.util.Map) {
                    mapMapField(field, (java.util.Map<String, Object>) value, source, target, ctx);
                } else if (value instanceof java.util.Collection) {
                    mapCollectionField(field, (java.util.Collection<?>) value, source, target, ctx);
                } else {
                    throw new NopException(ERR_RECORD_FIELD_NOT_COLLECTION_TYPE)
                            .source(field)
                            .param(ARG_MAPPING_NAME, mappingName)
                            .param(ARG_FIELD_NAME, field.getName()).source(field)
                            .param(ARG_VALUE, value);
                }
            } else {
                // 2. 对源对象返回的值进行映射，如果为null，则返回defaultValue
                value = tool.processFieldValue(mapping, field, value, ctx);

                // 4. 设置到目标对象
                tool.setTargetValue(field, target, value, ctx);
            }
        }
    }

    public void mapMapField(RecordFieldMappingConfig field, java.util.Map<String, Object> value,
                            Object source, Object target, RecordMappingContext ctx) {
        RecordMappingConfig itemMapping = field.getResolvedItemMapping();

        tool.mapMapField(field, value, source, target, ctx, (fromItem, toItem) -> {
            mapObject(itemMapping, fromItem, toItem, ctx);
        });
    }

    public Object mapCollectionField(RecordFieldMappingConfig field, java.util.Collection<?> value,
                                   Object source, Object target, RecordMappingContext ctx) {
        RecordMappingConfig itemMapping = field.getResolvedItemMapping();

        return tool.mapCollectionField(field, value, source, target, ctx, (fromItem, toItem) -> {
            mapObject(itemMapping, fromItem, toItem, ctx);
        });
    }

    public void mapObjectField(RecordFieldMappingConfig field, Object source, Object target, RecordMappingContext ctx) {
        if (field.isVirtual()) {
            mapObject(field.getResolvedMapping(), source, target, ctx);
        } else {
            Object fromValue = tool.getFromValue(field, source, target, ctx);
            Object toValue = tool.makeTargetObject(field, source, target, ctx);

            mapObject(field.getResolvedMapping(), fromValue, toValue, ctx);
        }
    }
}