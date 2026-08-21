package io.nop.record.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.nop.commons.collections.bit.IBitSet;
import io.nop.record.codec.impl.BitmapTagBinaryCodec;
import io.nop.record.model.RecordFieldMeta;
import io.nop.record.model.RecordObjectMeta;
import io.nop.record.netty.ByteBufBinaryDataWriter;
import io.nop.record.reader.ByteBufferBinaryDataReader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * bitmap tag 编解码往返：encodeTags 必须写出 bitmap 字节（修复前只返回 bitSet 不写输出，
 * 每条记录比解码少 8/16 字节，decode(encode(x)) 无法往返）
 */
public class TestBitmapTagBinaryCodec {

    static RecordObjectMeta buildMeta() {
        RecordObjectMeta meta = new RecordObjectMeta();
        meta.setName("T");

        RecordFieldMeta f1 = new RecordFieldMeta();
        f1.setName("a");
        f1.setTagIndex(1);

        RecordFieldMeta f2 = new RecordFieldMeta();
        f2.setName("b");
        f2.setTagIndex(65);

        List<RecordFieldMeta> fields = new ArrayList<>();
        fields.add(f1);
        fields.add(f2);
        meta.setFields(fields);
        return meta;
    }

    static byte[] encode(RecordObjectMeta meta, Map<String, Object> value) throws IOException {
        ByteBuf buf = UnpooledByteBufAllocator.DEFAULT.buffer();
        ByteBufBinaryDataWriter writer = new ByteBufBinaryDataWriter(buf);
        BitmapTagBinaryCodec.INSTANCE.encodeTags(writer, value, meta, null);
        return ByteBufUtil.getBytes(buf);
    }

    @Test
    public void testRoundTrip() throws IOException {
        RecordObjectMeta meta = buildMeta();
        Map<String, Object> value = new HashMap<>();
        value.put("a", "x");

        // 仅 a 存在：单 bitmap，8 字节，bit1 置位
        byte[] encoded = encode(meta, value);
        assertEquals(8, encoded.length);
        assertEquals(0x40, encoded[0] & 0xFF);

        IBitSet bits = BitmapTagBinaryCodec.INSTANCE.decodeTags(new ByteBufferBinaryDataReader(encoded), meta, null);
        assertEquals(true, bits.get(1));

        // a+b 存在：b 的 tagIndex=65 >= 64，写16字节，bit0 续位 + bit1(a) + bit65(b) 置位
        value.put("b", "y");
        encoded = encode(meta, value);
        assertEquals(16, encoded.length);
        assertEquals(0xC0, encoded[0] & 0xFF);

        bits = BitmapTagBinaryCodec.INSTANCE.decodeTags(new ByteBufferBinaryDataReader(encoded), meta, null);
        assertEquals(true, bits.get(1));
        assertEquals(true, bits.get(65));
    }

    @Test
    public void testEmptyValueWritesBitmap() throws IOException {
        RecordObjectMeta meta = buildMeta();
        Map<String, Object> value = new HashMap<>();

        // 没有任何字段值时仍写出 8 字节全0 bitmap，与解码消费量对称
        byte[] encoded = encode(meta, value);
        assertEquals(8, encoded.length);
        for (byte b : encoded)
            assertEquals(0, b);
    }
}
