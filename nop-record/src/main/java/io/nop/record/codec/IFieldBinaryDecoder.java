package io.nop.record.codec;

import io.nop.record.reader.IBinaryDataReader;

import java.io.IOException;

public interface IFieldBinaryDecoder {

    Object decode(IBinaryDataReader input, Object record, int length,
                  IFieldCodecContext context) throws IOException;
}
