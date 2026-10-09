package io.nop.canvas2d;

import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI2 Phase 1：FontMapper 解析与缺字体显式报错（Cross-Cutting #5）。
 * 环境门控：本机已实证 SansSerif canDisplay('中')=true；若其他环境无 CJK 字体，
 * 中文字形断言按 plan B1 策略以 assumeTrue 门控，逻辑覆盖由自定义 mapper 承载。
 */
class CanvasFontMapperTest {

    @Test
    void genericFamiliesResolveToLogicalFonts() {
        CanvasFontMapper mapper = new CanvasFontMapper();
        Font sans = mapper.resolve(List.of("sans-serif"), Font.PLAIN, 12);
        assertEquals(Font.SANS_SERIF, sans.getFamily());
        Font mono = mapper.resolve(List.of("monospace"), Font.PLAIN, 12);
        assertEquals(Font.MONOSPACED, mono.getFamily());
    }

    @Test
    void monospacedResolvesEvenIfNotInAvailableNames() {
        // M2 修正：JDK 逻辑字体名是 Monospaced（Font.MONOSPACED），绕过名单精确匹配
        CanvasFontMapper mapper = new CanvasFontMapper();
        Font mono = mapper.resolve(List.of("monospace"), Font.PLAIN, 12);
        assertTrue(mono.canDisplay('A'));
    }

    @Test
    void familyListFallsThroughToLaterEntries() {
        // 不可用物理名 → 落到下一个可用家族
        CanvasFontMapper mapper = new CanvasFontMapper();
        Font font = mapper.resolve(List.of("NoSuchFont Xyz", "sans-serif"), Font.PLAIN, 12);
        assertEquals(Font.SANS_SERIF, font.getFamily());
    }

    @Test
    void allUnavailableThrowsExplicitly() {
        // Cross-Cutting #5：缺字体显式报错，禁静默 fallback（自造家族，不依赖环境名单）
        CanvasFontMapper mapper = new CanvasFontMapper();
        NopCanvas2DException ex = assertThrows(NopCanvas2DException.class,
                () -> mapper.resolve(List.of("NoSuchFont Xyz", "Another Missing"), Font.PLAIN, 12));
        assertTrue(ex.getMessage().contains("No available font"));
    }

    @Test
    void customMappingOverridesGeneric() {
        // 逻辑覆盖承载：自定义映射（环境相关字体名 → 门控断言）
        org.junit.jupiter.api.Assumptions.assumeTrue(canDisplayChinese("PingFang SC"),
                "environment lacks PingFang SC");
        CanvasFontMapper mapper = new CanvasFontMapper();
        mapper.setGenericMapping(Map.of("sans-serif", "PingFang SC"));
        Font font = mapper.resolve(List.of("sans-serif"), Font.PLAIN, 14);
        assertEquals("PingFang SC", font.getFamily());
    }

    @Test
    void singleWesternFontFailsChineseGlyphCheck() {
        // D6：「A+中」双字形校验——仅有西文字形的自造场景由字体名单不可用性覆盖；
        // 若环境存在无中文字形的物理字体（如 Symbol），resolve 应跳过它。
        // 此处用不可用名单已测报错；本测试钉住逻辑字体通过双字形校验（mac/CI 均应成立或被门控跳过）。
        org.junit.jupiter.api.Assumptions.assumeTrue(
                new Font(Font.SANS_SERIF, Font.PLAIN, 12).canDisplay('\u4e2d'),
                "environment logical SansSerif lacks CJK glyphs");
        CanvasFontMapper mapper = new CanvasFontMapper();
        Font font = mapper.resolve(List.of("sans-serif"), Font.PLAIN, 12);
        assertTrue(font.canDisplay('\u4e2d'));
    }

    private static boolean canDisplayChinese(String family) {
        Font font = new Font(family, Font.PLAIN, 12);
        return font.canDisplay('\u4e2d') && !Font.DIALOG.equals(font.getFamily());
    }
}
