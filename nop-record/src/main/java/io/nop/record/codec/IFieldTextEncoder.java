package io.nop.record.codec;

import io.nop.record.output.IRecordTextOutput;

public interface IFieldTextEncoder {
    void encode(IRecordTextOutput output, Object value, int length, IFieldCodecContext context, IFieldTextEncoder bodyEncoder);
}
