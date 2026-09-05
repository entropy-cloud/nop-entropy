package io.nop.record.codec;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.bytes.ByteString;
import io.nop.record.codec._gen.FieldBinaryCodec_u2be;
import io.nop.record.codec.impl.AbstractFixedLengthAsciiCodec;
import io.nop.record.codec.impl.DynCountArrayBinaryCodec;
import io.nop.record.codec.impl.DynLVFieldBinaryCodec;
import io.nop.record.reader.ByteBufferBinaryDataReader;
import io.nop.record.reader.IBinaryDataReader;
import io.nop.record.writer.IBinaryDataWriter;
import io.nop.record.writer.StreamBinaryDataWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestDynCodecs {

    static final IFieldBinaryCodec ITEM_CODEC = new AbstractFixedLengthAsciiCodec(' ', false, StandardCharsets.UTF_8) {
        @Override
        protected Object decodeString(String text, Function<io.nop.api.core.exceptions.ErrorCode, NopException> errorFactory) {
            return text;
        }
    };

    static final IFieldBinaryCodec U2BE = FieldBinaryCodec_u2be.INSTANCE;

    IBinaryDataReader reader(byte[] bytes) {
        return new ByteBufferBinaryDataReader(bytes);
    }

    static IBinaryDataWriter writer(ByteArrayOutputStream out) {
        return new StreamBinaryDataWriter(out);
    }

    @Test
    public void testDynCountRoundTripWithItemCodec() throws Exception {
        DynCountArrayBinaryCodec codec = new DynCountArrayBinaryCodec(U2BE, ITEM_CODEC, 3);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        codec.encode(writer(out), List.of("ab", "cd"), -1, null, null);
        assertEquals(2 + 3 + 3, out.size());
        assertEquals(2, (out.toByteArray()[1] & 0xff));
        assertEquals('a', out.toByteArray()[2]);
        assertEquals('d', out.toByteArray()[6]);
        Object result = codec.decode(reader(out.toByteArray()), null, 6, null, null);
        assertEquals(List.of("ab", "cd"), result);
    }

    @Test
    public void testDynCountOldConstructorFailsFast() {
        DynCountArrayBinaryCodec codec = new DynCountArrayBinaryCodec(U2BE, 3);
        assertThrows(UnsupportedOperationException.class,
                () -> codec.decode(reader(new byte[]{0, 1, 'a', 'b', 'c'}), null, -1, null, null));
        assertThrows(UnsupportedOperationException.class,
                () -> codec.encode(writer(new ByteArrayOutputStream()), List.of("ab"), -1, null, null));
    }

    @Test
    public void testDynLVDecodeReturnsByteString() throws Exception {
        byte[] data = new byte[]{0, 3, 'a', 'b', 'c'};
        DynLVFieldBinaryCodec codec = new DynLVFieldBinaryCodec(U2BE, null, bs -> ((ByteString) bs).length());
        Object result = codec.decode(reader(data), null, -1, null, null);
        assertInstanceOf(ByteString.class, result);
        assertEquals("abc", new String(((ByteString) result).toByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    public void testDynLVEncodeWritesValue() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DynLVFieldBinaryCodec codec = new DynLVFieldBinaryCodec(U2BE, null, bs -> ((ByteString) bs).length());
        codec.encode(writer(out), ByteString.of(new byte[]{'a', 'b', 'c'}), -1, null, null);
        assertEquals(2 + 3, out.size());
        assertEquals(3, (out.toByteArray()[1] & 0xff));
        assertEquals('a', out.toByteArray()[2]);
    }

    @Test
    public void testDynLVEncodeStringAsUtf8() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DynLVFieldBinaryCodec codec = new DynLVFieldBinaryCodec(U2BE, null, s -> ((String) s).length());
        codec.encode(writer(out), "abc", -1, null, null);
        assertEquals(2 + 3, out.size());
        assertEquals("abc", new String(out.toByteArray(), 2, 3, StandardCharsets.UTF_8));
    }

    @Test
    public void testDynLVDecodeRejectsShortData() {
        byte[] data = new byte[]{0, 3, 'a'};
        DynLVFieldBinaryCodec codec = new DynLVFieldBinaryCodec(U2BE, null, bs -> ((ByteString) bs).length());
        assertThrows(NopException.class, () -> codec.decode(reader(data), null, -1, null, null));
    }

    // maxLength边界：线上长度恰好等于maxLength是合法值，超过才报too long（修复前 >= 把等值也拒绝）
    @Test
    public void testDynLVAcceptsLengthEqualToMaxLength() throws Exception {
        byte[] data = new byte[]{0, 3, 'a', 'b', 'c'};
        DynLVFieldBinaryCodec codec = new DynLVFieldBinaryCodec(U2BE, null, bs -> ((ByteString) bs).length());
        Object result = codec.decode(reader(data), null, 3, null, null);
        assertEquals("abc", new String(((ByteString) result).toByteArray(), StandardCharsets.UTF_8));

        byte[] tooLong = new byte[]{0, 4, 'a', 'b', 'c', 'd'};
        assertThrows(NopException.class, () -> codec.decode(reader(tooLong), null, 3, null, null));
    }
}
