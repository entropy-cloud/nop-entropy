package io.nop.record.codec;

import io.nop.record.reader.IRecordBinaryReader;

import java.nio.charset.Charset;

public interface IFieldBinaryDecoder {

    Object decode(IRecordBinaryReader input, int length, Charset charset, IFieldCodecContext context);

}
