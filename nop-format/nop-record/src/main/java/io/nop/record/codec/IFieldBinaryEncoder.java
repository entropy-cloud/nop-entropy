package io.nop.record.codec;

import io.nop.record.serialization.IModelBasedBinaryRecordSerializer;
import io.nop.record.writer.IBinaryDataWriter;

import java.io.IOException;

public interface IFieldBinaryEncoder {

    void encode(IBinaryDataWriter output, Object value, int length,
                IFieldCodecContext context, IModelBasedBinaryRecordSerializer serializer) throws IOException;
}
