package io.nop.record.codec;

import io.nop.api.core.exceptions.NopException;
import io.nop.record.RecordErrors;
import io.nop.record.codec.impl.DummyFieldCodecContext;
import io.nop.record.codec.impl.EOLFieldCodec;
import io.nop.record.reader.SimpleTextDataReader;
import io.nop.record.reader.StreamBinaryDataReader;
import io.nop.record.writer.AppendableTextDataWriter;
import io.nop.record.writer.StreamBinaryDataWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 行结束符编解码语义：解码兼容 "\r\n"/"\n"（归一为 '\n'），编码恒写 '\n'；非 EOL 字节必须报错。
 */
public class TestEOLFieldCodec {

    // 二进制路径："\n" 解码为 '\n'
    @Test
    public void testBinaryDecodeLf() throws Exception {
        StreamBinaryDataReader in = new StreamBinaryDataReader(
                new ByteArrayInputStream("a\n".getBytes(StandardCharsets.UTF_8)));
        in.read(); // 消费 'a'，让 EOL 从 '\n' 开始
        Object ret = EOLFieldCodec.INSTANCE.decode(in, null, -1, DummyFieldCodecContext.INSTANCE, null);
        assertEquals('\n', ret);
    }

    // 二进制路径："\r\n" 归一化为 '\n'
    @Test
    public void testBinaryDecodeCrlfNormalized() throws Exception {
        StreamBinaryDataReader in = new StreamBinaryDataReader(
                new ByteArrayInputStream("a\r\nb".getBytes(StandardCharsets.UTF_8)));
        in.read();
        Object ret = EOLFieldCodec.INSTANCE.decode(in, null, -1, DummyFieldCodecContext.INSTANCE, null);
        assertEquals('\n', ret);
        // CRLF 被完整消费：下一次 read 即为后续数据
        assertEquals('b', in.read());
    }

    // 非 EOL 字节报 ERR_RECORD_NOT_END_OF_LINE；流截断报 EOFException
    @Test
    public void testBinaryRejectsNonEolAndEof() throws Exception {
        StreamBinaryDataReader in = new StreamBinaryDataReader(
                new ByteArrayInputStream("ax".getBytes(StandardCharsets.UTF_8)));
        in.read();
        NopException e = assertThrows(NopException.class,
                () -> EOLFieldCodec.INSTANCE.decode(in, null, -1, DummyFieldCodecContext.INSTANCE, null));
        assertTrue(e.getErrorCode().equals(RecordErrors.ERR_RECORD_NOT_END_OF_LINE.getErrorCode()));

        StreamBinaryDataReader eofIn = new StreamBinaryDataReader(
                new ByteArrayInputStream("a".getBytes(StandardCharsets.UTF_8)));
        eofIn.read();
        assertThrows(EOFException.class,
                () -> EOLFieldCodec.INSTANCE.decode(eofIn, null, -1, DummyFieldCodecContext.INSTANCE, null));
    }

    // 文本路径：同样兼容 CRLF；encode 恒写 '\n'（读 CRLF 写回 LF 为有意规范化）
    @Test
    public void testTextCodecNormalizeAndEncode() throws Exception {
        SimpleTextDataReader in = new SimpleTextDataReader("a\r\nb");
        in.readChar();
        Object ret = EOLFieldCodec.INSTANCE.decode(in, null, -1, DummyFieldCodecContext.INSTANCE, null);
        assertEquals("\n", ret);
        assertEquals('b', in.readChar());

        // 二进制 encode 只写 1 字节 '\n'
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EOLFieldCodec.INSTANCE.encode(new StreamBinaryDataWriter(out), "\n", -1, DummyFieldCodecContext.INSTANCE, null);
        assertEquals(1, out.size());
        assertEquals('\n', out.toByteArray()[0]);

        // 文本 encode 追加 '\n'
        StringBuilder sb = new StringBuilder();
        AppendableTextDataWriter textOut = new AppendableTextDataWriter(sb);
        EOLFieldCodec.INSTANCE.encode(textOut, null, -1, DummyFieldCodecContext.INSTANCE, null);
        assertEquals("\n", sb.toString());
        assertEquals(1, textOut.length());
    }
}
