package io.nop.canvas2d;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 泛族 → 物理字体映射与可用性校验（plan 2360 D6）。
 *
 * <p>裁定：五个逻辑字体名（按 {@link Font} 常量）直接放行、绕过可用性名单精确匹配；
 * 物理字体按 getAvailableFontFamilyNames 的 default + Locale.ENGLISH 双名单精确匹配——
 * <b>可用性检查必须先于 canDisplay</b>（不可用字体名经 Dialog fallback 也能通过 canDisplay，
 * 顺序颠倒会静默接受豆腐块字体）；通过可用性后校验「A+中」双字形；
 * 全部家族不可用 → {@link NopCanvas2DException}（缺字体显式报错，Cross-Cutting #5）。
 * 物理映射可注入覆盖（如 sans-serif → PingFang SC），平台配置接线归 WI4。
 */
public class CanvasFontMapper {
    private static final Set<String> LOGICAL_FAMILIES = Set.of(
            Font.DIALOG, Font.DIALOG_INPUT, Font.MONOSPACED, Font.SANS_SERIF, Font.SERIF);

    private final Map<String, String> genericMapping = new LinkedHashMap<>();

    private volatile Set<String> availableNames;

    public CanvasFontMapper() {
        genericMapping.put("sans-serif", Font.SANS_SERIF);
        genericMapping.put("serif", Font.SERIF);
        genericMapping.put("monospace", Font.MONOSPACED);
    }

    /**
     * 注入/覆盖泛族映射（key 为小写泛族名，value 为目标字体家族名）。
     */
    public synchronized void setGenericMapping(Map<String, String> mapping) {
        genericMapping.clear();
        genericMapping.put("sans-serif", Font.SANS_SERIF);
        genericMapping.put("serif", Font.SERIF);
        genericMapping.put("monospace", Font.MONOSPACED);
        if (mapping != null)
            genericMapping.putAll(mapping);
    }

    /**
     * 按家族声明顺序解析出第一个可用字体；全部不可用抛 {@link NopCanvas2DException}。
     */
    public Font resolve(List<String> families, int style, float sizePx) {
        Set<String> names = availableNames();
        List<String> tried = new ArrayList<>(families.size());
        for (String family : families) {
            String target = genericMapping.getOrDefault(family.toLowerCase(Locale.ROOT), family);
            Font font = new Font(target, style, Math.round(sizePx));
            if (isUsable(target, font, names))
                return font;
            tried.add(target);
        }
        throw new NopCanvas2DException(
                "No available font for families " + families + " (tried: " + tried + "); "
                        + "CJK glyphs must be covered, configure a font mapping if needed");
    }

    private boolean isUsable(String target, Font font, Set<String> names) {
        if (!LOGICAL_FAMILIES.contains(target)) {
            // 可用性先于 canDisplay：不可用名字经 Dialog fallback 也能通过 canDisplay
            if (!names.contains(target))
                return false;
        }
        // 「A + 中」双字形校验：防单西文字体静默接受导致中文豆腐块
        return font.canDisplay('A') && font.canDisplay('\u4e2d');
    }

    private Set<String> availableNames() {
        Set<String> names = availableNames;
        if (names == null) {
            synchronized (this) {
                names = availableNames;
                if (names == null) {
                    GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
                    Set<String> merged = new java.util.HashSet<>();
                    for (String name : env.getAvailableFontFamilyNames())
                        merged.add(name);
                    for (String name : env.getAvailableFontFamilyNames(Locale.ENGLISH))
                        merged.add(name);
                    names = Set.copyOf(merged);
                    availableNames = names;
                }
            }
        }
        return names;
    }
}
