package io.nop.record.codec;

import io.nop.record.reader.IBinaryDataReader;
import io.nop.record.serialization.IModelBasedBinaryRecordDeserializer;

import java.io.IOException;

public interface IFieldBinaryDecoder {

    Object decode(IBinaryDataReader input, Object record, int length, IFieldCodecContext context,
                  IModelBasedBinaryRecordDeserializer deserializer) throws IOException;
}