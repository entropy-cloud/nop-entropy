package io.nop.record.codec;

import io.nop.record.reader.IRecordTextReader;

public interface IFieldTextDecoder {
    Object decode(IRecordTextReader input, int length, IFieldCodecContext context);
}
