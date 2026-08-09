package io.nop.datav.service.component;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

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
}
