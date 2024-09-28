package io.nop.record.codec;

import io.nop.record.output.IRecordBinaryOutput;

import java.nio.charset.Charset;

public interface IFieldBinaryEncoder {

    void encode(IRecordBinaryOutput output, Object value, int length, Charset charset,
                IFieldCodecContext context, IFieldBinaryEncoder bodyEncoder);
}
