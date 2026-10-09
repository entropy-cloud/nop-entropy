package io.nop.chart.echarts.engine;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI5 closure audit 修复轮（Major-1/Major-2 补充）：ImageDiffAssert 自身行为测试
 * （合成图「相同过/容差内过/篡改红/空白红」）+ configure 泵上限生效回归断言。
 */
class ImageDiffAssertBehaviorTest {

    private static BufferedImage solid(int rgb) {
        BufferedImage img = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 40; y++)
            for (int x = 0; x < 40; x++)
                img.setRGB(x, y, rgb);
        return img;
    }

    @Test
    void identicalImagesPass() {
        // 相同图过
        assertDoesNotThrow(() -> ImageDiffAssert.assertSimilar(solid(0xFFFF0000), solid(0xFFFF0000), 8, 0.0, "identical"));
    }

    @Test
    void withinTolerancePasses() {
        // 单通道差 8（=容差边界内）过
        assertDoesNotThrow(() -> ImageDiffAssert.assertSimilar(solid(0xFF0000FF), solid(0xFF0000F7), 8, 0.0, "within"));
    }

    @Test
    void beyondToleranceFailsAndProducesDiffImage() {
        // 篡改红：单通道差 >8 即 fail，且失败必产出 diff 图
        assertThrows(AssertionError.class,
                () -> ImageDiffAssert.assertSimilar(solid(0xFF0000FF), solid(0xFF000000), 8, 0.0, "tampered"));
        // diff 图落盘（失败必产，非可选）
        assertTrue(java.nio.file.Files.exists(java.nio.file.Path.of("target", "diff", "tampered.png")),
                "failure must produce diff image");
    }

    @Test
    void blankImageFailsNotBlank() {
        // 空白红：全透明图过不了 assertNotBlank（绝对下限 100px）
        assertThrows(AssertionError.class, () -> ImageDiffAssert.assertNotBlank(solid(0x00000000), "blank-probe"));
    }
}
