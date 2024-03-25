package io.nop.record.codec.impl;

import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.input.IRecordBinaryInput;
import io.nop.record.output.IRecordBinaryOutput;

import java.nio.charset.Charset;
import java.util.function.Function;

public class LVFieldBinaryCodec implements IFieldBinaryCodec {
    private final IFieldBinaryCodec lengthCodec;
    private final IFieldBinaryCodec valueCodec;
    private final Function<Object, Integer> lengthGetter;

    public LVFieldBinaryCodec(IFieldBinaryCodec lengthCodec,
                              IFieldBinaryCodec valueCodec, Function<Object, Integer> lengthGetter) {
        this.lengthCodec = lengthCodec;
        this.valueCodec = valueCodec;
        this.lengthGetter = lengthGetter;
    }

    @Override
    public Object decode(IRecordBinaryInput input, int length, Charset charset, IFieldCodecContext context) {
        int len = (int) lengthCodec.decode(input, length, charset, context);
        if (len <= 0) {
            return null;
        }

        if (length > 0 && len >= length) {
            throw new IllegalArgumentException("length is too large");
        }
        return valueCodec.decode(input, len, charset, context);
    }

    @Override
    public void encode(IRecordBinaryOutput output, Object value, int length, Charset charset, IFieldCodecContext context) {
        int len = lengthGetter.apply(value);
        lengthCodec.encode(output, len, length, charset, context);
        if (len > 0)
            valueCodec.encode(output, value, len, charset, context);
    }
}
