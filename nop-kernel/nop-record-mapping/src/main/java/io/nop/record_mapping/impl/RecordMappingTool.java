package io.nop.record_mapping.impl;

import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.DictOptionBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.validate.IValidationErrorCollector;
import io.nop.commons.collections.IKeyedList;
import io.nop.commons.util.StringHelper;
import io.nop.core.dict.DictProvider;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.type.IGenericType;
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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static io.nop.record_mapping.RecordMappingErrors.ARG_DICT;
import static io.nop.record_mapping.RecordMappingErrors.ARG_FIELD_NAME;
import static io.nop.record_mapping.RecordMappingErrors.ARG_KEY_PROP;
import static io.nop.record_mapping.RecordMappingErrors.ARG_KEY_VALUE;
import static io.nop.record_mapping.RecordMappingErrors.ARG_MAPPING_NAME;
import static io.nop.record_mapping.RecordMappingErrors.ARG_SOURCE_LOC;
import static io.nop.record_mapping.RecordMappingErrors.ARG_VALUE;
import static io.nop.record_mapping.RecordMappingErrors.ERR_RECORD_FIELD_IS_MANDATORY;
import static io.nop.record_mapping.RecordMappingErrors.ERR_RECORD_FIELD_VALUE_NOT_IN_DICT;
import static io.nop.record_mapping.RecordMappingErrors.ERR_RECORD_LIST_DUPLICATE_ITEM;

/**
 * 记录映射核心操作辅助类
 * 提供通用的字段处理、验证、对象创建等核心操作
 */
public class RecordMappingTool {
    private static final Logger LOG = LoggerFactory.getLogger(RecordMappingTool.class);
    public static final RecordMappingTool DEFAULT = new RecordMappingTool();

    // ========== 字段条件检查 ==========
    public boolean checkFieldCondition(RecordFieldMappingConfig field,
                                       Object source, Object target,
                                       RecordMappingContext ctx) {
        if (field.getWhen() != null) {
            boolean b = ConvertHelper.toTruthy(field.getWhen().call3(null, source, target, ctx, ctx.getEvalScope()));
            if (!b) {
                LOG.debug("nop.record.map-field-not-match-condition:from={},to={},loc={},source={}",
                        field.getFrom(), field.getName(), field.getLocation(), source);
                return false;
            }
        }
        return true;
    }

    // ========== 回调执行 ==========
    public void executeForEachField(RecordMappingConfig mapping,
                                    Object source, Object target,
                                    RecordMappingContext ctx, Consumer<RecordFieldMappingConfig> action) {
        executeForEachField0(mapping, source, target, ctx, field -> {
            executeForField(mapping, field, source, target, ctx, action);
        });
    }

    public void executeForObject(RecordMappingConfig mapping,
                                 Object source, Object target,
                                 RecordMappingContext ctx, Runnable action) {
        Object sourceParent = ctx.getSourceParent();
        Object targetParent = ctx.getTargetParent();

        ctx.setSourceParent(source);
        ctx.setTargetParent(target);

        if (ctx.getSourceRoot() == null) {
            ctx.setSourceRoot(source);
            ctx.setTargetRoot(target);
        }

        if (mapping.getBeforeMapping() != null)
            mapping.getBeforeMapping().call3(null, source, target, ctx, ctx.getEvalScope());

        action.run();

        if (mapping.getAfterMapping() != null)
            mapping.getAfterMapping().call3(null, source, target, ctx, ctx.getEvalScope());

        ctx.setSourceParent(sourceParent);
        ctx.setTargetParent(targetParent);
    }

    protected void executeForEachField0(RecordMappingConfig mapping,
                                        Object source, Object target,
                                        RecordMappingContext ctx, Consumer<RecordFieldMappingConfig> action) {
        executeForObject(mapping, source, target, ctx, () -> {
            for (RecordFieldMappingConfig field : mapping.getFields()) {
                action.accept(field);
            }
        });
    }

    public void executeForField(RecordMappingConfig mapping, RecordFieldMappingConfig field,
                                Object source, Object target, RecordMappingContext ctx,
                                Consumer<RecordFieldMappingConfig> action) {
        if (!this.checkFieldCondition(field, source, target, ctx))
            return;

        try {
            if (field.getBeforeFieldMapping() != null) {
                field.getBeforeFieldMapping().call3(null, source, target, ctx, ctx.getEvalScope());
            }

            action.accept(field);

            if (field.getAfterFieldMapping() != null) {
                field.getAfterFieldMapping().call3(null, source, target, ctx, ctx.getEvalScope());
            }
        } catch (NopException e) {
            fillStdErrorInfo(e, mapping, field, source, ctx);
            throw e;
        }
    }

