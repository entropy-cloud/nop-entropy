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
        } catch (NopCanvas2DException e) {
            throw e;
        } catch (RuntimeException e) {
            // 含 Color 构造器的分量越界（D2：解析失败一律包装，不泄漏 AWT 异常）
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
        } catch (NopCanvas2DException e) {
            throw e;
        } catch (RuntimeException e) {
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
        // CSS 语义：数值分量超界静默钳制（zrender 计算性颜色可产出 >255，如 rgba(135,175,274,.2)）
        return clampByte(part.endsWith("%")
                ? Math.round(Float.parseFloat(part.substring(0, part.length() - 1)) * 255f / 100f)
                : Integer.parseInt(part));
    }

    private static int alphaComponent(String part) {
        part = part.trim();
        if (part.endsWith("%"))
            return clampByte(Math.round(Float.parseFloat(part.substring(0, part.length() - 1)) * 255f / 100f));
        return clampByte((int) Math.round(Double.parseDouble(part) * 255));
    }

    private static int clampByte(int v) {
        return Math.max(0, Math.min(255, v));
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
        // 常用别名
        put(map, "cyan", 0x00FFFF);
        put(map, "magenta", 0xFF00FF);
        put(map, "grey", 0x808080);
        put(map, "orange", 0xFFA500);
        // CSS 扩展命名色（echarts 默认调色板与 rich 文本会产出，如 lightsteelblue）
        put(map, "aliceblue", 0xF0F8FF);
        put(map, "antiquewhite", 0xFAEBD7);
        put(map, "aquamarine", 0x7FFFD4);
        put(map, "azure", 0xF0FFFF);
        put(map, "beige", 0xF5F5DC);
        put(map, "bisque", 0xFFE4C4);
        put(map, "blanchedalmond", 0xFFEBCD);
        put(map, "blueviolet", 0x8A2BE2);
        put(map, "brown", 0xA52A2A);
        put(map, "burlywood", 0xDEB887);
        put(map, "cadetblue", 0x5F9EA0);
        put(map, "chartreuse", 0x7FFF00);
        put(map, "chocolate", 0xD2691E);
        put(map, "coral", 0xFF7F50);
        put(map, "cornflowerblue", 0x6495ED);
        put(map, "cornsilk", 0xFFF8DC);
        put(map, "crimson", 0xDC143C);
        put(map, "darkblue", 0x00008B);
        put(map, "darkcyan", 0x008B8B);
        put(map, "darkgoldenrod", 0xB8860B);
        put(map, "darkgray", 0xA9A9A9);
        put(map, "darkgreen", 0x006400);
        put(map, "darkgrey", 0xA9A9A9);
        put(map, "darkkhaki", 0xBDB76B);
        put(map, "darkmagenta", 0x8B008B);
        put(map, "darkolivegreen", 0x556B2F);
        put(map, "darkorange", 0xFF8C00);
        put(map, "darkorchid", 0x9932CC);
        put(map, "darksalmon", 0xE9967A);
        put(map, "darkseagreen", 0x8FBC8F);
        put(map, "darkslateblue", 0x483D8B);
        put(map, "darkslategray", 0x2F4F4F);
        put(map, "darkslategrey", 0x2F4F4F);
        put(map, "darkturquoise", 0x00CED1);
        put(map, "darkviolet", 0x9400D3);
        put(map, "deeppink", 0xFF1493);
        put(map, "deepskyblue", 0x00BFFF);
        put(map, "dimgray", 0x696969);
        put(map, "dimgrey", 0x696969);
        put(map, "dodgerblue", 0x1E90FF);
        put(map, "firebrick", 0xB22222);
        put(map, "floralwhite", 0xFFFAF0);
        put(map, "forestgreen", 0x228B22);
        put(map, "gainsboro", 0xDCDCDC);
        put(map, "ghostwhite", 0xF8F8FF);
        put(map, "gold", 0xFFD700);
        put(map, "goldenrod", 0xDAA520);
        put(map, "greenyellow", 0xADFF2F);
        put(map, "honeydew", 0xF0FFF0);
        put(map, "hotpink", 0xFF69B4);
        put(map, "indianred", 0xCD5C5C);
        put(map, "indigo", 0x4B0082);
        put(map, "ivory", 0xFFFFF0);
        put(map, "khaki", 0xF0E68C);
        put(map, "lavender", 0xE6E6FA);
        put(map, "lavenderblush", 0xFFF0F5);
        put(map, "lawngreen", 0x7CFC00);
        put(map, "lemonchiffon", 0xFFFACD);
        put(map, "lightblue", 0xADD8E6);
        put(map, "lightcoral", 0xF08080);
        put(map, "lightcyan", 0xE0FFFF);
        put(map, "lightgoldenrodyellow", 0xFAFAD2);
        put(map, "lightgray", 0xD3D3D3);
        put(map, "lightgreen", 0x90EE90);
        put(map, "lightgrey", 0xD3D3D3);
        put(map, "lightpink", 0xFFB6C1);
        put(map, "lightsalmon", 0xFFA07A);
        put(map, "lightseagreen", 0x20B2AA);
        put(map, "lightskyblue", 0x87CEFA);
        put(map, "lightslategray", 0x778899);
        put(map, "lightslategrey", 0x778899);
        put(map, "lightsteelblue", 0xB0C4DE);
        put(map, "lightyellow", 0xFFFFE0);
        put(map, "limegreen", 0x32CD32);
        put(map, "linen", 0xFAF0E6);
        put(map, "mediumaquamarine", 0x66CDAA);
        put(map, "mediumblue", 0x0000CD);
        put(map, "mediumorchid", 0xBA55D3);
        put(map, "mediumpurple", 0x9370DB);
        put(map, "mediumseagreen", 0x3CB371);
        put(map, "mediumslateblue", 0x7B68EE);
        put(map, "mediumspringgreen", 0x00FA9A);
        put(map, "mediumturquoise", 0x48D1CC);
        put(map, "mediumvioletred", 0xC71585);
        put(map, "midnightblue", 0x191970);
        put(map, "mintcream", 0xF5FFFA);
        put(map, "mistyrose", 0xFFE4E1);
        put(map, "moccasin", 0xFFE4B5);
        put(map, "navajowhite", 0xFFDEAD);
        put(map, "oldlace", 0xFDF5E6);
        put(map, "olivedrab", 0x6B8E23);
        put(map, "orchid", 0xDA70D6);
        put(map, "palegoldenrod", 0xEEE8AA);
        put(map, "palegreen", 0x98FB98);
        put(map, "paleturquoise", 0xAFEEEE);
        put(map, "palevioletred", 0xDB7093);
        put(map, "papayawhip", 0xFFEFD5);
        put(map, "peachpuff", 0xFFDAB9);
        put(map, "peru", 0xCD853F);
        put(map, "pink", 0xFFC0CB);
        put(map, "plum", 0xDDA0DD);
        put(map, "powderblue", 0xB0E0E6);
        put(map, "rosybrown", 0xBC8F8F);
        put(map, "rebeccapurple", 0x663399);
        put(map, "saddlebrown", 0x8B4513);
        put(map, "salmon", 0xFA8072);
        put(map, "sandybrown", 0xF4A460);
        put(map, "seagreen", 0x2E8B57);
        put(map, "seashell", 0xFFF5EE);
        put(map, "sienna", 0xA0522D);
        put(map, "skyblue", 0x87CEEB);
        put(map, "slateblue", 0x6A5ACD);
        put(map, "slategray", 0x708090);
        put(map, "slategrey", 0x708090);
        put(map, "snow", 0xFFFAFA);
        put(map, "springgreen", 0x00FF7F);
        put(map, "steelblue", 0x4682B4);
        put(map, "tan", 0xD2B48C);
        put(map, "thistle", 0xD8BFD8);
        put(map, "tomato", 0xFF6347);
        put(map, "turquoise", 0x40E0D0);
        put(map, "violet", 0xEE82EE);
        put(map, "wheat", 0xF5DEB3);
        put(map, "whitesmoke", 0xF5F5F5);
        put(map, "yellowgreen", 0x9ACD32);
        return map;
    }

    private static void put(Map<String, Color> map, String name, int rgb) {
        map.put(name, new Color(rgb));
    }
}
