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

        // 1~2位hex（如默认字体色"0x0"）补零到6位，避免输出非法CSS（"#0"会被浏览器丢弃）
        if (color.length() <= 2) {
            return "#" + StringHelper.leftPad(color, 6, '0');
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
        // 6位RRGGBB表示不透明颜色，alpha必须置0xFF；否则alpha=0会被new Color(argb,true)
        // 当作全透明，且纯黑#000000与toArgbInt的0哨兵值混淆
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    public static float[] toNormalizedRgb(String color) {
        int[] rgba = parseRgba(color);
        // 调用方（如 ColorModificationHelper）会原地 mutate 返回数组——必须返回 fresh 数组
        if (rgba == null)
            return new float[]{0, 0, 0};
        return new float[]{rgba[0] / 255f, rgba[1] / 255f, rgba[2] / 255f};
    }

    private static final java.util.Map<String, int[]> NAMED_COLORS = java.util.Map.of(
            "black", new int[]{0, 0, 0, 255},
            "white", new int[]{255, 255, 255, 255},
            "red", new int[]{255, 0, 0, 255},
            "green", new int[]{0, 128, 0, 255},
            "blue", new int[]{0, 0, 255, 255},
            "yellow", new int[]{255, 255, 0, 255},
            "gray", new int[]{128, 128, 128, 255});

    /**
     * 规范颜色解析（WI9）：覆盖命名色（black/white/red/green/blue/yellow/gray，AWT 精确值）、
     * {@code #RRGGBB}/{@code #AARRGGBB}、{@code 0x} 前缀（6/8 位）、裸 hex（6/8 位）、
     * 1-2 位 hex 补零、{@code rgb(r,g,b)}/{@code rgba(r,g,b,a)}。
     *
     * @return {@code int[4]{r,g,b,a}}（各分量 0-255）；无法解析返回 null（不抛）
     */
    public static int[] parseRgba(String color) {
        if (StringHelper.isEmpty(color))
            return null;

        String trimmed = color.trim().toLowerCase();

        int[] named = NAMED_COLORS.get(trimmed);
        if (named != null)
            return named.clone();

        if (trimmed.startsWith("rgb(") && trimmed.endsWith(")")) {
            int[] parts = parseComponents(trimmed.substring(4, trimmed.length() - 1), 3);
            return parts == null ? null : new int[]{parts[0], parts[1], parts[2], 255};
        }
        if (trimmed.startsWith("rgba(") && trimmed.endsWith(")")) {
            String[] parts = trimmed.substring(5, trimmed.length() - 1).split(",");
            if (parts.length != 4)
                return null;
            try {
                int r = Integer.parseInt(parts[0].trim());
                int g = Integer.parseInt(parts[1].trim());
                int b = Integer.parseInt(parts[2].trim());
                float a = Float.parseFloat(parts[3].trim());
                if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255 || a < 0 || a > 1)
                    return null;
                // 保留既有截断语义：floor(a*255)
                return new int[]{r, g, b, (int) (a * 255)};
            } catch (NumberFormatException e) {
                return null;
            }
        }

        String hex = trimmed;
        if (hex.startsWith("#"))
            hex = hex.substring(1);
        else if (hex.startsWith("0x"))
            hex = hex.substring(2);

        if (hex.length() <= 2)
            hex = StringHelper.leftPad(hex, 6, '0');
        int alpha = 255;
        if (hex.length() == 8) {
            // AARRGGBB：首字节为 alpha（WI9 提取而非丢弃）
            alpha = Integer.parseInt(hex.substring(0, 2), 16);
            hex = hex.substring(2);
        }
        if (hex.length() != 6)
            return null;

        try {
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            return new int[]{r, g, b, alpha};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int[] parseComponents(String csv, int count) {
        String[] parts = csv.split(",");
        if (parts.length != count)
            return null;
        try {
            int[] ret = new int[count];
            for (int i = 0; i < count; i++)
                ret[i] = Integer.parseInt(parts[i].trim());
            for (int i = 0; i < count; i++)
                if (ret[i] < 0 || ret[i] > 255)
                    return null;
            return ret;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}