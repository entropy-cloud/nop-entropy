package io.nop.dbtool.exp;

import io.nop.api.core.exceptions.NopException;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchProcessor;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.dbtool.exp.config.IFieldConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static io.nop.dbtool.exp.DbToolExpErrors.ARG_FIELD_NAME;

public class FieldsProcessor implements IBatchProcessor<Map<String, Object>, Map<String, Object>, IBatchChunkContext> {
    private final List<? extends IFieldConfig> fields;

    public FieldsProcessor(List<? extends IFieldConfig> fields) {
        this.fields = fields;
    }

    @Override
    public void process(Map<String, Object> item, Consumer<Map<String, Object>> consumer, IBatchChunkContext context) {
        Map<String, Object> ret = new LinkedHashMap<>();
        IEvalScope scope = context.getEvalScope();
        scope.setLocalValue(DbToolExpConstants.VAR_INPUT, item);

        for (IFieldConfig field : fields) {
            Object value = item.get(field.getSourceFieldName());
            if (field.getStdDataType() != null)
                value = field.getStdDataType().convert(value,
                        err -> new NopException(err).source(field).param(ARG_FIELD_NAME, field.getName()));

            if (field.getTransformExpr() != null) {
                scope.setLocalValue(DbToolExpConstants.VAR_VALUE, value);
                value = field.getTransformExpr().invoke(scope);
            }

            value = field.validate(value, scope);
            ret.put(field.getName(), value);
        }
        consumer.accept(ret);
    }
}
