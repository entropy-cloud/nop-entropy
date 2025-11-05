package io.nop.record.serialization;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.record.codec.IFieldCodecContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RecordSerializeHelper {
    static final Logger LOG = LoggerFactory.getLogger(RecordSerializeHelper.class);

    public static boolean runIfExpr(IEvalFunction expr, Object record, String name, IFieldCodecContext context) {
        if (expr == null)
            return true;
        if (!ConvertHelper.toPrimitiveBoolean(expr.call2(null, record, context, context.getEvalScope()))) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("nop.record.skip-field:fieldPath={},name={}", context.getFieldPath(), name);
            }
            return false;
        }
        return true;
    }
}
