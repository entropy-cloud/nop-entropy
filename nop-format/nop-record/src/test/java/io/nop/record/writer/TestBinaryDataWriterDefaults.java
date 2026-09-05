package io.nop.record.writer;

import io.nop.record.reader.ByteBufferBinaryDataReader;
import io.nop.record.reader.IBinaryDataReader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * writeFloat/writeDouble 便捷方法必须与 readFloat/readDouble 的默认字节序（大端）对称，
 * 修复前 writeFloat 默认走 writeF4le，往返后得到字节反转的垃圾值
 */
public class TestBinaryDataWriterDefaults {

    @Test
    public void testFloatDoubleRoundTrip() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IBinaryDataWriter writer = new StreamBinaryDataWriter(out);
        writer.writeFloat(1.5f);
        writer.writeDouble(-2.25);
        writer.flush();

        IBinaryDataReader reader = new ByteBufferBinaryDataReader(out.toByteArray());
        assertEquals(1.5f, reader.readFloat(), 0.0f);
        assertEquals(-2.25, reader.readDouble(), 0.0);
        reader.close();
    }

    @Test
    public void testWriteDoubleAcceptsDoublePrecision() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IBinaryDataWriter writer = new StreamBinaryDataWriter(out);
        // double精度值：修复前参数类型误声明为float，会在编译期/精度上截断
        double d = 0.1 + 0.2;
        writer.writeDouble(d);
        writer.flush();

        IBinaryDataReader reader = new ByteBufferBinaryDataReader(out.toByteArray());
        assertEquals(d, reader.readDouble(), 0.0);
        reader.close();
    }
}
