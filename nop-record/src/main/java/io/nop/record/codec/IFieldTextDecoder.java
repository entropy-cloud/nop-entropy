package io.nop.record.codec;

import io.nop.record.reader.ITextDataReader;
import io.nop.record.serialization.IModelBasedTextRecordDeserializer;

import java.io.IOException;

public interface IFieldTextDecoder {

    Object decode(ITextDataReader input, Object record, int length, IFieldCodecContext context,
                  IModelBasedTextRecordDeserializer deserializer) throws IOException;
}
