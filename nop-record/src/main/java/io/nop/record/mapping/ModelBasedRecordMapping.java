package io.nop.record.mapping;

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
import io.nop.record.model.RecordFieldMappingConfig;
import io.nop.record.model.RecordMappingConfig;
import io.nop.xlang.xmeta.ISchema;
import io.nop.xlang.xmeta.SimpleSchemaValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.nop.record.RecordErrors.ARG_DICT;
import static io.nop.record.RecordErrors.ARG_FIELD_NAME;
import static io.nop.record.RecordErrors.ARG_VALUE;
import static io.nop.record.RecordErrors.ERR_RECORD_FIELD_IS_MANDATORY;
import static io.nop.record.RecordErrors.ERR_RECORD_FIELD_VALUE_NOT_IN_DICT;

public class ModelBasedRecordMapping implements IRecordMapping {
    static final Logger LOG = LoggerFactory.getLogger(ModelBasedRecordMapping.class);

    private final RecordMappingConfig config;

    public ModelBasedRecordMapping(RecordMappingConfig config) {
        this.config = config;
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

        for (RecordFieldMappingConfig field : mapping.getFieldMappings()) {
            mapField(field, source, target, ctx);
        }

        if (mapping.getAfterMapping() != null)
            mapping.getAfterMapping().call3(null, source, target, ctx, ctx.getEvalScope());
    }

    protected void mapField(RecordFieldMappingConfig field, Object source, Object target, RecordMappingContext ctx) {
        if (field.getWhen() != null) {
            boolean b = ConvertHelper.toTruthy(field.getWhen().call3(null, source, target, ctx, ctx.getEvalScope()));
            if (!b) {
                LOG.debug("nop.record.map-field-not-match-condition:from={},to={},loc={},source={}",
                        field.getFrom(), field.getTo(), field.getLocation(), source);
                return;
            }
        }

        if (field.getResolvedMapping() != null) {
            if (field.isVirtual()) {
                mapObject(field.getResolvedMapping(), source, target, ctx);
            } else {
                Object fromValue = BeanTool.getComplexProperty(source, field.getFrom());
                Object toValue = BeanTool.makeComplexProperty(target, field.getTo());

                Object sourceParent = ctx.getSourceParent();
                Object targetParent = ctx.getTargetParent();
                ctx.setSourceParent(source);
                ctx.setTargetRoot(target);
                mapObject(field.getResolvedMapping(), fromValue, toValue, ctx);
                ctx.setSourceParent(sourceParent);
                ctx.setTargetRoot(targetParent);
            }
        } else {
            // 1. 从源对象上获取值，或者动态计算值
            Object value = getFromValue(field, source, target, ctx);

            // 2. 对源对象返回的值进行映射，如果为null，则返回defaultValue
            value = applyValueMapper(field, value);

            if (value != null) {
                IGenericType type = field.getType();
                if (type != null)
                    value = BeanTool.castBeanToType(value, type);

                ISchema schema = field.getSchema();
                // 验证值满足schema要求
                if (schema != null) {
                    if (type != null) {
                        if (type.getStdDataType().isSimpleType()) {
                            SimpleSchemaValidator.INSTANCE.validate(schema, field.getLocation(), field.getTo(), value,
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
                                    .param(ARG_FIELD_NAME, field.getTo()).param(ARG_VALUE, value);
                        }
                    }
                }
            }

            if (StringHelper.isEmptyObject(value) && field.isMandatory())
                throw new NopException(ERR_RECORD_FIELD_IS_MANDATORY)
                        .param(ARG_FIELD_NAME, field.getTo()).source(field);

            BeanTool.setComplexProperty(target, field.getTo(), value);
        }
    }

    protected Object getFromValue(RecordFieldMappingConfig field, Object source, Object target, RecordMappingContext ctx) {
        if (field.getComputeExpr() != null)
            return field.getComputeExpr().call3(null, source, target, ctx, ctx.getEvalScope());

        if (field.getFrom() != null) {
            return BeanTool.getComplexProperty(source, field.getFrom());
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
