/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.web.page;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.json.JSON;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.web.page.PageProvider;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(enableAppBeansFile = false, autoConfigPattern = "nop-web")
public class TestWebPage extends JunitBaseTestCase {

    @Inject
    PageProvider pageProvider;

    @Test
    public void testPage() {
        Map<String, Object> page = pageProvider.getPage("/nop/auth/pages/NopAuthUser/main.page.yaml", null);
        System.out.println(JSON.serialize(page, true));
    }

    @Test
    public void testCustomActions() {
        Map<String, Object> page = pageProvider.getPage("/nop/auth/pages/NopAuthResource/main.page.yaml", null);
        String str = JSON.serialize(page, true);
        System.out.println(str);
        assertTrue(str.indexOf("row-add-child-button") > 0);
    }

    @Test
    public void testSaveDiff() {
        String path = "/nop/auth/pages/NopAuthUser/main.page.yaml";
        Map<String, Object> page = pageProvider.getPageSource(path);
        System.out.println(JSON.serialize(page, true));

        Map<String, Object> delta = pageProvider.getPageDelta(path, page);
        System.out.println(JSON.serialize(delta, true));

        IResource resource = VirtualFileSystem.instance().getResource(path);
        Map<String, Object> map = JsonTool.parseBeanFromResource(resource, Map.class);
        assertEquals(JSON.serialize(map, true), JSON.serialize(delta, true));
    }

    @Test
    public void testGenPage() {
        String path = "/nop/auth/pages/NopAuthOpLog/main.page.yaml";
        Map<String, Object> page = pageProvider.getPageSource(path);
        System.out.println(JSON.serialize(page, true));

        String text = JsonTool.serialize(page, true);
        assertFalse(text.contains("session.null"));
    }

    @Test
    public void testExtAttributes() {
        String path = "/nop/auth/pages/NopAuthRole/main.page.yaml";
        Map<String, Object> page = pageProvider.getPageSource(path);
        System.out.println(JSON.serialize(page, true));

        String text = JsonTool.serialize(page, true);
        assertTrue(text.contains("xui:permissions"));
    }
}