package io.nop.dbtool.exp;

import io.nop.api.core.exceptions.NopException;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchProcessorProvider;
import io.nop.batch.core.IBatchProcessorProvider.IBatchProcessor;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.dbtool.exp.config.IFieldConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static io.nop.dbtool.exp.DbToolExpErrors.ARG_FIELD_NAME;

public class FieldsProcessor implements IBatchProcessorProvider<Map<String, Object>, Map<String, Object>>,
        IBatchProcessor<Map<String, Object>, Map<String, Object>> {
    private final List<? extends IFieldConfig> fields;
    private final IEvalAction transformExpr;

    public FieldsProcessor(List<? extends IFieldConfig> fields, IEvalAction transformExpr) {
        this.fields = fields;
        this.transformExpr = transformExpr;
    }

    @Override
    public IBatchProcessor<Map<String, Object>, Map<String, Object>> setup(IBatchTaskContext taskContext) {
        return this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void process(Map<String, Object> item, Consumer<Map<String, Object>> consumer, IBatchChunkContext context) {
        Map<String, Object> ret = new LinkedHashMap<>();
        IEvalScope scope = context.getEvalScope();
        scope.setLocalValue(DbToolExpConstants.VAR_INPUT, item);
        scope.setLocalValue(DbToolExpConstants.VAR_OUTPUT, ret);

        for (IFieldConfig field : fields) {
            Object value = item.get(field.getSourceFieldName());
            if (value == null)
                continue;

            if (field.getTransformExpr() != null) {
                scope.setLocalValue(DbToolExpConstants.VAR_VALUE, value);
                value = field.getTransformExpr().invoke(scope);
            }

            if (field.getStdDataType() != null)
                value = field.getStdDataType().convert(value,
                        err -> new NopException(err).source(field).param(ARG_FIELD_NAME, field.getName()));

            value = field.validate(value, scope);
            ret.put(field.getName(), value);
        }

        if (transformExpr != null) {
            Object result = transformExpr.invoke(scope);
            if (result instanceof Map)
                ret = (Map<String, Object>) result;
        }

        consumer.accept(ret);
    }
}