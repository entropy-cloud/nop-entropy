package io.nop.record.codec;

import io.nop.record.input.IRecordBinaryInput;

import java.nio.charset.Charset;

public interface IFieldBinaryDecoder {

    Object decode(IRecordBinaryInput input, int length, Charset charset, IFieldCodecContext context);

}
