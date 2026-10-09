package io.nop.chart.echarts.engine;

import javax.imageio.ImageIO;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 像素容差比对工具（plan 2363 D26，仓库首个 image-diff 设施）：
 * 差异像素 = 任一 ARGB 通道差 > channelTolerance；失败时必产出 diff 图（target/diff/）；
 * golden 生成模式 -Degcharts.golden.generate=true（仍执行非空白断言；CI 环境拒跑）。
 */
public final class ImageDiffAssert {
    private static final int CHANNEL_TOLERANCE = 8;
    private static final double MAX_DIFF_RATIO = 0.015;
    private static final Path GENERATE_FLAG = Path.of("target", "golden-generate.flag");

    private ImageDiffAssert() {
    }

    /**
     * 非背景（非全透明）判定——全 plan 统一的「非空白」：绝对下限 100px 或比例 ≥0.1%
     * （细线图墨迹占比天然低于 1%，阈值实测校正：lines@400x300 ≈ 900px = 0.76%）。
     */
    public static void assertNotBlank(BufferedImage image, String what) {
        int total = image.getWidth() * image.getHeight();
        int opaque = 0;
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++)
                if (((image.getRGB(x, y) >>> 24) & 0xFF) > 10)
                    opaque++;
        if (opaque < 100 && opaque * 1000 < total)
            fail(what + ": render is blank (opaque " + opaque + " px)");
    }

    /**
     * 与 golden 比对（D26）。生成模式下写出 target/golden/<name>.png 并跳过比对（仍先验非空白）。
     */
    public static void assertMatchesGolden(BufferedImage actual, String goldenName) {
        assertNotBlank(actual, goldenName);
        if (generateMode()) {
            if (System.getenv("CI") != null || System.getenv("GITHUB_ACTIONS") != null)
                fail("golden generation mode must not run on CI");
            write(actual, Path.of("target", "golden", goldenName + ".png"));
            return;
        }
        InputStream in = ImageDiffAssert.class.getResourceAsStream("/golden/" + goldenName + ".png");
        if (in == null)
            fail("golden missing: golden/" + goldenName + ".png — touch target/golden-generate.flag"
                    + " and rerun, then copy target/golden/" + goldenName + ".png into src/test/resources/golden/");
        BufferedImage expected;
        try {
            expected = ImageIO.read(in);
            in.close();
        } catch (IOException e) {
            fail("golden unreadable: " + goldenName);
            return;
        }
        assertSimilar(expected, actual, CHANNEL_TOLERANCE, MAX_DIFF_RATIO, goldenName);
    }

    /**
     * 容差比对：尺寸一致 + 差异像素（任一通道差 > tolerance）比例 ≤ maxDiffRatio。
     * 失败时产出 diff 图 target/diff/<label>.png。
     */
    public static void assertSimilar(BufferedImage expected, BufferedImage actual,
                                     int channelTolerance, double maxDiffRatio, String label) {
        assertEquals(expected.getWidth(), actual.getWidth(), label + ": width mismatch");
        assertEquals(expected.getHeight(), actual.getHeight(), label + ": height mismatch");
        int diff = 0;
        BufferedImage diffImage = null;
        Graphics2DHolder diffG = new Graphics2DHolder();
        int total = expected.getWidth() * expected.getHeight();
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                int e = expected.getRGB(x, y);
                int a = actual.getRGB(x, y);
                if (channelDiff(e, a) > channelTolerance) {
                    diff++;
                    if (diffImage == null) {
                        diffImage = new BufferedImage(expected.getWidth(), expected.getHeight(),
                                BufferedImage.TYPE_INT_ARGB);
                        diffG.set(diffImage.createGraphics());
                    }
                    diffImage.setRGB(x, y, 0xFFFF0000);
                }
            }
        }
        if (diffG.graphics != null)
            diffG.graphics.dispose();
        double ratio = (double) diff / total;
        if (ratio > maxDiffRatio) {
            if (diffImage != null)
                write(diffImage, Path.of("target", "diff", label + ".png"));
            fail(label + ": diff ratio " + String.format("%.3f", ratio * 100) + "% (" + diff + "/"
                    + total + " px) exceeds " + (maxDiffRatio * 100) + "% — diff image at target/diff/");
        }
    }

    /**
     * 生成模式开关 = 标记文件 target/golden-generate.flag 存在（surefire fork 的系统属性
     * 透传在本仓库父 pom 配置下不可靠，标记文件机制无传播依赖；target/ 不入库，CI 天然无标记）。
     */
    static boolean generateMode() {
        return Files.exists(GENERATE_FLAG);
    }

    private static int channelDiff(int a, int b) {
        return Math.max(Math.max(Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF)),
                Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF))),
                Math.max(Math.abs((a & 0xFF) - (b & 0xFF)),
                        Math.abs(((a >>> 24) & 0xFF) - ((b >>> 24) & 0xFF))));
    }

    private static void write(BufferedImage image, Path path) {
        try {
            Files.createDirectories(path.getParent());
            ImageIO.write(image, "png", path.toFile());
        } catch (IOException e) {
            throw new IllegalStateException("failed to write " + path, e);
        }
    }

    private static final class Graphics2DHolder {
        Graphics2D graphics;

        void set(Graphics2D g) {
            this.graphics = g;
        }
    }
}
