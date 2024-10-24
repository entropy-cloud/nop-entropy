package io.nop.record.codec.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldBinaryEncoder;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.reader.IRecordBinaryReader;
import io.nop.record.writer.IRecordBinaryWriter;

import java.nio.charset.Charset;
import java.util.function.Function;

import static io.nop.record.RecordErrors.ARG_LENGTH;
import static io.nop.record.RecordErrors.ARG_MAX_LENGTH;
import static io.nop.record.RecordErrors.ERR_RECORD_DECODE_LENGTH_IS_TOO_LONG;

public class DynLVFieldBinaryCodec implements IFieldBinaryCodec {
    private final IFieldBinaryCodec lengthCodec;
    private final Function<Object, Integer> lengthGetter;

    public DynLVFieldBinaryCodec(IFieldBinaryCodec lengthCodec, Function<Object, Integer> lengthGetter) {
        this.lengthCodec = lengthCodec;
        this.lengthGetter = lengthGetter;
    }

    @Override
    public Object decode(IRecordBinaryReader input, int length, Charset charset, IFieldCodecContext context) {
        int len = (Integer) lengthCodec.decode(input, length, charset, context);
        if (len <= 0) {
            return null;
        }

        if (length > 0 && len >= length) {
            throw new NopException(ERR_RECORD_DECODE_LENGTH_IS_TOO_LONG)
                    .param(ARG_LENGTH, len).param(ARG_MAX_LENGTH, length);
        }
        return null;
    }

    @Override
    public void encode(IRecordBinaryWriter output, Object value, int length, Charset charset, IFieldCodecContext context, IFieldBinaryEncoder bodyEncoder) {
        int len = lengthGetter.apply(value);
        lengthCodec.encode(output, len, length, charset, context, null);
        if (len > 0)
            bodyEncoder.encode(output, value, len, charset, context, null);
    }
}
