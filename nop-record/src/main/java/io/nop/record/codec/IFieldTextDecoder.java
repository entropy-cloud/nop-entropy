package io.nop.record.codec;

import io.nop.record.reader.ITextDataReader;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

public interface IFieldTextDecoder {
    Object decode(ITextDataReader input, Object record, int length, IFieldCodecContext context) throws IOException;

    default Object decode(ITextDataReader input, Object record, int length, IFieldCodecContext context,
                          IFieldTextDecoder bodyDecoder) throws IOException {
        throw new UnsupportedEncodingException("decode-with-body-codec");
    }
}
