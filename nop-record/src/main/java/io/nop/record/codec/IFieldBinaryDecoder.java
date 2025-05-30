package io.nop.record.codec;

import io.nop.record.reader.IBinaryDataReader;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

public interface IFieldBinaryDecoder {

    Object decode(IBinaryDataReader input, Object record, int length,
                  IFieldCodecContext context) throws IOException;

    default Object decode(IBinaryDataReader input, Object record, int length,
                          IFieldCodecContext context, IFieldBinaryDecoder bodyDecoder) throws IOException {
        throw new UnsupportedEncodingException("decode-with-body-codec");
    }
}