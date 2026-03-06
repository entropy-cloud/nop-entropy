package io.nop.record_mapping.impl;

import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.DictOptionBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.validate.IValidationErrorCollector;
import io.nop.commons.collections.IKeyedList;
import io.nop.commons.path.ICompiledPathMatcher;
import io.nop.commons.util.StringHelper;
import io.nop.core.dict.DictProvider;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.core.type.IGenericType;
import io.nop.record_mapping.RecordMappingContext;
import io.nop.record_mapping.model.IRecordFieldMappingConfig;
import io.nop.record_mapping.model.RecordFieldMappingConfig;
import io.nop.record_mapping.model.RecordMappingConfig;
import io.nop.record_mapping.model.RecordPatternFieldConfig;
import io.nop.xlang.api.EvalCode;
import io.nop.xlang.api.ExprEvalAction;
import io.nop.xlang.api.source.IWithSourceCode;
import io.nop.xlang.xdef.IStdDomainHandler;
import io.nop.xlang.xdef.domain.StdDomainRegistry;
import io.nop.xlang.xmeta.ISchema;
import io.nop.xlang.xmeta.SimpleSchemaValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static io.nop.record_mapping.RecordMappingConstants.*;
import static io.nop.record_mapping.RecordMappingErrors.*;

/**
 * 记录映射核心操作辅助类
 * 提供通用的字段处理、验证、对象创建等核心操作
 */
public class RecordMappingTool {
    private static final Logger LOG = LoggerFactory.getLogger(RecordMappingTool.class);
    public static final RecordMappingTool DEFAULT = new RecordMappingTool();
    private static final io.nop.commons.path.IPathMatcher PATH_MATCHER = new io.nop.commons.path.AntPathMatcher();
    private static final java.util.Map<String, io.nop.commons.path.IPathMatcher> PATTERN_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

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
            java.util.Set<String> processedFields = new java.util.HashSet<>();

            for (RecordFieldMappingConfig field : mapping.getFields()) {
                action.accept(field);
                if (field.getFrom() != null) {
                    processedFields.add(field.getFrom());
                }
            }

