package io.nop.record.codec;

import io.nop.core.context.IEvalContext;

public interface IFieldCodecContext extends IEvalContext {

    String getFieldPath();

    void enterField(String name);

    void leaveField(String name);
}
