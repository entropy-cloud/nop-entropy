package io.nop.record_mapping.impl;

import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.DictOptionBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.validate.IValidationErrorCollector;
import io.nop.commons.util.StringHelper;
import io.nop.core.dict.DictProvider;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.type.IGenericType;
import io.nop.record_mapping.IRecordMapping;
import io.nop.record_mapping.RecordMappingContext;
import io.nop.record_mapping.model.RecordFieldMappingConfig;
import io.nop.record_mapping.model.RecordMappingConfig;
import io.nop.xlang.xmeta.ISchema;
import io.nop.xlang.xmeta.SimpleSchemaValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.nop.record_mapping.RecordMappingErrors.ARG_DICT;
import static io.nop.record_mapping.RecordMappingErrors.ARG_FIELD_NAME;
import static io.nop.record_mapping.RecordMappingErrors.ARG_VALUE;
import static io.nop.record_mapping.RecordMappingErrors.ERR_RECORD_FIELD_IS_MANDATORY;
import static io.nop.record_mapping.RecordMappingErrors.ERR_RECORD_FIELD_NOT_COLLECTION_TYPE;
import static io.nop.record_mapping.RecordMappingErrors.ERR_RECORD_FIELD_VALUE_NOT_IN_DICT;
import static io.nop.xlang.XLangErrors.ARG_BIZ_OBJ_NAME;

public class ModelBasedRecordMapping implements IRecordMapping {
    static final Logger LOG = LoggerFactory.getLogger(ModelBasedRecordMapping.class);

    private final RecordMappingConfig config;

    public ModelBasedRecordMapping(RecordMappingConfig config) {
        this.config = config;
    }

    @Override
    public Object newTarget() {
        return config.newTarget();
    }

    @Override
    public void map(Object source, Object target, RecordMappingContext ctx) {
        mapObject(config, source, target, ctx);
    }

    protected void mapObject(RecordMappingConfig mapping, Object source, Object target, RecordMappingContext ctx) {
        if (ctx.getSourceRoot() == null) {
            ctx.setSourceRoot(source);
            ctx.setTargetRoot(target);
        }

        if (mapping.getBeforeMapping() != null)
            mapping.getBeforeMapping().call3(null, source, target, ctx, ctx.getEvalScope());

        for (RecordFieldMappingConfig field : mapping.getFields()) {
            mapField(mapping.getName(), field, source, target, ctx);
        }

        if (mapping.getAfterMapping() != null)
            mapping.getAfterMapping().call3(null, source, target, ctx, ctx.getEvalScope());
    }

    protected void mapField(String mappingName, RecordFieldMappingConfig field, Object source, Object target, RecordMappingContext ctx) {
        if (field.getWhen() != null) {
            boolean b = ConvertHelper.toTruthy(field.getWhen().call3(null, source, target, ctx, ctx.getEvalScope()));
            if (!b) {
                LOG.debug("nop.record.map-field-not-match-condition:from={},to={},loc={},source={}",
                        field.getFrom(), field.getName(), field.getLocation(), source);
                return;
            }
        }

        if (field.getBeforeFieldMapping() != null) {
            field.getBeforeFieldMapping().call3(null, source, target, ctx, ctx.getEvalScope());
        }

        mapField0(mappingName, field, source, target, ctx);

        if (field.getAfterFieldMapping() != null) {
            field.getAfterFieldMapping().call3(null, source, target, ctx, ctx.getEvalScope());
        }
    }

