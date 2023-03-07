/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nop.auth.core.verifycode;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.QuadCurve2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * 文字验证码。根据Happy-Captcha的代码改造
 */
public class DefaultVerifyCodeGenerator implements IVerifyCodeGenerator {
    // 验证码字体
    private Font font = loadFont();
    // 验证码字符默认长度
    protected int length = 4;
    // 验证码图片默认宽度
    protected int width = 105;
    // 验证码图片默认高度
    protected int height = 35;

    private static final String BASE_CHARS = "qwertyuiplkjhgfdsazxcvbnmQWERTYUPLKJHGFDSAZXCVBNM1234567890";

    Font loadFont() {
        return new Font("Times New Roman", Font.BOLD | Font.ITALIC, 30);
        // return new Font("Arial", Font.ITALIC, 30);
    }

    @Override
    public VerifyCode generateCode(String secret) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String code = StringHelper.randomString(length, BASE_CHARS);
        drawImage(code, out);
        String base64 = StringHelper.encodeBase64(out.toByteArray());
        return new VerifyCode(code, "data:image/jpg;base64," + base64);
    }

    @Override
    public boolean checkValid(String cachedCode, String inputCode, String secret) {
        return cachedCode.equals(inputCode);
    }

    protected void drawImage(String chars, OutputStream output) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_BGR);
        Graphics2D g = (Graphics2D) img.getGraphics();
        g.setBackground(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        drawLine(200, g);
        drawOval(g);
        drawBezierLine(g);
        g.setFont(font);
        FontMetrics fontMetrics = g.getFontMetrics();
        int fw = (width / chars.length()) - 2;
        int fm = (fw - (int) fontMetrics.getStringBounds("8", g).getWidth()) / 2;
        for (int i = 0; i < chars.length(); i++) {
            g.setColor(Color.BLACK);
            // int fh = height - ((height - (int) fontMetrics.getStringBounds(String.valueOf(chars.charAt(i)),
            // g).getHeight()) >> 1);
            g.drawString(String.valueOf(chars.charAt(i)), i * fw + fm + MathHelper.random().nextInt(-3, 3),
                    23 + MathHelper.random().nextInt(3));
        }
        g.dispose();
        try {
            ImageIO.write(img, "JPEG", output);
            output.flush();
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    private static int nextInt(int value) {
        return MathHelper.random().nextInt(value);
    }

    /**
     * 获取两个数之间的随机数
     *
     * @param start 起始值
     * @param end   结束值
     * @return 随机数
     */
    private static int nextInt(int start, int end) {
        return MathHelper.random().nextInt(start, end);
    }

    /**
     * 随机从Web安全色中选取一种颜色
     *
     * @return RGB颜色
     */
    protected Color color() {
        return new Color(nextInt(150, 200), nextInt(150, 200), nextInt(150, 200));
    }

    /**
     * 绘制干扰线条
     *
     * @param size 线条数量
     * @param g    Graphics2D
     */
    protected void drawLine(int size, Graphics2D g) {
        for (int i = 0; i < size; i++) {
            g.setColor(color());
            int x = nextInt(width - 1) + 1;
            int y = nextInt(height);
            int _width = nextInt(2);
            int _height = nextInt(2);
            g.drawLine(x, y, x + _width, y + _height);
        }
    }

    /**
     * 绘制圆圈
     *
     * @param g Graphics2D
     */
    protected void drawOval(Graphics2D g) {
        drawOval(nextInt(3, 8), g);
    }

    /**
     * 绘制圆圈
     *
     * @param size 数量
     * @param g    Graphics2D
     */
    protected void drawOval(int size, Graphics2D g) {
        size = Math.max(size, 3);
        for (int i = 0; i < size; i++) {
            g.setColor(color());
            g.drawOval(nextInt(width - 20), nextInt(height - 10), nextInt(5, 15), nextInt(5, 15));
        }
    }

    /**
     * 绘制贝塞尔曲线
     *
     * @param g Graphics2D
     */
    protected void drawBezierLine(Graphics2D g) {
        drawBezierLine(nextInt(2, 5), g);
    }

    /**
     * 绘制贝塞尔曲线
     *
     * @param size 曲线数量
     * @param g    Graphics2D
     */
    protected void drawBezierLine(int size, Graphics2D g) {
        size = Math.max(size, 2);
        for (int i = 0; i < size; i++) {
            g.setColor(color());
            int x1 = 5, y1 = nextInt(5, height / 2);
            int x2 = width - 5, y2 = nextInt(height / 2, height - 5);
            int ctrlX = nextInt(width / 4, width / 4 * 3), ctrlY = nextInt(5, height - 5);
            if (nextInt(2) == 0) {
                int ty = y1;
                y1 = y2;
                y2 = ty;
            }
            if (nextInt(2) == 0) {
                // 绘制二阶贝塞尔曲线
                QuadCurve2D shape = new QuadCurve2D.Double();
                shape.setCurve(x1, y1, ctrlX, ctrlY, x2, y2);
                g.draw(shape);
            } else {
                // 绘制三阶贝塞尔曲线
                int ctrlX1 = nextInt(width / 4, width / 4 * 3), ctrlY1 = nextInt(5, height - 5);
                CubicCurve2D shape = new CubicCurve2D.Double(x1, y1, ctrlX, ctrlY, ctrlX1, ctrlY1, x2, y2);
                g.draw(shape);
            }
        }
    }
}