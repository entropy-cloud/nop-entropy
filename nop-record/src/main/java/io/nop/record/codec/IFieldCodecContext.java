package io.nop.record.codec;

import io.nop.api.core.util.IVariableScope;
import io.nop.core.context.IEvalContext;
import io.nop.record.model.RecordFieldMeta;
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

    default void setValue(String name, Object value){
        getEvalScope().setLocalValue(name,value);
    }

    String getFieldPath();

    void enterField(RecordFieldMeta field);

    void exitField(RecordFieldMeta field);

    RecordFieldMeta getCurrentField();

    RecordTypeMeta getType(String name);
}
