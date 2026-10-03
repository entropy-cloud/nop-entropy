package io.nop.record.codec;

import io.nop.commons.bytes.ByteString;
import io.nop.commons.type.StdDataType;
import io.nop.record.codec.impl.DefaultFieldConfig;
import io.nop.record.codec.impl.DummyFieldCodecContext;
import io.nop.record.codec.impl.FixedLengthStringCodecFactory;
import io.nop.record.reader.ByteBufferBinaryDataReader;
import io.nop.record.reader.SimpleTextDataReader;
import io.nop.record.writer.AppendableTextDataWriter;
import io.nop.record.writer.IBinaryDataWriter;
import io.nop.record.writer.ITextDataWriter;
import io.nop.record.writer.StreamBinaryDataWriter;
import io.nop.record.codec.IFieldTextCodec;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 定长字符串（FLS）编解码语义：按左/右补齐到固定位宽，解码时去补齐并按字段类型转换；空串解码为 null。
 */
public class TestFixedLengthStringCodec {

    static IFieldBinaryCodec codec(StdDataType dataType, int length, boolean leftPad) {
        DefaultFieldConfig config = new DefaultFieldConfig("f", dataType, length);
        config.setLeftPad(leftPad);
        return FixedLengthStringCodecFactory.INSTANCE.newBinaryCodec(config);
    }

    static IBinaryDataWriter binaryWriter(ByteArrayOutputStream out) {
        return new StreamBinaryDataWriter(out);
    }

    // 整数右补齐："42" -> "42   "，解码去右补齐转回 Integer
    @Test
    public void testIntRightPadRoundtrip() throws Exception {
        IFieldBinaryCodec codec = codec(StdDataType.INT, 5, false);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        codec.encode(binaryWriter(out), 42, 5, DummyFieldCodecContext.INSTANCE, null);
        assertEquals("42   ", new String(out.toByteArray(), StandardCharsets.UTF_8));

        Object restored = codec.decode(new ByteBufferBinaryDataReader(out.toByteArray()), null, 5,
                DummyFieldCodecContext.INSTANCE, null);
        assertEquals(42, restored);
    }

    // 整数左补齐："42" -> "   42"，解码去左补齐转回 Integer
    @Test
    public void testIntLeftPadRoundtrip() throws Exception {
        IFieldBinaryCodec codec = codec(StdDataType.INT, 5, true);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        codec.encode(binaryWriter(out), 42, 5, DummyFieldCodecContext.INSTANCE, null);
        assertEquals("   42", new String(out.toByteArray(), StandardCharsets.UTF_8));

        Object restored = codec.decode(new ByteBufferBinaryDataReader(out.toByteArray()), null, 5,
                DummyFieldCodecContext.INSTANCE, null);
        assertEquals(42, restored);
    }

    // 字符串字段 roundtrip 恒等；全补齐字段（空值）解码为 null
    @Test
    public void testStringRoundtripAndEmptyToNull() throws Exception {
        IFieldBinaryCodec codec = codec(StdDataType.STRING, 6, false);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        codec.encode(binaryWriter(out), "AB", 6, DummyFieldCodecContext.INSTANCE, null);
        assertEquals("AB    ", new String(out.toByteArray(), StandardCharsets.UTF_8));
        assertEquals("AB", codec.decode(new ByteBufferBinaryDataReader(out.toByteArray()), null, 6,
                DummyFieldCodecContext.INSTANCE, null));

        // 全空格编码 -> 解码为 null
        ByteArrayOutputStream emptyOut = new ByteArrayOutputStream();
        codec.encode(binaryWriter(emptyOut), "", 6, DummyFieldCodecContext.INSTANCE, null);
        assertEquals("      ", new String(emptyOut.toByteArray(), StandardCharsets.UTF_8));
        assertNull(codec.decode(new ByteBufferBinaryDataReader(emptyOut.toByteArray()), null, 6,
                DummyFieldCodecContext.INSTANCE, null));
    }

    // 文本路径同样按左补齐编解码；自定义补齐字符按 config.padding 生效
    @Test
    public void testTextCodecPadSemantics() throws Exception {
        DefaultFieldConfig config = new DefaultFieldConfig("f", StdDataType.INT, 5);
        config.setLeftPad(true);
        IFieldTextCodec textCodec = FixedLengthStringCodecFactory.INSTANCE.newTextCodec(config);

        StringBuilder sb = new StringBuilder();
        ITextDataWriter textWriter = new AppendableTextDataWriter(sb);
        textCodec.encode(textWriter, 42, 5, DummyFieldCodecContext.INSTANCE, null);
        assertEquals("   42", sb.toString());

        Object restored = textCodec.decode(new SimpleTextDataReader("   42"), null, 5,
                DummyFieldCodecContext.INSTANCE, null);
        assertEquals(42, restored);

        // 左补齐字符为 '0' 时解码同样去左侧 '0'
        DefaultFieldConfig zeroPadConfig = new DefaultFieldConfig("f", StdDataType.INT, 5);
        zeroPadConfig.setLeftPad(true);
        zeroPadConfig.setPadding(ByteString.of(new byte[]{'0'}));
        IFieldBinaryCodec zeroPadCodec = FixedLengthStringCodecFactory.INSTANCE.newBinaryCodec(zeroPadConfig);
        assertEquals(42, zeroPadCodec.decode(
                new ByteBufferBinaryDataReader("00042".getBytes(StandardCharsets.UTF_8)), null, 5,
                DummyFieldCodecContext.INSTANCE, null));
    }

    // 回归覆盖 wi9#4（plan 2306 项 26）：文本路径 encode 的 null 缺省统一为 ""，
    // 与二进制路径一致；此前文本路径缺省 "0" 会伪造数值内容
    @Test
    public void testTextEncodeNullUsesEmptyDefault() throws Exception {
        DefaultFieldConfig config = new DefaultFieldConfig("f", StdDataType.STRING, 5);
        config.setLeftPad(false);
        IFieldTextCodec codec = FixedLengthStringCodecFactory.INSTANCE.newTextCodec(config);

        StringBuilder sb = new StringBuilder();
        codec.encode(new AppendableTextDataWriter(sb), null, 5, DummyFieldCodecContext.INSTANCE, null);
        assertEquals("     ", sb.toString(), "null 必须按空串补齐，而非按 \"0\" 补齐");
    }
}
