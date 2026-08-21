package io.nop.record.writer;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 修复 writeByteBuffer 忽略 arrayOffset、direct buffer 崩溃的问题
 */
public class TestStreamBinaryDataWriter {

    @Test
    public void testWriteHeapSliceByteBuffer() throws Exception {
        ByteBuffer heap = ByteBuffer.allocate(10);
        for (int i = 0; i < 10; i++)
            heap.put((byte) i);
        heap.flip();

        // 视图：position=2, limit=6, arrayOffset=0 → 写出 [2,3,4,5]
        ByteBuffer slice = heap.slice();
        slice.position(2).limit(6);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StreamBinaryDataWriter writer = new StreamBinaryDataWriter(out);
        writer.writeByteBuffer(slice);
        byte[] bytes = out.toByteArray();
        assertEquals(4, bytes.length);
        assertEquals(2, bytes[0]);
        assertEquals(5, bytes[3]);
        assertEquals(4, writer.getWrittenCount());
    }

    @Test
    public void testWriteNonZeroArrayOffsetSlice() throws Exception {
        ByteBuffer heap = ByteBuffer.allocate(10);
        for (int i = 0; i < 10; i++)
            heap.put((byte) i);
        heap.position(3);
        // 在 offset=3 处创建子视图：arrayOffset=3，视图内容 [3..9]
        ByteBuffer slice = heap.slice();
        slice.position(1).limit(4);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new StreamBinaryDataWriter(out).writeByteBuffer(slice);
        byte[] bytes = out.toByteArray();
        // 修复前忽略 arrayOffset，写出错误数据
        assertEquals(3, bytes.length);
        assertEquals(4, bytes[0]);
        assertEquals(6, bytes[2]);
    }

    @Test
    public void testWriteDirectByteBuffer() throws Exception {
        ByteBuffer direct = ByteBuffer.allocateDirect(4);
        direct.put((byte) 7);
        direct.put((byte) 8);
        direct.put((byte) 9);
        direct.put((byte) 10);
        direct.flip();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new StreamBinaryDataWriter(out).writeByteBuffer(direct);
        // 修复前 buf.array() 对 direct buffer 抛 UnsupportedOperationException
        byte[] bytes = out.toByteArray();
        assertEquals(4, bytes.length);
        assertEquals(7, bytes[0]);
        assertEquals(10, bytes[3]);
    }
}