    protected void mapField0(String mappingName, RecordFieldMappingConfig field, Object source, Object target, RecordMappingContext ctx) {
        if (field.getMapping() != null) {
            mapObjectField(field, source, target, ctx);
        } else {
            // 1. 从源对象上获取值，或者动态计算值
            Object value = getFromValue(field, source, target, ctx);

            if (field.getValueExpr() != null) {
                value = field.getValueExpr().call2(null, value, ctx, ctx.getEvalScope());
            }

            if (field.getItemMapping() != null) {
                // 映射Map或者List
                if (value == null) {
                    if (field.isMandatory())
                        throw new NopException(ERR_RECORD_FIELD_IS_MANDATORY).source(field)
                                .param(ARG_FIELD_NAME, field.getName()).param(ARG_BIZ_OBJ_NAME, mappingName);
                } else if (value instanceof Map) {
                    mapMapField(field, (Map<String, Object>) value, source, target, ctx);
                } else if (value instanceof Collection) {
                    mapCollectionField(field, (Collection<?>) value, source, target, ctx);
                } else {
                    throw new NopException(ERR_RECORD_FIELD_NOT_COLLECTION_TYPE)
                            .param(ARG_BIZ_OBJ_NAME, mappingName)
                            .param(ARG_FIELD_NAME, field.getName()).source(field)
                            .param(ARG_VALUE, value);
                }
            } else {

                // 2. 对源对象返回的值进行映射，如果为null，则返回defaultValue
                value = applyValueMapper(field, value);

                if (value != null) {
                    IGenericType type = field.getType();
                    if (type != null)
                        value = BeanTool.castBeanToType(value, type);

                    ISchema schema = field.getSchema();
                    // 验证值满足schema要求
                    if (schema != null && !ctx.isSkipValidation()) {
                        validateValue(schema, mappingName, field, value, ctx);
                    }
                }

                if (StringHelper.isEmptyObject(value) && field.isMandatory())
                    throw new NopException(ERR_RECORD_FIELD_IS_MANDATORY).source(field)
                            .param(ARG_FIELD_NAME, field.getName()).param(ARG_BIZ_OBJ_NAME, mappingName);

                BeanTool.setComplexProperty(target, field.getName(), value);
            }
        }
    }

    protected void mapMapField(RecordFieldMappingConfig field, Map<String, Object> value,
                               Object source, Object target, RecordMappingContext ctx) {
        Object toValue = BeanTool.makeComplexProperty(target, field.getName(), field.getObjectConstructor(false, source, target, ctx));
        if (toValue == null) {
            toValue = new LinkedHashMap<>();
            BeanTool.setComplexProperty(target, field.getName(), toValue);
        }

        RecordMappingConfig itemMapping = field.getResolvedItemMapping();
        for (Map.Entry<String, Object> entry : value.entrySet()) {
            String key = entry.getKey();
            Object itemValue = entry.getValue();

            if (field.getItemFilterExpr() != null) {
                boolean b = ConvertHelper.toTruthy(field.getItemFilterExpr().call3(null, itemValue, key, ctx, ctx.getEvalScope()));
                if (!b)
                    continue;
            }

            Object toItemValue = BeanTool.instance().makeProperty(toValue, key, ctx.getEvalScope());
            Object sourceParent = ctx.getSourceParent();
            Object targetParent = ctx.getTargetParent();

            ctx.setSourceParent(value);
            ctx.setTargetParent(toValue);

            if (toItemValue == null) {
                toItemValue = field.getItemConstructor(value, toValue, ctx).get();
                BeanTool.setProperty(toValue, key, toItemValue);
            }
            mapObject(itemMapping, itemValue, toItemValue, ctx);

            ctx.setSourceParent(sourceParent);
            ctx.setTargetParent(targetParent);
        }
    }

