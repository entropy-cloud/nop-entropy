/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.model.color;

import io.nop.commons.util.StringHelper;

public class ColorHelper {
    public static byte applyTint(int lum, double tint) {
        if (tint > 0) {
            return (byte) (lum * (1.0 - tint) + (255 - 255 * (1.0 - tint)));
        } else if (tint < 0) {
            return (byte) (lum * (1 + tint));
        } else {
            return (byte) lum;
        }
    }

    public static String applyTint(String rgb, double tint) {
        if (rgb.length() == 8) {
            rgb = rgb.substring(2);
        }

        if (rgb.length() != 6)
            return rgb;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            int value = Integer.parseInt(rgb.substring(i * 2, i * 2 + 2), 16);
            byte val = applyTint(value & 0xFF, tint);
            sb.append(StringHelper.leftPad(Integer.toHexString(val & 0xFF), 2, '0'));
        }
        return sb.toString().toUpperCase();
    }

    public static String toCssColor(String color) {
        if (StringHelper.isEmpty(color))
            return color;

        if (color.startsWith("0x"))
            color = color.substring(2);

        if (color.length() == 6) {
            return "#" + color;
        }

        if (color.length() == 8) {
            // rgba
            double alpha = Integer.parseInt(color.substring(0, 2), 16) / 255.0;
            int red = Integer.parseInt(color.substring(2, 4), 16);
            int green = Integer.parseInt(color.substring(4, 6), 16);
            int blue = Integer.parseInt(color.substring(6, 8), 16);
            return "rgba(" + red + "," + green + "," + blue + "," + alpha + ")";
        }


        if (!color.startsWith("#"))
            return "#" + color;
        return color;
    }

    public static int toArgbInt(String color) {
        if (StringHelper.isEmpty(color))
            return 0;

        if (color.startsWith("#")) {
            color = color.substring(1);
        } else if (color.startsWith("0x")) {
            color = color.substring(2);
        }

        int ret = 0;
        if (color.length() == 8) {
            return argb(color);
        } else if (color.length() == 6) {
            return rgb(color);
        }
        return ret;
    }

    private static int argb(String color) {
        int alpha = Integer.parseInt(color.substring(0, 2), 16) & 0xFF;
        int red = Integer.parseInt(color.substring(2, 4), 16) & 0xFF;
        int green = Integer.parseInt(color.substring(4, 6), 16) & 0xFF;
        int blue = Integer.parseInt(color.substring(6, 8), 16) & 0xFF;
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int rgb(String color) {
        int red = Integer.parseInt(color.substring(0, 2), 16) & 0xFF;
        int green = Integer.parseInt(color.substring(2, 4), 16) & 0xFF;
        int blue = Integer.parseInt(color.substring(4, 6), 16) & 0xFF;
        return red << 16 | green << 8 | blue;
    }

    public static float[] toNormalizedRgb(String color) {
        if (StringHelper.isEmpty(color))
            return new float[]{0, 0, 0};

        if (color.startsWith("#")) {
            color = color.substring(1);
        } else if (color.startsWith("0x")) {
            color = color.substring(2);
        }

        if (color.length() != 6) {
            throw new IllegalArgumentException("Invalid color format. Expected 6 hex digits.");
        }

        int red = Integer.parseInt(color.substring(0, 2), 16);
        int green = Integer.parseInt(color.substring(2, 4), 16);
        int blue = Integer.parseInt(color.substring(4, 6), 16);

        return new float[]{
                red / 255f,
                green / 255f,
                blue / 255f
        };
    }
}