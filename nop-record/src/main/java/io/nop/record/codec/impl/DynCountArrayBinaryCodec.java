package io.nop.record.codec.impl;

import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldBinaryEncoder;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.reader.IRecordBinaryReader;
import io.nop.record.writer.IRecordBinaryWriter;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DynCountArrayBinaryCodec implements IFieldBinaryCodec {
    private final IFieldBinaryCodec countCodec;

    private final int itemLength;

    public DynCountArrayBinaryCodec(IFieldBinaryCodec countCodec, int itemLength) {
        this.countCodec = countCodec;
        this.itemLength = itemLength;
    }

    @Override
    public Object decode(IRecordBinaryReader input, int length, Charset charset, IFieldCodecContext context) {
        int count = (Integer) countCodec.decode(input, length, charset, context);

        IRecordBinaryReader arrayInput = input.subInput(length);

        List<Object> ret = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Object item = null; // itemCodec.decode(arrayInput, itemLength, charset, context);
            ret.add(item);
        }
        return ret;
    }

    @Override
    public void encode(IRecordBinaryWriter output, Object value, int length, Charset charset,
                       IFieldCodecContext context, IFieldBinaryEncoder bodyEncoder) {
        Collection<Object> list = (Collection<Object>) value;
        if (list == null)
            list = Collections.emptyList();

        countCodec.encode(output, list.size(), -1, charset, context, null);
        for (Object item : list) {
            bodyEncoder.encode(output, item, itemLength, charset, context, null);
        }
    }
}
