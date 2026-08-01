package io.nop.auth.web.page;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.config.AppConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.web.WebConfigs;
import io.nop.web.page.PageProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证本模块手写 page.yaml 对应的 flux.yaml 能在 flux 模式下经 PageProvider 正确加载。
 */
@NopTestConfig
public class TestFluxYamlPages extends JunitBaseTestCase {

    @Inject
    PageProvider pageProvider;

    private void fluxMode() {
        AppConfig.getConfigProvider().updateConfigValue(WebConfigs.CFG_WEB_RENDER_MODE, "flux");
        ResourceComponentManager.instance().clearCache("xlib");
        ResourceComponentManager.instance().clearCache("xpage");
    }

    @AfterEach
    public void tearDown() {
        AppConfig.getConfigProvider().updateConfigValue(WebConfigs.CFG_WEB_RENDER_MODE, "amis");
        ResourceComponentManager.instance().clearCache("xlib");
        ResourceComponentManager.instance().clearCache("xpage");
    }

    private Map<String, Object> load(String path) {
        fluxMode();
        Map<String, Object> page = pageProvider.getPage(path, "");
        assertNotNull(page, "flux.yaml 应可加载: " + path);
        return page;
    }

    @Test
    public void testChangeSelfPass() {
        load("/nop/auth/pages/NopAuthUser/change-self-pass.flux.yaml");
    }

    @Test
    public void testAssignAuth() {
        load("/nop/auth/pages/NopAuthRole/assign-auth.flux.yaml");
    }

    @Test
    public void testUserDemo() {
        // demo.flux.yaml 使用 flux-web:GenPage + NopAuthUser.view.xml，验证整条生成链可加载
        load("/nop/auth/pages/NopAuthUser/demo.flux.yaml");
    }

    @Test
    public void testDemoPageDemo() {
        load("/nop/auth/pages/DemoPage/demo.flux.yaml");
    }
}
