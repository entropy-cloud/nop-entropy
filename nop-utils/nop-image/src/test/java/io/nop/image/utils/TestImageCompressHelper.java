package io.nop.image.utils;

import io.nop.api.core.beans.BinaryDataBean;
import io.nop.core.resource.impl.FileResource;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestImageCompressHelper {

    /**
     * 带透明区域的 PNG 超过 maxSize 后走 jpg 压缩路径：
     * 透明像素必须合成到白色背景上，而不是默认的黑色
     */
    @Test
    public void testTransparentPngCompressedWithWhiteBackground() throws Exception {
        // 高熵噪声图像，保证 png 尺寸远大于 jpg，从而必然进入降质压缩循环
        int size = 200;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(42);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (x < size / 2) {
                    // 左半完全透明
                    img.setRGB(x, y, 0x00000000);
                } else {
                    // 右半不透明噪声
                    img.setRGB(x, y, 0xFF000000 | (random.nextInt() & 0xFFFFFF));
                }
            }
        }

        File pngFile = Files.createTempFile("nop-image-test-", ".png").toFile();
        try {
            ImageIO.write(img, "png", pngFile);
            long pngLength = pngFile.length();
            assertTrue(pngLength > 1000, "png should be large enough to force compression, length=" + pngLength);

            // maxSize 取 png 一半，必然触发 jpg 压缩路径
            BinaryDataBean result = ImageCompressHelper.compressImageWithLimit(
                    new FileResource(pngFile), (int) (pngLength / 2));
            assertNotNull(result);
            assertTrue(result.getData().length > 0);

            BufferedImage out = ImageIO.read(new ByteArrayInputStream(result.getData()));
            assertNotNull(out);

            // 左上角原图透明：压缩后应合成白色背景（修复前合成黑色）
            int px = out.getRGB(0, 0);
            int r = (px >> 16) & 0xFF, g = (px >> 8) & 0xFF, b = px & 0xFF;
            assertTrue(r > 200 && g > 200 && b > 200,
                    "transparent pixel should blend to white background, got rgb=" + r + "," + g + "," + b);
        } finally {
            Files.deleteIfExists(pngFile.toPath());
        }
    }
}
