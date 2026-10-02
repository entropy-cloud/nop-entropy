package io.nop.excel.model.constants;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 文件魔数识别语义：按前缀字节判定文档/图片类型，'?' 为通配字节，前缀不足按 UNKNOWN 处理。
 */
public class TestFileMagicDetection {

    private static byte[] bytes(int... ints) {
        byte[] ret = new byte[ints.length];
        for (int i = 0; i < ints.length; i++) {
            ret[i] = (byte) (ints[i] & 0xFF);
        }
        return ret;
    }

    @Test
    public void testDetectDocumentMagics() {
        // OOXML（zip 头 PK\x03\x04）
        assertEquals(FileMagic.OOXML, FileMagic.valueOf(bytes('P', 'K', 0x03, 0x04, 0x14, 0x00)));
        // 原始 XML（<?xml）
        assertEquals(FileMagic.XML, FileMagic.valueOf(bytes('<', '?', 'x', 'm', 'l', ' ')));
        // PDF / RTF
        assertEquals(FileMagic.PDF, FileMagic.valueOf(bytes('%', 'P', 'D', 'F', '-')));
        assertEquals(FileMagic.RTF, FileMagic.valueOf(bytes('{', '\\', 'r', 't', 'f')));
        // 图片魔数
        assertEquals(FileMagic.PNG, FileMagic.valueOf(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)));
        assertEquals(FileMagic.GIF, FileMagic.valueOf(bytes('G', 'I', 'F', '8', '9', 'a')));
        assertEquals(FileMagic.BMP, FileMagic.valueOf(bytes('B', 'M', 0x00, 0x00)));

        // 未知前缀
        assertEquals(FileMagic.UNKNOWN, FileMagic.valueOf(bytes(0x00, 0x01, 0x02, 0x03)));
    }

    // 模式比 '?' 通配；字节数少于所有魔数模式长度时不能误判为 UNKNOWN 之外的值
    @Test
    public void testWildcardAndTooShortPrefix() {
        // BIFF2 模式含 '?' 通配字节
        assertEquals(FileMagic.BIFF2, FileMagic.valueOf(bytes(0x09, 0x00, 0x04, 0x00, 0x00, 0x00, 'X', 0x00)));

        // 只有 2 字节 "PK"，短于 OOXML 的 4 字节模式 → 无法命中任何模式 → UNKNOWN
        assertEquals(FileMagic.UNKNOWN, FileMagic.valueOf(bytes('P', 'K')));

        // 空数组
        assertEquals(FileMagic.UNKNOWN, FileMagic.valueOf(new byte[0]));
    }

    // PictureType 按 FileMagic 映射图片类型
    @Test
    public void testPictureTypeFromFileMagic() {
        assertEquals(PictureType.PNG, PictureType.valueOf(FileMagic.PNG));
        assertEquals(PictureType.JPEG, PictureType.valueOf(FileMagic.JPEG));
        assertEquals(PictureType.GIF, PictureType.valueOf(FileMagic.GIF));
        assertEquals(PictureType.BMP, PictureType.valueOf(FileMagic.BMP));
        assertEquals(PictureType.TIFF, PictureType.valueOf(FileMagic.TIFF));

        // 非 picture 魔数降级为 PictureType.UNKNOWN（不抛异常）
        assertEquals(PictureType.UNKNOWN, PictureType.valueOf(FileMagic.PDF));
        assertEquals(PictureType.UNKNOWN, PictureType.valueOf(FileMagic.UNKNOWN));

        // contentType 与扩展名成对
        assertEquals("image/png", PictureType.PNG.getContentType());
        assertEquals(".png", PictureType.PNG.getExtension());
    }
}