    // ========== 值获取和处理 ==========
    public Object getFromValue(RecordFieldMappingConfig field, Object source, Object target, RecordMappingContext ctx) {
        Object value = getFromValue0(field, source, target, ctx);
        if (field.getValueExpr() != null) {
            value = field.getValueExpr().call2(null, value, ctx, ctx.getEvalScope());
        }
        return value;
    }

    protected Object getFromValue0(RecordFieldMappingConfig field,
                                   Object source, Object target,
                                   RecordMappingContext ctx) {
        if (field.getComputeExpr() != null)
            return field.getComputeExpr().call3(null, source, target, ctx, ctx.getEvalScope());

        if (field.getFrom() != null) {
            Object value = getSourceValue(field, source, field.getFrom(), ctx);
            if (value != null) {
                return value;
            }
            if (field.getAlias() != null) {
                for (String alias : field.getAlias()) {
                    value = getSourceValue(field, source, alias, ctx);
                    if (value != null)
                        return value;
                }
            }
        }

        return null;
    }

    /**
     * 完整的值处理流程：值映射 → 类型转换 → 验证 → 必填检查
     */
    public Object processFieldValue(RecordMappingConfig mapping,
                                    RecordFieldMappingConfig field,
                                    Object value,
                                    RecordMappingContext ctx) {
        // 1. 对源对象返回的值进行映射，如果为null，则返回defaultValue
        value = applyValueMapper(field, value);

        // 2. 类型转换
        value = castType(field, value);

        // 3. 验证
        validateValue(mapping, field, value, ctx);

        // 4. 必填检查
        validateMandatoryField(mapping.getName(), field, value);

        return value;
    }

    public Object applyValueMapper(RecordFieldMappingConfig field, Object value) {
        if (value == null)
            return field.getDefaultValue();

        if (field.getValueMapper() != null) {
            value = field.getValueMapper().mapValue(value.toString());
        }
        return value;
    }

    public Object getSourceValue(RecordFieldMappingConfig field,
                                 Object source, String propName,
                                 RecordMappingContext ctx) {
        if (field.isOptional()) {
            return BeanTool.tryGetComplexProperty(source, propName);
        }
        return BeanTool.getComplexProperty(source, propName);
    }

    public void setTargetValue(RecordFieldMappingConfig field,
                               Object target, Object value,
                               RecordMappingContext ctx) {
        BeanTool.setComplexProperty(target, field.getName(), value);
    }

    // ========== 对象创建 ==========

    public Object makeTargetObject(RecordFieldMappingConfig field,
                                   Object source, Object target,
                                   RecordMappingContext ctx) {
        Supplier<Object> constructor = field.getObjectConstructor(false, source, target, ctx);
        Object toValue = BeanTool.makeComplexProperty(target, field.getName(), constructor);
        if (toValue == null) {
            toValue = new LinkedHashMap<>();
            BeanTool.setComplexProperty(target, field.getName(), toValue);
        }
        return toValue;
    }

    public Object makeMapValue(RecordFieldMappingConfig field, Object value,
                               Object toValue, String key,
                               RecordMappingContext ctx) {
        Object toItemValue = BeanTool.instance().makeProperty(toValue, key, ctx.getEvalScope());
        if (toItemValue == null) {
            toItemValue = field.getItemConstructor(value, toValue, ctx).get();
            BeanTool.setProperty(toValue, key, toItemValue);
        }
        return toItemValue;
    }

    public Object makeTargetCollection(RecordFieldMappingConfig field,
                                       Object source, Object target,
                                       RecordMappingContext ctx) {
        Supplier<Object> constructor = field.getObjectConstructor(true, source, target, ctx);
        Object toValue = BeanTool.makeComplexProperty(target, field.getName(), constructor);
        if (toValue == null) {
            toValue = new ArrayList<>();
            BeanTool.setComplexProperty(target, field.getName(), toValue);
        }
        return toValue;
    }

    public Object makeCollectionItem(RecordFieldMappingConfig field,
                                     Object itemValue,
                                     Object toValue, RecordMappingContext ctx) {
        Object toItemValue = field.getItemConstructor(itemValue, null, ctx).get();
        if (toValue instanceof IKeyedList) {
            ((IKeyedList) toValue).addUnique(toItemValue, key ->
                    new NopException(ERR_RECORD_LIST_DUPLICATE_ITEM).source(field)
                            .param(ARG_KEY_PROP, field.getKeyProp()).param(ARG_KEY_VALUE, key));
        } else {
            ((Collection<Object>) toValue).add(toItemValue);
        }
        return toItemValue;
    }

