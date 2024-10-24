package io.nop.record.codec;

import io.nop.record.writer.IRecordTextWriter;

import java.io.IOException;

public interface IFieldTextEncoder {
    void encode(IRecordTextWriter output, Object value, int length,
                IFieldCodecContext context, IFieldTextEncoder bodyEncoder) throws IOException;
}
