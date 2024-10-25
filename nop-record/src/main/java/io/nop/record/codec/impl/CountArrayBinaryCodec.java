package io.nop.record.codec.impl;

import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldBinaryEncoder;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.reader.IBinaryDataReader;
import io.nop.record.writer.IBinaryDataWriter;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class CountArrayBinaryCodec implements IFieldBinaryCodec {
    private final IFieldBinaryCodec countCodec;
    private final IFieldBinaryCodec itemCodec;

    private final int itemLength;

    public CountArrayBinaryCodec(IFieldBinaryCodec countCodec, IFieldBinaryCodec itemCodec, int itemLength) {
        this.countCodec = countCodec;
        this.itemCodec = itemCodec;
        this.itemLength = itemLength;
    }

    @Override
    public Object decode(IBinaryDataReader input, int length, Charset charset, IFieldCodecContext context) {
        int count = (Integer) countCodec.decode(input, length, charset, context);

        IBinaryDataReader arrayInput = input.subInput(length);

        List<Object> ret = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Object item = itemCodec.decode(arrayInput, itemLength, charset, context);
            ret.add(item);
        }
        return ret;
    }

    @Override
    public void encode(IBinaryDataWriter output, Object value, int length, Charset charset,
                       IFieldCodecContext context, IFieldBinaryEncoder bodyEncoder) {
        Collection<Object> list = (Collection<Object>) value;
        if (list == null)
            list = Collections.emptyList();

        countCodec.encode(output, list.size(), -1, charset, context, null);
        for (Object item : list) {
            itemCodec.encode(output, item, itemLength, charset, context, null);
        }
    }
}
