package io.nop.ai.meta;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.IObjPropMeta;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MR4 adjudication regression test: NopAiModel.apiKey must not be exposed at any xmeta layer.
 * <p>
 * - The generated base xmeta (_NopAiModel.xmeta) must restrict queryable/sortable/published
 * (driven by ORM source column tagSet not-query/not-sort/not-pub).
 * - The delta-merged runtime xmeta (NopAiModel.xmeta) must additionally block insert/update
 * and mark the prop internal.
 */
public class TestNopAiModelApiKeyXmeta {

    private static final String BASE_XMETA = "/nop/ai/model/NopAiModel/_NopAiModel.xmeta";
    private static final String MERGED_XMETA = "/nop/ai/model/NopAiModel/NopAiModel.xmeta";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static IObjPropMeta loadApiKeyProp(String path) {
        IObjMeta meta = (IObjMeta) ResourceComponentManager.instance().loadComponentModel(path);
        IObjPropMeta prop = meta.getProp("apiKey");
        assertNotNull(prop, "apiKey prop must exist in " + path);
        return prop;
    }

    @Test
    public void testGeneratedBaseXmetaRestrictsApiKey() {
        IObjPropMeta prop = loadApiKeyProp(BASE_XMETA);
        assertFalse(prop.isQueryable(), "base xmeta must not expose apiKey as queryable");
        assertFalse(prop.isSortable(), "base xmeta must not expose apiKey as sortable");
        assertFalse(prop.isPublished(), "base xmeta must not publish apiKey");
        assertTrue(prop.isInternal(), "base xmeta must mark apiKey internal");
    }

    @Test
    public void testMergedXmetaFullyRestrictsApiKey() {
        IObjPropMeta prop = loadApiKeyProp(MERGED_XMETA);
        assertFalse(prop.isQueryable(), "merged xmeta must not expose apiKey as queryable");
        assertFalse(prop.isSortable(), "merged xmeta must not expose apiKey as sortable");
        assertFalse(prop.isInsertable(), "merged xmeta must not allow apiKey insert via API");
        assertFalse(prop.isUpdatable(), "merged xmeta must not allow apiKey update via API");
        assertFalse(prop.isPublished(), "merged xmeta must not publish apiKey");
        assertTrue(prop.isInternal(), "merged xmeta must mark apiKey internal");
    }

    @Test
    public void testOtherPropsRemainExposed() {
        IObjMeta meta = (IObjMeta) ResourceComponentManager.instance().loadComponentModel(MERGED_XMETA);
        IObjPropMeta modelName = meta.getProp("modelName");
        assertNotNull(modelName);
        assertTrue(modelName.isQueryable(), "non-credential props must stay queryable");
        assertTrue(modelName.isSortable(), "non-credential props must stay sortable");
    }
}
