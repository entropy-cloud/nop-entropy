package io.nop.record.codec;

import io.nop.record.reader.ITextDataReader;

import java.io.IOException;

public interface IFieldTextDecoder {
    Object decode(ITextDataReader input, int length, IFieldCodecContext context) throws IOException;
}
