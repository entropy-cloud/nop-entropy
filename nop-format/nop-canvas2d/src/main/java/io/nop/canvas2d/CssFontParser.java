package io.nop.canvas2d;

import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CSS font 简写解析（canvas 规范子集，plan 2360 D5）：
 * {@code [style] [variant] [weight] size[/line-height] family-list}。
 * size 必须为 px（其他单位显式报错）；weight 支持 normal/bold/bolder/lighter/数字 100-900；
 * variant 仅接受 normal（zrender 不产出 small-caps）；不可解析 token 一律抛
 * {@link NopCanvas2DException}（English 消息含原始串）。
 */
public final class CssFontParser {
    private static final Pattern PREFIX = Pattern.compile(
            "^\\s*(?:(normal|italic|oblique|small-caps|bold|bolder|lighter|[1-9]00)\\s+)*" +
                    "(\\d+(?:\\.\\d+)?px(?:\\s*/\\s*(?:[\\d.]+px|[\\d.]+|normal))?)\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);

    private CssFontParser() {
    }

    public static CssFont parse(String cssFont) {
        if (cssFont == null || cssFont.trim().isEmpty())
            throw new NopCanvas2DException("CSS font string must not be null or empty");
        Matcher m = PREFIX.matcher(cssFont.trim());
        if (!m.matches())
            throw new NopCanvas2DException("Unsupported CSS font shorthand: " + cssFont);

        int style = Font.PLAIN;
        int weight = Font.PLAIN;
        // 前缀中的可选 token（style/variant/weight 任意顺序、可缺省）——按空白整词匹配，防子串误劈（如 bolder→bold+er）
        String head = cssFont.trim().substring(0, m.start(2)).trim();
        if (!head.isEmpty()) {
            for (String rawToken : head.split("\\s+")) {
                String token = rawToken.toLowerCase(java.util.Locale.ROOT);
                switch (token) {
                    case "italic":
                    case "oblique":
                        style |= Font.ITALIC;
                        break;
                    case "small-caps":
                        throw new NopCanvas2DException("font-variant small-caps is not supported: " + cssFont);
                    case "bold":
                    case "bolder":
                        weight = Font.BOLD;
                        break;
                    case "lighter":
                        weight = Font.PLAIN;
                        break;
                    case "normal":
                        break;
                    default:
                        if (token.matches("[1-9]00"))
                            weight = Integer.parseInt(token) >= 600 ? Font.BOLD : Font.PLAIN;
                        else
                            throw new NopCanvas2DException("Unsupported CSS font token \"" + rawToken + "\": " + cssFont);
                        break;
                }
            }
        }

        String sizeToken = m.group(2);
        float sizePx;
        float lineHeight = 0;
        int slash = sizeToken.indexOf('/');
        String sizePart = slash >= 0 ? sizeToken.substring(0, slash).trim() : sizeToken.trim();
        if (!sizePart.toLowerCase(java.util.Locale.ROOT).endsWith("px"))
            throw new NopCanvas2DException("CSS font size must use px unit: " + cssFont);
        sizePx = Float.parseFloat(sizePart.substring(0, sizePart.length() - 2));
        if (sizePx <= 0)
            throw new NopCanvas2DException("CSS font size must be positive: " + cssFont);
        if (slash >= 0) {
            String lh = sizeToken.substring(slash + 1).trim();
            if (lh.equalsIgnoreCase("normal")) {
                lineHeight = 0;
            } else if (lh.toLowerCase(java.util.Locale.ROOT).endsWith("px")) {
                lineHeight = Float.parseFloat(lh.substring(0, lh.length() - 2));
            } else {
                lineHeight = Float.parseFloat(lh) * sizePx;
            }
        }

        List<String> families = parseFamilies(m.group(3), cssFont);
        return new CssFont(style, weight, sizePx, lineHeight, families);
    }

    private static List<String> parseFamilies(String raw, String cssFont) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty())
            throw new NopCanvas2DException("CSS font family list must not be empty: " + cssFont);
        List<String> families = new ArrayList<>();
        // 按逗号切分并剥离引号（逗号在引号内的场景浏览器罕见，此处按简单逗号切分）
        for (String part : s.split(",")) {
            String family = part.trim();
            if (family.startsWith("'") || family.startsWith("\"")) {
                if (family.length() < 2)
                    throw new NopCanvas2DException("Malformed quoted font family: " + cssFont);
                family = family.substring(1, family.length() - 1).trim();
            }
            if (family.isEmpty())
                throw new NopCanvas2DException("CSS font family list contains empty entry: " + cssFont);
            families.add(family);
        }
        return List.copyOf(families);
    }
}
