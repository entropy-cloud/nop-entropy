package io.nop.dyn.web;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.config.AppConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.web.WebConfigs;
import io.nop.web.page.PageProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 验证 ai-gen.flux.yaml 在 flux 模式下可加载。 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestFluxYamlPages extends JunitBaseTestCase {
    @Inject
    PageProvider pageProvider;

    @AfterEach
    public void tearDown() {
        AppConfig.getConfigProvider().updateConfigValue(WebConfigs.CFG_WEB_RENDER_MODE, "amis");
        ResourceComponentManager.instance().clearCache("xlib");
        ResourceComponentManager.instance().clearCache("xpage");
    }

    @Test
    public void testAiGen() {
        AppConfig.getConfigProvider().updateConfigValue(WebConfigs.CFG_WEB_RENDER_MODE, "flux");
        ResourceComponentManager.instance().clearCache("xlib");
        ResourceComponentManager.instance().clearCache("xpage");
        assertNotNull(pageProvider.getPage("/nop/dyn/pages/NopDynModule/ai-gen.flux.yaml", ""));
    }
}
