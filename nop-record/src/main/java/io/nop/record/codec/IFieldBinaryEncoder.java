package io.nop.record.codec;

import io.nop.record.writer.IRecordBinaryWriter;

import java.nio.charset.Charset;

public interface IFieldBinaryEncoder {

    void encode(IRecordBinaryWriter output, Object value, int length, Charset charset,
                IFieldCodecContext context, IFieldBinaryEncoder bodyEncoder);
}
