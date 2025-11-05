package io.nop.record.template;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.validate.IValidationErrorCollector;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.record.model.RecordTemplateFieldModel;
import io.nop.record.model.RecordTemplateModel;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xmeta.SimpleSchemaValidator;

import java.util.HashMap;
import java.util.Map;

import static io.nop.record.RecordErrors.ARG_FIELD_NAME;
import static io.nop.record.RecordErrors.ERR_RECORD_FIELD_IS_MANDATORY;

public class RecordTemplateManager {
    public RecordTemplateModel getRecordTemplate(String path) {
        return (RecordTemplateModel) ResourceComponentManager.instance().loadComponentModel(path);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> buildRecord(RecordTemplateModel tpl, Map<String, Object> vars) {
        IEvalScope scope = prepareVars(tpl, vars);
        String text = tpl.getTemplate().generateText(scope);
        return (Map<String, Object>) JsonTool.parseNonStrict(text);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> buildRecordWithGenerator(RecordTemplateModel tpl, Map<String, Object> vars) {
        IEvalScope scope = prepareVars(tpl, vars);
        for (RecordTemplateFieldModel field : tpl.getFields()) {
            if (field.getGenerator() != null) {
                Object value = field.getGenerator().invoke(scope);
                scope.setLocalValue(field.getName(), value);
            }
        }

        String text = tpl.getTemplate().generateText(scope);
        Map<String, Object> record = (Map<String, Object>) JsonTool.parseNonStrict(text);

        if (tpl.getGenerator() != null) {
            Object value = tpl.getGenerator().invoke(scope);
            if (value instanceof Map) {
                record.putAll((Map<String, Object>) value);
            }
        }
        return record;
    }

    private IEvalScope prepareVars(RecordTemplateModel tpl, Map<String, Object> vars) {
        if (vars == null)
            vars = new HashMap<>();

        IEvalScope scope = XLang.newEvalScope(vars);

        for (RecordTemplateFieldModel field : tpl.getFields()) {
            if (field.isOptional()) {
                if (!vars.containsKey(field.getName())) {
                    continue;
                }
            }

            Object value = vars.get(field.getName());
            if (field.isMandatory()) {
                if (value == null)
                    value = field.getDefaultValue();
                if (StringHelper.isEmptyObject(value))
                    throw new NopException(ERR_RECORD_FIELD_IS_MANDATORY).param(ARG_FIELD_NAME, field.getName());
            }

            value = checkValue(value, field, scope);
            scope.setLocalValue(field.getName(), value);
        }
        return scope;
    }

    private Object checkValue(Object value, RecordTemplateFieldModel field, IEvalScope scope) {
        if (value == null)
            return null;
        if (field.getType() != null)
            value = field.getType().getStdDataType().convert(value, err -> new NopException(err).param(ARG_FIELD_NAME, field.getName()));
        if (field.getSchema() != null)
            SimpleSchemaValidator.INSTANCE.validate(field.getSchema(), null, field.getTemplateName(), field.getName(),
                    value, scope, IValidationErrorCollector.THROW_ERROR);
        return value;
    }
}