            if (!mapping.getPatternFields().isEmpty()) {
                Set<String> fieldNames = getAllFieldNames(source);
                processPatternFields(mapping, source, target, ctx, action, fieldNames, processedFields);
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
    public Object getProcessedFromValue(RecordMappingConfig mapping, RecordFieldMappingConfig field,
                                        Object source, Object target, RecordMappingContext ctx) {
        Object value = getFromValue(field, source, target, ctx);
        return processFieldValue(mapping, field, field.getName(), value, ctx);
    }

    public Object getFromValue(RecordFieldMappingConfig field,
                               Object source, Object target,
                               RecordMappingContext ctx) {
        if (field.getComputeExpr() != null)
            return field.getComputeExpr().call3(null, source, target, ctx, ctx.getEvalScope());

        if (field.isFlattenFrom()) {
            return FlattenListProcessor.instance().parseFromFlattenObj(source, field.getFromOrName(),
                    field.isDisableFromPropPath());
        }

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
                                    IRecordFieldMappingConfig field,
                                    String fieldName,
                                    Object value,
                                    RecordMappingContext ctx) {
        if (field.getValueExpr() != null) {
            value = field.getValueExpr().call2(null, value, ctx, ctx.getEvalScope());
        }

        // 1. 对源对象返回的值进行映射，如果为null，则返回defaultValue
        value = applyValueMapper(field, fieldName, value);

        if (!ctx.isSkipValidation()) {
            // 2. 类型转换
            value = castType(field, fieldName, value, ctx);

            // 3. 验证
            validateValue(mapping, field, fieldName, value, ctx);

            // 4. 必填检查
            validateMandatoryField(mapping.getName(), field, fieldName, value);
        }

        return value;
    }

    public Object applyValueMapper(IRecordFieldMappingConfig field, String fieldName, Object value) {
        if (value == null)
            return field.getNormalizedDefaultValue();

        if (field.getValueMapper() != null) {
            value = field.getValueMapper().mapValue(value.toString());
        }
        return value;
    }

    public Object getSourceValue(RecordFieldMappingConfig field,
                                 Object source, String propName,
                                 RecordMappingContext ctx) {
        if (field.isDisableFromPropPath()) {
            if (field.isOptional())
                return BeanTool.tryGetProperty(source, propName);
            return BeanTool.getProperty(source, propName);
        } else {
            if (field.isOptional()) {
                return BeanTool.tryGetComplexProperty(source, propName);
            }

            return BeanTool.getComplexProperty(source, propName);
        }
    }

    public void setTargetValue(IRecordFieldMappingConfig field,
                               Object target, String fieldName, Object value,
                               RecordMappingContext ctx) {
        if (field.getVarName() != null) {
            ctx.setValue(field.getVarName(), value);
            return;
        }
        if (field.isDisableToPropPath()) {
            BeanTool.setProperty(target, fieldName, value);
        } else {
            BeanTool.setComplexProperty(target, fieldName, value);
        }
    }

    // ========== 对象创建 ==========

    public Object makeTargetObject(RecordFieldMappingConfig field,
                                   Object source, Object target,
                                   RecordMappingContext ctx) {
        Supplier<Object> constructor = field.getObjectConstructor(false, source, target, ctx);
        if (field.getVarName() != null) {
            Object toValue = constructor.get();
            ctx.setValue(field.getVarName(), toValue);
            return toValue;
        }

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

    public Collection<?> makeTargetCollection(RecordFieldMappingConfig field,
                                              Object source, Object target,
                                              RecordMappingContext ctx) {
        Supplier<Object> constructor = field.getObjectConstructor(true, source, target, ctx);
        if (field.getVarName() != null) {
            Object toItemValue = constructor.get();
            ctx.setValue(field.getVarName(), toItemValue);
            return (Collection<?>) toItemValue;
        }

        if (field.isFlattenTo()) {
            return (Collection<?>) constructor.get();
        }

        Collection<?> toValue = (Collection<?>) BeanTool.makeComplexProperty(target, field.getName(), constructor);
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
        return toItemValue;
    }

    public void addToList(RecordFieldMappingConfig field, Object coll, Object value) {
        if (coll instanceof IKeyedList) {
            ((IKeyedList) coll).addUnique(value, key ->
                    new NopException(ERR_RECORD_LIST_DUPLICATE_ITEM).source(field)
                            .param(ARG_KEY_PROP, field.getKeyProp()).param(ARG_KEY_VALUE, key));
        } else {
            ((Collection<Object>) coll).add(value);
        }
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

    public Object mapCollectionField(RecordFieldMappingConfig field, Collection<?> items,
                                     Object source, Object target,
                                     RecordMappingContext ctx,
                                     BiConsumer<Object, Object> action) {
        if (items == null) return null;

        Collection<?> toValue = makeTargetCollection(field, source, target, ctx);

        int index = 0;

        for (Object item : items) {
            if (!passItemFilter(field, item, index, ctx)) {
                index++;
                continue;
            }

            Object itemValue = makeCollectionItem(field, item, toValue, ctx);
            action.accept(item, itemValue);
            addToList(field, toValue, itemValue);
            index++;
        }

        if (field.isFlattenTo()) {
            FlattenListProcessor.instance().generateFlattenObj(source, toValue, field.getName(), field.isDisableToPropPath(),
                    row -> getRowValues(row, field.getResolvedItemMapping()));
        }
        return toValue;
    }

    protected Map<String, Object> getRowValues(Object row, RecordMappingConfig itemMapping) {
        if (row instanceof Map)
            return (Map<String, Object>) row;

        Map<String, Object> ret = new LinkedHashMap<>();
        for (RecordFieldMappingConfig field : itemMapping.getFields()) {
            String name = field.getName();
            Object value = BeanTool.getProperty(row, name);
            ret.put(name, value);
        }
        return ret;
    }

    // ========== 验证相关 ==========
    public Object castType(IRecordFieldMappingConfig field, String fieldName, Object value, RecordMappingContext ctx) {
        if (StringHelper.isEmptyObject(value))
            return null;

        if (field.getSchema() != null) {
            String stdDomain = field.getSchema().getStdDomain();
            if (stdDomain != null) {
                IStdDomainHandler handler = StdDomainRegistry.instance().requireStdDomainHandler(field.getLocation(), stdDomain);
                Object source = value;
                value = handler.parseProp(field.getSchema().getStdDomainOptions(), field.getLocation(), fieldName, value, ctx.makeCompileTool());
                if (value instanceof ExprEvalAction && !(value instanceof IWithSourceCode)) {
                    value = EvalCode.addSource((ExprEvalAction) value, source.toString());
                }
                return value;
            }
        }

        IGenericType type = field.getType();
        if (type == null) {
            if (field.getSchema() != null)
                type = field.getSchema().getType();
        }
        if (type != null)
            value = BeanTool.castBeanToType(value, type);
        return value;
    }

    public void validateValue(RecordMappingConfig mapping,
                              IRecordFieldMappingConfig field, String fieldName, Object value,
                              RecordMappingContext ctx) {
        String mappingName = mapping.getName();
        ISchema schema = field.getSchema();
        // 验证值满足schema要求
        if (schema == null) {
            return;
        }

        IGenericType type = field.getType();
        if (type == null)
            type = schema.getType();


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
                                     IRecordFieldMappingConfig field, String fieldName,
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
                                       IRecordFieldMappingConfig field,
                                       String fieldName,
                                       Object value) {
        if (StringHelper.isEmptyObject(value) && field.isMandatory()) {
            throw new NopException(ERR_RECORD_FIELD_IS_MANDATORY).source(field)
                    .param(ARG_FIELD_NAME, fieldName)
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
                (value instanceof Map || value instanceof Collection);
    }

    protected void processPatternFields(RecordMappingConfig mapping,
                                        Object source, Object target,
                                        RecordMappingContext ctx,
                                        Consumer<RecordFieldMappingConfig> action,
                                        Set<String> fieldNames,
                                        java.util.Set<String> processedFields) {

        for (RecordPatternFieldConfig patternField : mapping.getPatternFields()) {
            ICompiledPathMatcher matcher = patternField.getCompiledPattern();
            if(matcher == null){
                //  不需要匹配来源，完全是动态生成
                String targetFieldName = evaluateToExpression(patternField.getTo(), patternField.getCompiledPattern(), null, source, target, ctx);
                if (targetFieldName == null) {
                    continue;
                }

                ctx.setValue(VAR_SOURCE_FIELD_NAME, null);
                ctx.setValue(VAR_TARGET_FIELD_NAME, targetFieldName);

                RecordFieldMappingConfig fieldConfig = createFieldConfigFromPattern(patternField, targetFieldName, null);
                action.accept(fieldConfig);
                continue;
            }

            for (String fieldName : fieldNames) {
                if (processedFields.contains(fieldName)) {
                    continue;
                }

                if (matcher.match(fieldName)) {
                    if (patternField.isIgnore()) {
                        processedFields.add(fieldName);
                        continue;
                    }

                    String targetFieldName = evaluateToExpression(patternField.getTo(), patternField.getCompiledPattern(), fieldName, source, target, ctx);
                    if (targetFieldName == null) {
                        continue;
                    }

                    ctx.setValue(VAR_SOURCE_FIELD_NAME, fieldName);
                    ctx.setValue(VAR_TARGET_FIELD_NAME, targetFieldName);

                    RecordFieldMappingConfig fieldConfig = createFieldConfigFromPattern(patternField, targetFieldName, fieldName);
                    action.accept(fieldConfig);
                    processedFields.add(fieldName);
                }
            }
        }
    }


    protected String evaluateToExpression(io.nop.core.lang.eval.IEvalAction toExpr,
                                          ICompiledPathMatcher patternExpr,
                                          String fieldName,
                                          Object source,
                                          Object target,
                                          RecordMappingContext ctx) {
        if (toExpr == null) {
            return null;
        }

        java.util.Map<String, String> vars = extractVariablesFromPattern(patternExpr, fieldName);
        if (vars != null) {
            for (java.util.Map.Entry<String, String> entry : vars.entrySet()) {
                ctx.setValue(entry.getKey(), entry.getValue());
            }
        }
        ctx.setValue(VAR_SOURCE, source);
        ctx.setValue(VAR_TARGET, target);

        return ConvertHelper.toString(toExpr.invoke(ctx));
    }

    protected java.util.Map<String, String> extractVariablesFromPattern(ICompiledPathMatcher matcher, String fieldName) {
        if (matcher == null)
            return null;

        return matcher.extractUriTemplateVariables(fieldName);
    }

    protected Set<String> getAllFieldNames(Object source) {
        if (source instanceof java.util.Map) {
            return ((java.util.Map<String, Object>) source).keySet();
        }

        Set<String> ret = new LinkedHashSet<>();

        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(source.getClass());
        beanModel.forEachReadableProp(prop -> {
            ret.add(prop.getName());
        });

        Set<String> propNames = beanModel.getExtPropertyNames(source);
        if (propNames != null) {
            ret.addAll(propNames);
        }

        return ret;
    }

    protected RecordFieldMappingConfig createFieldConfigFromPattern(RecordPatternFieldConfig patternField,
                                                                    String targetFieldName,
                                                                    String sourceFieldName) {
        RecordFieldMappingConfig fieldConfig = new RecordFieldMappingConfig();
        fieldConfig.setName(targetFieldName);
        fieldConfig.setFrom(sourceFieldName);
        fieldConfig.setComputeExpr(patternField.getComputeExpr());
        fieldConfig.setValueExpr(patternField.getValueExpr());
        fieldConfig.setWhen(patternField.getWhen());
        fieldConfig.setSchema(patternField.getSchema());
        fieldConfig.setType(patternField.getType());
        fieldConfig.setMapping(patternField.getMapping());
        fieldConfig.setItemMapping(patternField.getItemMapping());
        fieldConfig.setMandatory(patternField.isMandatory());
        fieldConfig.setAfterFieldMapping(patternField.getAfterFieldMapping());
        fieldConfig.setBeforeFieldMapping(patternField.getBeforeFieldMapping());
        fieldConfig.setValueMapper(patternField.getValueMapper());
        fieldConfig.setResolvedItemMapping(patternField.getResolvedItemMapping());
        fieldConfig.setResolvedMapping(patternField.getResolvedMapping());
        fieldConfig.setClassModel(patternField.getClassModel());
        fieldConfig.setItemClassModel(patternField.getItemClassModel());
        fieldConfig.setDisableFromPropPath(patternField.isDisableFromPropPath());
        fieldConfig.setDisableToPropPath(patternField.isDisableToPropPath());
        fieldConfig.setFlattenFrom(patternField.isFlattenFrom());
        fieldConfig.setFlattenTo(patternField.isFlattenTo());
        fieldConfig.setIgnoreWhenEmpty(patternField.isIgnoreWhenEmpty());
        fieldConfig.setItemFilterExpr(patternField.getItemFilterExpr());
        fieldConfig.setKeyProp(patternField.getKeyProp());
        fieldConfig.setNewInstanceExpr(patternField.getNewInstanceExpr());
        fieldConfig.setNewItemExpr(patternField.getNewItemExpr());
        fieldConfig.setOptional(patternField.isOptional());
        fieldConfig.setVarName(patternField.getVarName());
        fieldConfig.setVirtual(patternField.isVirtual());
        return fieldConfig;
    }

}