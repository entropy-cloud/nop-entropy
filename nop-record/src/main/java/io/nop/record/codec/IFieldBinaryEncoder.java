package io.nop.record.codec;

import io.nop.record.writer.IBinaryDataWriter;

import java.io.IOException;
import java.nio.charset.Charset;

public interface IFieldBinaryEncoder {

    void encode(IBinaryDataWriter output, Object value, int length,
                IFieldCodecContext context, IFieldBinaryEncoder bodyEncoder) throws IOException;
}
