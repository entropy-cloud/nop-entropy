package io.nop.record.codec;

import io.nop.api.core.util.IVariableScope;
import io.nop.core.context.IEvalContext;
import io.nop.record.model.RecordTypeMeta;

public interface IFieldCodecContext extends IEvalContext, IVariableScope {

    @Override
    default Object getValueByPropPath(String propPath) {
        return getEvalScope().getValueByPropPath(propPath);
    }

    @Override
    default Object getValue(String name) {
        return getEvalScope().getValue(name);
    }

    String getFieldPath();

    void enterField(String name);

    void leaveField(String name);

    RecordTypeMeta getType(String name);
}
