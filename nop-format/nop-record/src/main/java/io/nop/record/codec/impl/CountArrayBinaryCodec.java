package io.nop.record.codec.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.reader.IBinaryDataReader;
import io.nop.record.serialization.IModelBasedBinaryRecordDeserializer;
import io.nop.record.serialization.IModelBasedBinaryRecordSerializer;
import io.nop.record.writer.IBinaryDataWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static io.nop.record.RecordConstants.DEFAULT_MAX_COLLECTION_SIZE;
import static io.nop.record.RecordErrors.ARG_LENGTH;
import static io.nop.record.RecordErrors.ARG_MAX_LENGTH;
import static io.nop.record.RecordErrors.ERR_RECORD_DECODE_LENGTH_IS_TOO_LONG;

public class CountArrayBinaryCodec implements IFieldBinaryCodec {

    static final int MAX_PREALLOC_SIZE = 1024;

    private final IFieldBinaryCodec countCodec;
    private final IFieldBinaryCodec itemCodec;

    private final int itemLength;

    public CountArrayBinaryCodec(IFieldBinaryCodec countCodec, IFieldBinaryCodec itemCodec, int itemLength) {
        this.countCodec = countCodec;
        this.itemCodec = itemCodec;
        this.itemLength = itemLength;
    }

    @Override
    public Object decode(IBinaryDataReader input, Object record, int length, IFieldCodecContext context,
                         IModelBasedBinaryRecordDeserializer deserializer) throws IOException {
        int count = (Integer) countCodec.decode(input, record, length, context, deserializer);
        checkCount(count);

        IBinaryDataReader arrayInput = input.subInput(length);
        List<Object> ret = new ArrayList<>(Math.min(count, MAX_PREALLOC_SIZE));
        try {
            for (int i = 0; i < count; i++) {
                Object item = itemCodec.decode(arrayInput, record, itemLength, context, deserializer);
                ret.add(item);
            }
        } finally {
            // ByteBuf 类 subInput 持有独立引用计数，必须关闭释放
            arrayInput.close();
        }
        return ret;
    }

    static void checkCount(int count) {
        if (count < 0 || count > DEFAULT_MAX_COLLECTION_SIZE)
            throw new NopException(ERR_RECORD_DECODE_LENGTH_IS_TOO_LONG)
                    .param(ARG_LENGTH, count)
                    .param(ARG_MAX_LENGTH, DEFAULT_MAX_COLLECTION_SIZE);
    }

    @Override
    public void encode(IBinaryDataWriter output, Object value, int length,
                       IFieldCodecContext context, IModelBasedBinaryRecordSerializer serializer) throws IOException {
        Collection<Object> list = (Collection<Object>) value;
        if (list == null)
            list = Collections.emptyList();

        countCodec.encode(output, list.size(), -1, context, serializer);
        for (Object item : list) {
            itemCodec.encode(output, item, itemLength, context, serializer);
        }
    }
}
