package io.nop.pdf.toc;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.List;

/**
 * WI15：标题键化与匹配原语（headingize.mjs :13-55 直译）。
 */
public final class TocKeys {

    /** 锚（消费语义：由使用方/WI16 产出，nop-pdf 不生成） */
    public static final Pattern ANCHOR_RE = Pattern.compile("<a id=\"pdf-page-(\\d+)\"></a>");

    private static final Pattern H1_RE = Pattern.compile("^(chapter|appendix|part)\\s+\\d+", Pattern.CASE_INSENSITIVE);
    private static final Pattern BODY_H1_RE = Pattern.compile(
            "^(chapter\\s+\\d+|appendix\\s+[a-z]|part\\s+\\d+)\\s*\\r?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SOURCE_HEADING_LIKE_RE = Pattern.compile("^#{1,6}\\s+\\S");

    private TocKeys() {
    }

    public static String cleanTitle(String raw) {
        return raw.replaceAll("[\\s.·…]+$", "").replaceAll("\\s+", " ").trim();
    }

    /** headingize titleLevel（:29-32）：两档 */
    public static int titleLevel(String title) {
        if (H1_RE.matcher(title).find())
            return 1;
        return 2;
    }

    public static boolean isBodyH1(String trimmedLine) {
        return BODY_H1_RE.matcher(trimmedLine).find();
    }

    public static boolean isSourceHeadingLike(String trimmedLine) {
        return SOURCE_HEADING_LIKE_RE.matcher(trimmedLine).find();
    }

    /**
     * keyify（headingize :34-41）：NFKC → lowercase(Locale.ROOT) → 保留字母数字与 CJK
     */
    public static String keyify(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\u4e00-\\u9fff]", "");
    }

    /**
     * keyMatch（headingize :45-49）：双向前缀，min 长度 4
     */
    public static boolean keyMatch(String lineText, String titleKey) {
        String k = keyify(lineText);
        if (k.length() < Math.min(4, titleKey.length()))
            return false;
        return k.startsWith(titleKey) || titleKey.startsWith(k);
    }

    /**
     * findLine（headingize :51-54）：返回行号或 -1
     */
    public static int findLine(List<String> lines, String titleKey) {
        for (int i = 0; i < lines.size(); i++) {
            if (keyMatch(lines.get(i), titleKey))
                return i;
        }
        return -1;
    }
}
