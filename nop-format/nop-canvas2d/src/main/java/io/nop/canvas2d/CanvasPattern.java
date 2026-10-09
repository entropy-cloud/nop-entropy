package io.nop.canvas2d;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * 图案（plan 2361 D10）：源图为创建时拷贝快照；repetition 缺省 repeat；
 * pattern.setTransform 不实现（zrender 的 DOMMatrix 守卫在裸环境必然跳过，dist:27337-27345）。
 */
public class CanvasPattern implements ICanvasPattern {
    private final BufferedImage image;
    private final String repetition;

    CanvasPattern(BufferedImage image, String repetition) {
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        try {
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }
        this.image = copy;
        this.repetition = repetition;
    }

    BufferedImage getImage() {
        return image;
    }

    String getRepetition() {
        return repetition;
    }
}