    protected void mapCollectionField(RecordFieldMappingConfig field, Collection<?> value, Object source, Object target, RecordMappingContext ctx) {
        Object toValue = BeanTool.makeComplexProperty(target, field.getName(), field.getObjectConstructor(true, source, target, ctx));
        if (toValue == null) {
            toValue = new ArrayList<>();
            BeanTool.setComplexProperty(target, field.getName(), toValue);
        }

        RecordMappingConfig itemMapping = field.getResolvedItemMapping();
        int index = 0;
        for (Object itemValue : value) {
            if (field.getItemFilterExpr() != null) {
                boolean b = ConvertHelper.toTruthy(field.getItemFilterExpr().call3(null, itemValue, index, ctx, ctx.getEvalScope()));
                if (!b)
                    continue;
            }
            index++;

            Object sourceParent = ctx.getSourceParent();
            Object targetParent = ctx.getTargetParent();

            ctx.setSourceParent(value);
            ctx.setTargetParent(toValue);

            Object toItemValue = field.getItemConstructor(itemValue, null, ctx).get();
            ((Collection<Object>) toValue).add(toItemValue);

            mapObject(itemMapping, itemValue, toItemValue, ctx);

            ctx.setSourceParent(sourceParent);
            ctx.setTargetParent(targetParent);
        }
    }

    protected void mapObjectField(RecordFieldMappingConfig field, Object source, Object target, RecordMappingContext ctx) {
        if (field.isVirtual()) {
            mapObject(field.getResolvedMapping(), source, target, ctx);
        } else {
            Object fromValue = BeanTool.getComplexProperty(source, field.getFrom());
            Object toValue = BeanTool.makeComplexProperty(target, field.getName(), field.getObjectConstructor(true, source, target, ctx));

            Object sourceParent = ctx.getSourceParent();
            Object targetParent = ctx.getTargetParent();
            ctx.setSourceParent(source);
            ctx.setTargetRoot(target);
            mapObject(field.getResolvedMapping(), fromValue, toValue, ctx);
            ctx.setSourceParent(sourceParent);
            ctx.setTargetRoot(targetParent);
        }
    }

    protected void validateValue(ISchema schema, String mappingName, RecordFieldMappingConfig field, Object value, RecordMappingContext ctx) {
        IGenericType type = field.getType();
        if (type == null)
            type = schema.getType();

        String fieldName = field.getName();
        if (fieldName == null)
            fieldName = field.getFrom();

        if (type != null) {
            if (type.getStdDataType().isSimpleType()) {
                SimpleSchemaValidator.INSTANCE.validate(schema, field.getLocation(),
                        mappingName, fieldName, value,
                        ctx.getEvalScope(), IValidationErrorCollector.THROW_ERROR);
            }
        }

        String dictName = schema.getDict();
        if (dictName != null) {
            DictBean dictBean = DictProvider.instance().requireDict(ContextProvider.currentLocale(), dictName,
                    ctx.getCache(), ctx);
            DictOptionBean option = dictBean.getOptionByValue(value);
            if (option == null) {
                String dict = dictName;
                if (dictBean.getLabel() != null)
                    dict = dictBean.getLabel();

                throw new NopException(ERR_RECORD_FIELD_VALUE_NOT_IN_DICT).source(field)
                        .param(ARG_DICT, dict)
                        .param(ARG_BIZ_OBJ_NAME, mappingName)
                        .param(ARG_FIELD_NAME, fieldName).param(ARG_VALUE, value);
            }
        }
    }

    protected Object getFromValue(RecordFieldMappingConfig field, Object source, Object target, RecordMappingContext ctx) {
        if (field.getComputeExpr() != null)
            return field.getComputeExpr().call3(null, source, target, ctx, ctx.getEvalScope());

        if (field.getFrom() != null) {
            Object value = BeanTool.getComplexProperty(source, field.getFrom());
            if (value == null) {
                if (field.getAlias() != null) {
                    for (String alias : field.getAlias()) {
                        value = BeanTool.getComplexProperty(source, alias);
                        if (value != null)
                            return value;
                    }
                }
            }
        }

        // 既不是动态计算的值，也没有指定源对象上的属性路径，则返回null
        return null;
    }

    protected Object applyValueMapper(RecordFieldMappingConfig field, Object value) {
        if (value == null)
            return field.getDefaultValue();

        if (field.getValueMapper() != null) {
            value = field.getValueMapper().mapValue(value.toString());
        }
        return value;
    }
}