    public void mapMapField(RecordFieldMappingConfig field, java.util.Map<String, Object> value,
                            Object source, Object target, RecordMappingContext ctx,
                            BiConsumer<Object, Object> action) {
        Object toValue = makeTargetObject(field, source, target, ctx);

        for (java.util.Map.Entry<String, Object> entry : value.entrySet()) {
            String key = entry.getKey();
            Object itemValue = entry.getValue();

            if (!passItemFilter(field, itemValue, key, ctx)) {
                continue;
            }

            Object toItemValue = makeMapValue(field, value, toValue, key, ctx);
            action.accept(itemValue, toItemValue);
        }
    }

    public void mapCollectionField(RecordFieldMappingConfig field, Collection<?> items,
                                   Object source, Object target,
                                   RecordMappingContext ctx,
                                   BiConsumer<Object, Object> action) {
        if (items == null) return;

        Object toValue = makeTargetCollection(field, source, target, ctx);

        int index = 0;

        for (Object item : items) {
            if (!passItemFilter(field, item, index, ctx)) {
                index++;
                continue;
            }

            Object itemValue = makeCollectionItem(field, item, toValue, ctx);
            action.accept(item, itemValue);
            index++;
        }
    }

    // ========== 验证相关 ==========
    public Object castType(RecordFieldMappingConfig field, Object value) {
        IGenericType type = field.getType();
        if (type != null)
            value = BeanTool.castBeanToType(value, type);
        return value;
    }

    public void validateValue(RecordMappingConfig mapping,
                              RecordFieldMappingConfig field, Object value,
                              RecordMappingContext ctx) {
        String mappingName = mapping.getName();
        ISchema schema = field.getSchema();
        // 验证值满足schema要求
        if (schema == null || ctx.isSkipValidation()) {
            return;
        }

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

        validateDictValue(schema, mappingName, field, fieldName, value, ctx);
    }

    protected void validateDictValue(ISchema schema, String mappingName,
                                     RecordFieldMappingConfig field, String fieldName,
                                     Object value, RecordMappingContext ctx) {
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
                        .param(ARG_MAPPING_NAME, mappingName)
                        .param(ARG_FIELD_NAME, fieldName).param(ARG_VALUE, value);
            }
        }
    }

    public void validateMandatoryField(String mappingName,
                                       RecordFieldMappingConfig field,
                                       Object value) {
        if (StringHelper.isEmptyObject(value) && field.isMandatory()) {
            throw new NopException(ERR_RECORD_FIELD_IS_MANDATORY).source(field)
                    .param(ARG_FIELD_NAME, field.getName())
                    .param(ARG_MAPPING_NAME, mappingName);
        }
    }

    // ========== 错误处理 ==========

    public void fillStdErrorInfo(NopException e, RecordMappingConfig mapping,
                                 RecordFieldMappingConfig field,
                                 Object source, RecordMappingContext ctx) {
        if (e.getErrorLocation() == null) {
            e.loc(field.getLocation());
        }

        if (e.getParam(ARG_SOURCE_LOC) == null) {
            SourceLocation loc = SourceLocation.getLocation(source);
            if (loc != null)
                e.param(ARG_SOURCE_LOC, loc);
        }

        if (e.getParam(ARG_FIELD_NAME) == null) {
            e.param(ARG_FIELD_NAME, field.getName());
        }

        if (e.getParam(ARG_MAPPING_NAME) == null) {
            e.param(ARG_MAPPING_NAME, mapping.getName());
        }
    }

    // ========== 集合操作 ==========

    public boolean passItemFilter(RecordFieldMappingConfig field,
                                  Object itemValue, Object keyOrIndex,
                                  RecordMappingContext ctx) {
        if (field.getItemFilterExpr() != null) {
            return ConvertHelper.toTruthy(field.getItemFilterExpr().call3(null, itemValue, keyOrIndex, ctx, ctx.getEvalScope()));
        }
        return true;
    }

    // ========== 类型判断 ==========

    public boolean isCollectionLikeField(RecordFieldMappingConfig field, Object value) {
        return field.getItemMapping() != null &&
                value != null &&
                (value instanceof Map || value instanceof Collection);
    }
}