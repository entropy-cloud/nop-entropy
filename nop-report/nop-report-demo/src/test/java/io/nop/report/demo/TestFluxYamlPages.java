package io.nop.report.demo;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.config.AppConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.web.WebConfigs;
import io.nop.web.page.PageProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 验证 report demo 的 flux.yaml 在 flux 模式下可加载。 */
@NopTestConfig
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
    public void testDemoPages() {
        AppConfig.getConfigProvider().updateConfigValue(WebConfigs.CFG_WEB_RENDER_MODE, "flux");
        ResourceComponentManager.instance().clearCache("xlib");
        ResourceComponentManager.instance().clearCache("xpage");
        assertNotNull(pageProvider.getPage("/nop/report/demo/pages/demo.flux.yaml", ""));
        assertNotNull(pageProvider.getPage("/nop/report/demo/pages/report-with-params.flux.yaml", ""));
    }
}
