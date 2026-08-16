package io.nop.datav.service.component;

import io.nop.api.core.exceptions.NopException;
import io.nop.datav.biz.PanelComponentConfigArea;
import io.nop.datav.biz.PanelComponentMeta;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_UNKNOWN_COMPONENT_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestPanelComponentRegistry {

    @Test
    public void testAllSevenComponentTypesRegistered() {
        PanelComponentRegistry registry = PanelComponentRegistry.getInstance();
        // 7 component types in design doc §1.2 + container
        assertNotNull(registry.requireComponent(PanelComponentTypes.CHART));
        assertNotNull(registry.requireComponent(PanelComponentTypes.PIVOT_TABLE));
        assertNotNull(registry.requireComponent(PanelComponentTypes.STAT_TILE));
        assertNotNull(registry.requireComponent(PanelComponentTypes.MAP));
        assertNotNull(registry.requireComponent(PanelComponentTypes.TABLE));
        assertNotNull(registry.requireComponent(PanelComponentTypes.TEXT));
        assertNotNull(registry.requireComponent(PanelComponentTypes.IFRAME));
        assertNotNull(registry.requireComponent(PanelComponentTypes.CONTAINER));

        Map<String, IPanelComponent> all = registry.getComponents();
        assertTrue(all.size() >= 7, "registry should contain at least 7 component types");
    }

    @Test
    public void testMetadataIsQueryable() {
        PanelComponentRegistry registry = PanelComponentRegistry.getInstance();

        PanelComponentMeta chartMeta = registry.requireComponent(PanelComponentTypes.CHART).getMetadata();
        assertEquals(PanelComponentTypes.CHART, chartMeta.getType());
        assertNotNull(chartMeta.getDisplayName());
        assertTrue(chartMeta.isNeedsDataset(), "chart should need a dataset");

        PanelComponentMeta textMeta = registry.requireComponent(PanelComponentTypes.TEXT).getMetadata();
        assertEquals(PanelComponentTypes.TEXT, textMeta.getType());
        assertFalse(textMeta.isNeedsDataset(), "text should not need a dataset");

        PanelComponentMeta iframeMeta = registry.requireComponent(PanelComponentTypes.IFRAME).getMetadata();
        assertFalse(iframeMeta.isNeedsDataset(), "iframe should not need a dataset");
    }

    @Test
    public void testUnknownComponentTypeThrowsNopException() {
        PanelComponentRegistry registry = PanelComponentRegistry.getInstance();
        NopException ex = assertThrows(NopException.class,
                () -> registry.requireComponent("non-existent-type"));
        assertEquals(ERR_DATAV_UNKNOWN_COMPONENT_TYPE.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testPanelTypeMappingIntToType() {
        assertEquals(PanelComponentTypes.CHART, PanelTypeMapping.toComponentType(0));
        assertEquals(PanelComponentTypes.TABLE, PanelTypeMapping.toComponentType(10));
        assertEquals(PanelComponentTypes.STAT_TILE, PanelTypeMapping.toComponentType(20));
        assertEquals(PanelComponentTypes.TEXT, PanelTypeMapping.toComponentType(30));
        assertEquals(PanelComponentTypes.CONTAINER, PanelTypeMapping.toComponentType(40));
        assertEquals(PanelComponentTypes.PIVOT_TABLE, PanelTypeMapping.toComponentType(50));
        assertEquals(PanelComponentTypes.MAP, PanelTypeMapping.toComponentType(60));
        assertEquals(PanelComponentTypes.IFRAME, PanelTypeMapping.toComponentType(70));
    }

    @Test
    public void testPanelTypeMappingUnknownThrows() {
        NopException ex = assertThrows(NopException.class,
                () -> PanelTypeMapping.toComponentType(999));
        assertEquals(ERR_DATAV_UNKNOWN_COMPONENT_TYPE.getErrorCode(), ex.getErrorCode());

        NopException nullEx = assertThrows(NopException.class,
                () -> PanelTypeMapping.toComponentType(null));
        assertEquals(ERR_DATAV_UNKNOWN_COMPONENT_TYPE.getErrorCode(), nullEx.getErrorCode());
    }

    // ==================== D4-2 装饰/媒体组件族 ====================

    @Test
    public void testDecorativeMediaComponentsRegisteredWithNeedsDatasetFalse() {
        PanelComponentRegistry registry = PanelComponentRegistry.getInstance();
        // 6 类装饰/媒体组件均经 requireComponent 查询成功且 needsDataset=false
        for (String type : new String[]{
                PanelComponentTypes.DECORATIVE_BORDER,
                PanelComponentTypes.SCROLL_TEXT,
                PanelComponentTypes.TIME_CLOCK,
                PanelComponentTypes.VIDEO,
                PanelComponentTypes.STREAM,
                PanelComponentTypes.CAROUSEL_TAB}) {
            PanelComponentMeta meta = registry.requireComponent(type).getMetadata();
            assertEquals(type, meta.getType());
            assertFalse(meta.isNeedsDataset(),
                    "decorative/media component should not need a dataset: " + type);
            assertNotNull(meta.getDisplayName(), "displayName should be set: " + type);
        }
    }

    @Test
    public void testRegistryContainsFourteenComponentTypes() {
        Map<String, IPanelComponent> all = PanelComponentRegistry.getInstance().getComponents();
        // 8 D1-1 既有 + 6 D4-2 装饰/媒体 = 14
        assertEquals(14, all.size(), "registry should contain 14 component types (8 D1-1 + 6 D4-2)");
    }

    @Test
    public void testDecorativeMediaConfigAreasMatchDesignDoc() {
        PanelComponentRegistry registry = PanelComponentRegistry.getInstance();

        // decorative-border → variant(required) + color
        assertConfigAreas(registry, PanelComponentTypes.DECORATIVE_BORDER,
                new ExpectedArea("variant", true),
                new ExpectedArea("color", false));

        // scroll-text → text(required) + speed + direction
        assertConfigAreas(registry, PanelComponentTypes.SCROLL_TEXT,
                new ExpectedArea("text", true),
                new ExpectedArea("speed", false),
                new ExpectedArea("direction", false));

        // time-clock → format + timezone
        assertConfigAreas(registry, PanelComponentTypes.TIME_CLOCK,
                new ExpectedArea("format", false),
                new ExpectedArea("timezone", false));

        // video → src(required) + autoplay + loop + controls
        assertConfigAreas(registry, PanelComponentTypes.VIDEO,
                new ExpectedArea("src", true),
                new ExpectedArea("autoplay", false),
                new ExpectedArea("loop", false),
                new ExpectedArea("controls", false));

        // stream → src(required) + protocol
        assertConfigAreas(registry, PanelComponentTypes.STREAM,
                new ExpectedArea("src", true),
                new ExpectedArea("protocol", false));

        // carousel-tab → tabs(required) + interval
        assertConfigAreas(registry, PanelComponentTypes.CAROUSEL_TAB,
                new ExpectedArea("tabs", true),
                new ExpectedArea("interval", false));
    }

    @Test
    public void testLegacyComponentsHaveEmptyConfigAreas() {
        // D1-1 既有组件（无描述符）按空列表处理（向后兼容）
        PanelComponentRegistry registry = PanelComponentRegistry.getInstance();
        for (String type : new String[]{
                PanelComponentTypes.CHART,
                PanelComponentTypes.TEXT,
                PanelComponentTypes.IFRAME,
                PanelComponentTypes.CONTAINER}) {
            List<PanelComponentConfigArea> areas = registry.requireComponent(type).getMetadata().getConfigAreas();
            assertNotNull(areas, "configAreas should not be null (legacy): " + type);
            assertTrue(areas.isEmpty(), "legacy components should have empty configAreas: " + type);
        }
    }

    private static void assertConfigAreas(PanelComponentRegistry registry, String type,
                                          ExpectedArea... expected) {
        List<PanelComponentConfigArea> areas = registry.requireComponent(type).getMetadata().getConfigAreas();
        assertNotNull(areas, "configAreas should not be null: " + type);
        assertEquals(expected.length, areas.size(),
                "config area count mismatch for " + type + ": " + areas);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i].name, areas.get(i).getName(),
                    "area[" + i + "].name mismatch for " + type);
            assertEquals(expected[i].required, areas.get(i).isRequired(),
                    "area[" + i + "].required mismatch for " + type);
            assertNotNull(areas.get(i).getDescription(),
                    "area[" + i + "].description should not be null for " + type);
        }
    }

    private static final class ExpectedArea {
        final String name;
        final boolean required;

        ExpectedArea(String name, boolean required) {
            this.name = name;
            this.required = required;
        }
    }
}
