package io.nop.record.codec.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldBinaryEncoder;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.reader.IBinaryDataReader;
import io.nop.record.writer.IBinaryDataWriter;

import java.io.IOException;
import java.util.function.Function;

import static io.nop.record.RecordErrors.ARG_LENGTH;
import static io.nop.record.RecordErrors.ARG_MAX_LENGTH;
import static io.nop.record.RecordErrors.ERR_RECORD_DECODE_LENGTH_IS_TOO_LONG;

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
    public Object decode(IBinaryDataReader input, Object record, int length,
                         IFieldCodecContext context) throws IOException {
        int len = (Integer) lengthCodec.decode(input, record, length, context);
        if (len <= 0) {
            return null;
        }

        if (length > 0 && len >= length) {
            throw new NopException(ERR_RECORD_DECODE_LENGTH_IS_TOO_LONG)
                    .param(ARG_LENGTH, len).param(ARG_MAX_LENGTH, length);
        }
        return valueCodec.decode(input, record, len, context);
    }

    @Override
    public void encode(IBinaryDataWriter output, Object value, int length,
                       IFieldCodecContext context, IFieldBinaryEncoder bodyEncoder) throws IOException {
        int len = lengthGetter.apply(value);
        lengthCodec.encode(output, len, length, context, null);
        if (len > 0)
            valueCodec.encode(output, value, len, context, bodyEncoder);
    }
}
