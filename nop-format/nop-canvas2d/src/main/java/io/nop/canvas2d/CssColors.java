package io.nop.canvas2d;

import java.awt.Color;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * CSS 颜色字符串解析：#rgb/#rgba/#rrggbb/#rrggbbaa、rgb()/rgba()（逗号或 modern 空格语法，支持 %）、
 * hsl()/hsla()、CSS 基础命名色、transparent。非法输入抛 {@link NopCanvas2DException}。
 *
 * <p>HSL→RGB 为独立实现（色度法），禁止以 Color.HSBtoRGB 代用——HSB 的第三分量是 brightness，
 * 与 HSL 的 lightness 语义不同（hsl(0,100%,50%) 必须是 #ff0000，HSB 代用会得暗红）。
 */
public final class CssColors {
    private static final Map<String, Color> NAMED_COLORS = buildNamedColors();

    private CssColors() {
    }

    public static Color parse(String css) {
        if (css == null)
            throw new NopCanvas2DException("CSS color must not be null");
        String s = css.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty())
            throw new NopCanvas2DException("Empty CSS color string");
        if (s.equals("transparent"))
            return new Color(0, 0, 0, 0);
        Color named = NAMED_COLORS.get(s);
        if (named != null)
            return named;
        if (s.startsWith("#"))
            return parseHex(s);
        if (s.startsWith("rgb(") || s.startsWith("rgba("))
            return parseRgb(s);
        if (s.startsWith("hsl(") || s.startsWith("hsla("))
            return parseHsl(s);
        throw new NopCanvas2DException("Unknown CSS color: " + css);
    }

    private static Color parseHex(String s) {
        String hex = s.substring(1);
        try {
            switch (hex.length()) {
                case 3: {
                    int r = hexDigit(hex, 0) * 17;
                    int g = hexDigit(hex, 1) * 17;
                    int b = hexDigit(hex, 2) * 17;
                    return new Color(r, g, b);
                }
                case 4: {
                    int r = hexDigit(hex, 0) * 17;
                    int g = hexDigit(hex, 1) * 17;
                    int b = hexDigit(hex, 2) * 17;
                    int a = hexDigit(hex, 3) * 17;
                    return new Color(r, g, b, a);
                }
                case 6: {
                    int rgb = (int) Long.parseLong(hex, 16);
                    return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
                }
                case 8: {
                    int r = Integer.parseInt(hex.substring(0, 2), 16);
                    int g = Integer.parseInt(hex.substring(2, 4), 16);
                    int b = Integer.parseInt(hex.substring(4, 6), 16);
                    int a = Integer.parseInt(hex.substring(6, 8), 16);
                    return new Color(r, g, b, a);
                }
                default:
                    throw new NopCanvas2DException("Unknown CSS color: " + s);
            }
        } catch (NumberFormatException e) {
            throw new NopCanvas2DException("Unknown CSS color: " + s, e);
        }
    }

    private static int hexDigit(String hex, int index) {
        return Integer.parseInt(hex.substring(index, index + 1), 16);
    }

    private static Color parseRgb(String s) {
        String[] parts = splitArgs(s);
        require(parts, 3, s);
        try {
            int r = colorComponent(parts[0]);
            int g = colorComponent(parts[1]);
            int b = colorComponent(parts[2]);
            int a = parts.length > 3 ? alphaComponent(parts[3]) : 255;
            return new Color(r, g, b, a);
        } catch (NumberFormatException e) {
            throw new NopCanvas2DException("Unknown CSS color: " + s, e);
        }
    }

    private static Color parseHsl(String s) {
        String[] parts = splitArgs(s);
        require(parts, 3, s);
        try {
            String hue = parts[0].trim();
            if (hue.endsWith("deg"))
                hue = hue.substring(0, hue.length() - 3);
            float h = normalizeHue(Float.parseFloat(hue.trim())) / 360f;
            float sat = percentOrFraction(parts[1]);
            float light = percentOrFraction(parts[2]);
            float a = parts.length > 3 ? alphaComponent(parts[3]) / 255f : 1f;
            return hslToRgb(h, sat, light, a);
        } catch (NumberFormatException e) {
            throw new NopCanvas2DException("Unknown CSS color: " + s, e);
        }
    }

    /**
     * 色度法 HSL→RGB。h/s/l 均已归一到 [0,1]。
     */
    static Color hslToRgb(float h, float saturation, float lightness, float alpha) {
        float c = (1 - Math.abs(2 * lightness - 1)) * saturation;
        float hp = h * 6f;
        float x = c * (1 - Math.abs(hp % 2f - 1));
        float r = 0, g = 0, b = 0;
        switch ((int) hp) {
            case 0:
                r = c;
                g = x;
                break;
            case 1:
                r = x;
                g = c;
                break;
            case 2:
                g = c;
                b = x;
                break;
            case 3:
                g = x;
                b = c;
                break;
            case 4:
                r = x;
                b = c;
                break;
            default:
                r = c;
                b = x;
                break;
        }
        float m = lightness - c / 2f;
        return new Color(Math.round((r + m) * 255), Math.round((g + m) * 255),
                Math.round((b + m) * 255), Math.round(alpha * 255));
    }

    private static float normalizeHue(float deg) {
        float h = deg % 360f;
        return h < 0 ? h + 360f : h;
    }

    private static String[] splitArgs(String s) {
        String inner = s.substring(s.indexOf('(') + 1, s.length() - 1);
        return inner.trim().split("[,\\s/]+");
    }

    private static void require(String[] parts, int min, String input) {
        if (parts.length < min || parts.length > 4 || parts[0].isEmpty())
            throw new NopCanvas2DException("Unknown CSS color: " + input);
    }

    private static int colorComponent(String part) {
        part = part.trim();
        if (part.endsWith("%"))
            return Math.round(Float.parseFloat(part.substring(0, part.length() - 1)) * 255f / 100f);
        return Integer.parseInt(part);
    }

    private static int alphaComponent(String part) {
        part = part.trim();
        if (part.endsWith("%"))
            return Math.round(Float.parseFloat(part.substring(0, part.length() - 1)) * 255f / 100f);
        return (int) Math.round(Double.parseDouble(part) * 255);
    }

    private static float percentOrFraction(String part) {
        part = part.trim();
        if (part.endsWith("%"))
            return Float.parseFloat(part.substring(0, part.length() - 1)) / 100f;
        return Float.parseFloat(part);
    }

    private static Map<String, Color> buildNamedColors() {
        Map<String, Color> map = new HashMap<>();
        // CSS16 基础色全集
        put(map, "aqua", 0x00FFFF);
        put(map, "black", 0x000000);
        put(map, "blue", 0x0000FF);
        put(map, "fuchsia", 0xFF00FF);
        put(map, "gray", 0x808080);
        put(map, "green", 0x008000);
        put(map, "lime", 0x00FF00);
        put(map, "maroon", 0x800000);
        put(map, "navy", 0x000080);
        put(map, "olive", 0x808000);
        put(map, "purple", 0x800080);
        put(map, "red", 0xFF0000);
        put(map, "silver", 0xC0C0C0);
        put(map, "teal", 0x008080);
        put(map, "white", 0xFFFFFF);
        put(map, "yellow", 0xFFFF00);
        // 常用别名与扩展
        put(map, "cyan", 0x00FFFF);
        put(map, "magenta", 0xFF00FF);
        put(map, "grey", 0x808080);
        put(map, "orange", 0xFFA500);
        return map;
    }

    private static void put(Map<String, Color> map, String name, int rgb) {
        map.put(name, new Color(rgb));
    }
}
