package io.nop.record.codec;

import io.nop.record.serialization.IModelBasedTextRecordSerializer;
import io.nop.record.writer.ITextDataWriter;

import java.io.IOException;

public interface IFieldTextEncoder {
    void encode(ITextDataWriter output, Object value, int length,
                IFieldCodecContext context, IModelBasedTextRecordSerializer serializer) throws IOException;
}
