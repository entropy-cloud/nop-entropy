package io.nop.pdf.toc;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WI15：目录行检测——headingize.mjs TOC_ENTRY_RE（:17）原文直译（3 组形态，页码取
 * 组 3 对应组；三页码形态：物理页/逻辑页 `3-1`/中文汇编卷 `附件一-1`；行尾容 \r）。
 * 逐行 full match；不启用 UNICODE_CHARACTER_CLASS（R1-m2 裁定：全角由 keyify NFKC 兜底）。
 */
public final class TocLineDetector {

    /**
     * headingize.mjs:17 原文：
     * /^(.{3,140}?)[ .]{2,}(([A-Za-z]?\d{1,4}(?:-[A-Za-z]?\d{1,4}[A-Za-z]?)?)|附件[一二三四五六七八九十]+-\d+)\s*\r?$/
     */
    static final Pattern TOC_ENTRY_RE = Pattern.compile(
            "^(.{3,140}?)[ .]{2,}(([A-Za-z]?\\d{1,4}(?:-[A-Za-z]?\\d{1,4}[A-Za-z]?)?)|附件[一二三四五六七八九十]+-\\d+)\\s*\\r?$");

    private TocLineDetector() {
    }

    /**
     * cleanTitle（headingize :20-22）：剥尾部空白/点/间隔号/省略号，空白归一
     */
    public static String cleanTitle(String raw) {
        return raw.replaceAll("[\\s.·…]+$", "").replaceAll("\\s+", " ").trim();
    }

    public static boolean isTocEntry(String line) {
        return TOC_ENTRY_RE.matcher(line).matches();
    }

    /**
     * 检测一行目录条目；非目录行返回 null
     */
    public static TocLine detect(String line) {
        Matcher m = TOC_ENTRY_RE.matcher(line);
        if (!m.matches())
            return null;
        String title = cleanTitle(m.group(1));
        if (title.length() < 3)
            return null;
        String ref = m.group(2);
        Integer pageNo;
        if (ref.contains("-")) {
            // 逻辑页形态（`3-1` / `附件一-1`）：物理页未知
            pageNo = null;
        } else {
            // NaN 守卫（headingize :182）：字母前缀伪页码（页眉 "V17" 类）条目整条丢弃
            try {
                pageNo = Integer.parseInt(ref);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return new TocLine(title, ref, pageNo);
    }

    public static List<TocLine> detectAll(List<String> lines) {
        List<TocLine> ret = new ArrayList<>();
        for (String line : lines) {
            TocLine t = detect(line);
            if (t != null)
                ret.add(t);
        }
        return ret;
    }
}
