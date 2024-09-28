package io.nop.record.codec;

import io.nop.record.input.IRecordTextInput;

public interface IFieldTextDecoder {
    Object decode(IRecordTextInput input, int length, IFieldCodecContext context);
}
