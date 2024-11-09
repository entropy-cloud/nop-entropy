package io.nop.record.codec;

import io.nop.record.reader.IBinaryDataReader;

import java.io.IOException;
import java.nio.charset.Charset;

public interface IFieldBinaryDecoder {

    Object decode(IBinaryDataReader input, Object record, int length,
                  Charset charset, IFieldCodecContext context) throws IOException;
}
