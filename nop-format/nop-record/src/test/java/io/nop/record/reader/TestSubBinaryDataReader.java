package io.nop.record.reader;

import io.nop.commons.util.FileHelper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SubBinaryDataReader 的位置语义：seek/reset 以子区间起点为基准，
 * 不能把相对位置当底层绝对位置（修复前 seek(相对值) 导致静默错位读）
 */
public class TestSubBinaryDataReader {

    static File tempFile(byte[] data) throws IOException {
        File dir = new File("_tmp");
        dir.mkdirs();
        File file = new File(dir, "test-sub-binary-reader.bin");
        FileHelper.writeBytes(file, data);
        return file;
    }

    @Test
    public void testSeekRelativeSubInterval() throws Exception {
        byte[] data = new byte[64];
        for (int i = 0; i < 64; i++)
            data[i] = (byte) i;
        File file = tempFile(data);

        try (RandomAccessFileBinaryDataReader reader = new RandomAccessFileBinaryDataReader(file)) {
            reader.seek(10);
            // 从偏移10开始的子区间
            IBinaryDataReader sub = reader.subInput(8);
            assertEquals(0, sub.pos());
            assertEquals(10, sub.readU1());

            // 相对位置1应映射到底层绝对位置11
            sub.seek(1);
            assertEquals(11, sub.readU1());

            // seek(0) 回到子区间起点
            sub.seek(0);
            assertEquals(10, sub.readU1());

            // reset 回到子区间起点（修复前 reset 回到底层文件起点0）
            sub.seek(0);
            sub.skip(3);
            assertEquals(3, sub.pos());
            sub.reset();
            assertEquals(0, sub.pos());
            assertEquals(10, sub.readU1());

            sub.close();
        }
    }

    // 嵌套subInput必须穿透父reader推进position：修复前委托underlying导致父position停滞，
    // 上层区域对齐公式据父pos算出虚增残留并双重skip
    @Test
    public void testNestedSubInputAdvancesParentPosition() throws Exception {
        byte[] data = new byte[10];
        for (int i = 0; i < 10; i++)
            data[i] = (byte) i;

        try (StreamBinaryDataReader stream = new StreamBinaryDataReader(new java.io.ByteArrayInputStream(data))) {
            IBinaryDataReader outer = stream.subInput(10);
            IBinaryDataReader inner = outer.subInput(5);
            for (int i = 0; i < 5; i++)
                assertEquals(i, inner.readU1());

            assertEquals(5, outer.pos());
            // outer 应从第5字节继续读，而不是重新读前5字节
            assertEquals(5, outer.readU1());
            inner.close();
            outer.close();
        }
    }
}
