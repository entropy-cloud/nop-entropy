package io.nop.record.codec;

import io.nop.api.core.exceptions.NopException;
import io.nop.record.RecordErrors;
import io.nop.record.codec._gen.FieldBinaryCodec_u2be;
import io.nop.record.codec.impl.DummyFieldCodecContext;
import io.nop.record.codec.impl.LVFieldBinaryCodec;
import io.nop.record.reader.ByteBufferBinaryDataReader;
import io.nop.record.reader.IBinaryDataReader;
import io.nop.record.serialization.IModelBasedBinaryRecordDeserializer;
import io.nop.record.serialization.IModelBasedBinaryRecordSerializer;
import io.nop.record.writer.IBinaryDataWriter;
import io.nop.record.writer.StreamBinaryDataWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LV（length-prefixed）字段编解码 roundtrip 语义：长度前缀 + 值、零长度解码为 null、超上限拒绝。
 */
public class TestLVFieldBinaryCodec {

    static final IFieldBinaryCodec LENGTH_CODEC = FieldBinaryCodec_u2be.INSTANCE;

    // 字符串值编解码：值以 UTF-8 字节写入，长度为字节数
    static final IFieldBinaryCodec VALUE_CODEC = new IFieldBinaryCodec() {
        @Override
        public Object decode(IBinaryDataReader input, Object record, int length,
                             IFieldCodecContext context, IModelBasedBinaryRecordDeserializer deserializer)
                throws IOException {
            byte[] bytes = input.readBytes(length);
            return new String(bytes, StandardCharsets.UTF_8);
        }

        @Override
        public void encode(IBinaryDataWriter output, Object value, int length,
                           IFieldCodecContext context, IModelBasedBinaryRecordSerializer serializer)
                throws IOException {
            output.writeBytes(((String) value).getBytes(StandardCharsets.UTF_8));
        }
    };

    LVFieldBinaryCodec newCodec() {
        return new LVFieldBinaryCodec(LENGTH_CODEC, VALUE_CODEC, v -> ((String) v).length());
    }

    // encode→decode roundtrip 恒等，线上布局 = [u2be 长度][utf8 字节]
    @Test
    public void testStringRoundtripIdentity() throws Exception {
        LVFieldBinaryCodec codec = newCodec();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        codec.encode(new StreamBinaryDataWriter(out), "abc", -1, DummyFieldCodecContext.INSTANCE, null);

        byte[] bytes = out.toByteArray();
        assertEquals(5, bytes.length);
        assertArrayEquals(new byte[]{0x00, 0x03, 'a', 'b', 'c'}, bytes);

        Object restored = codec.decode(new ByteBufferBinaryDataReader(bytes), null, -1,
                DummyFieldCodecContext.INSTANCE, null);
        assertEquals("abc", restored);
    }

    // 空值：encode 只写长度 0，不写值字节；decode 读到 0 长度返回 null
    @Test
    public void testNullAndZeroLengthSemantics() throws Exception {
        LVFieldBinaryCodec codec = newCodec();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        codec.encode(new StreamBinaryDataWriter(out), "", -1, DummyFieldCodecContext.INSTANCE, null);
        assertEquals(2, out.size());

        LVFieldBinaryCodec nullCodec = new LVFieldBinaryCodec(LENGTH_CODEC, VALUE_CODEC,
                v -> v == null ? 0 : ((String) v).length());
        ByteArrayOutputStream nullOut = new ByteArrayOutputStream();
        nullCodec.encode(new StreamBinaryDataWriter(nullOut), null, -1, DummyFieldCodecContext.INSTANCE, null);
        assertArrayEquals(new byte[]{0x00, 0x00}, nullOut.toByteArray());

        assertNull(codec.decode(new ByteBufferBinaryDataReader(new byte[]{0x00, 0x00}), null, -1,
                DummyFieldCodecContext.INSTANCE, null));
    }

    // 解码长度超过对象级 length 上限时拒绝（ERR_RECORD_DECODE_LENGTH_IS_TOO_LONG）
    @Test
    public void testDecodeLengthAboveCapRejected() throws Exception {
        LVFieldBinaryCodec codec = newCodec();
        byte[] bytes = new byte[]{0x00, 0x05, 'a', 'b', 'c', 'd', 'e'};
        // 上限 5：合法
        assertEquals("abcde", codec.decode(new ByteBufferBinaryDataReader(bytes), null, 5,
                DummyFieldCodecContext.INSTANCE, null));
        // 上限 4：线上长度 5 超限
        NopException e = assertThrows(NopException.class,
                () -> codec.decode(new ByteBufferBinaryDataReader(bytes), null, 4,
                        DummyFieldCodecContext.INSTANCE, null));
        assertTrue(e.getErrorCode().equals(RecordErrors.ERR_RECORD_DECODE_LENGTH_IS_TOO_LONG.getErrorCode()));
    }
}
