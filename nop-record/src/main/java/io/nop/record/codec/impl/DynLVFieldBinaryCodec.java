package io.nop.record.codec.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.reader.IBinaryDataReader;
import io.nop.record.serialization.IModelBasedBinaryRecordDeserializer;
import io.nop.record.serialization.IModelBasedBinaryRecordSerializer;
import io.nop.record.writer.IBinaryDataWriter;

import java.io.IOException;
import java.util.function.Function;

import static io.nop.record.RecordErrors.ARG_LENGTH;
import static io.nop.record.RecordErrors.ARG_MAX_LENGTH;
import static io.nop.record.RecordErrors.ERR_RECORD_DECODE_LENGTH_IS_TOO_LONG;

public class DynLVFieldBinaryCodec implements IFieldBinaryCodec {
    private final IFieldBinaryCodec lengthCodec;
    private final IFieldBinaryCodec valueCodec;
    private final Function<Object, Integer> lengthGetter;

    public DynLVFieldBinaryCodec(IFieldBinaryCodec lengthCodec, IFieldBinaryCodec valueCodec,
                                 Function<Object, Integer> lengthGetter) {
        this.lengthCodec = lengthCodec;
        this.lengthGetter = lengthGetter;
        this.valueCodec = valueCodec;
    }

    @Override
    public Object decode(IBinaryDataReader input, Object record, int length, IFieldCodecContext context,
                         IModelBasedBinaryRecordDeserializer deserializer) throws IOException {
        int len = (Integer) lengthCodec.decode(input, record, length, context, deserializer);
        if (len <= 0) {
            return null;
        }

        if (length > 0 && len >= length) {
            throw new NopException(ERR_RECORD_DECODE_LENGTH_IS_TOO_LONG)
                    .param(ARG_LENGTH, len).param(ARG_MAX_LENGTH, length);
        }

        if (valueCodec == null)
            return len;
        return valueCodec.decode(input, record, len, context, deserializer);
    }

    @Override
    public void encode(IBinaryDataWriter output, Object value, int length,
                       IFieldCodecContext context, IModelBasedBinaryRecordSerializer serializer) throws IOException {
        int len = lengthGetter.apply(value);
        lengthCodec.encode(output, len, length, context, null);
        if (len > 0) {
            if (valueCodec != null) {
                valueCodec.encode(output, value, len, context, null);
                return;
            }

            //serializer.encode(output, value, len, context, null);
        }
    }
}
