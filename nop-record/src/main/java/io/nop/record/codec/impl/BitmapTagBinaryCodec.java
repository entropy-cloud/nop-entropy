package io.nop.record.codec.impl;

import io.nop.commons.collections.bit.FixedBitSet;
import io.nop.commons.collections.bit.IBitSet;
import io.nop.commons.collections.bit.SmallBitSet;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.codec.IFieldTagBinaryCodec;
import io.nop.record.model.RecordFieldMeta;
import io.nop.record.model.RecordObjectMeta;
import io.nop.record.reader.IBinaryDataReader;
import io.nop.record.writer.IBinaryDataWriter;

import java.io.IOException;
import java.util.List;

public class BitmapTagBinaryCodec implements IFieldTagBinaryCodec {
    public static final BitmapTagBinaryCodec INSTANCE = new BitmapTagBinaryCodec();

    @Override
    public IBitSet decodeTags(IBinaryDataReader input, RecordObjectMeta typeMeta,
                              IFieldCodecContext context) throws IOException {
        byte[] bytes = input.readBytes(8);
        IBitSet bs;
        byte[] nextBytes = null;
        // 第一位为0，则只有一个bitmap
        if ((bytes[0] & 0b1000_0000) == 0) {
            bs = new SmallBitSet();
        } else {
            bs = new FixedBitSet(128);
            nextBytes = input.readBytes(8);
        }

        int index = 0;

        for (int i = 0; i < 8; i++) {
            for (int j = 7; j >= 0; j--) {
                if (((bytes[i] >> j) & 0x01) != 0) {
                    bs.set(index);
                }
                index++;
            }
        }

        if (nextBytes != null) {
            for (int i = 0; i < 8; i++) {
                for (int j = 7; j >= 0; j--) {
                    if (((nextBytes[i] >> j) & 0x01) != 0) {
                        bs.set(index);
                    }
                    index++;
                }
            }
        }

        return bs;
    }

    @Override
    public IBitSet encodeTags(IBinaryDataWriter output, Object value,
                              RecordObjectMeta typeMeta, IFieldCodecContext context) throws IOException {
        IBitSet bitSet = new FixedBitSet(128);
        List<RecordFieldMeta> fields = typeMeta.getFields();
        int max = 0;
        for (RecordFieldMeta f : fields) {
            if (f.getTagIndex() < 0)
                continue;

            String propName = f.getPropOrFieldName();
            Object propValue = BeanTool.getProperty(value, propName);
            if (propValue != null) {
                if (f.getTagIndex() > max)
                    max = f.getTagIndex();
                bitSet.set(f.getTagIndex());
            }
        }
        if (max >= 64)
            bitSet.set(0);
        return bitSet;
    }
}
