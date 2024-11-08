package io.nop.record.codec;

import io.nop.record.reader.ITextDataReader;

import java.io.IOException;

public interface IFieldTextDecoder {
    Object decode(ITextDataReader input, Object record, int length, IFieldCodecContext context) throws IOException;
}
