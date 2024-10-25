package io.nop.record.codec;

import io.nop.record.reader.ITextDataReader;

public interface IFieldTextDecoder {
    Object decode(ITextDataReader input, int length, IFieldCodecContext context);
}
