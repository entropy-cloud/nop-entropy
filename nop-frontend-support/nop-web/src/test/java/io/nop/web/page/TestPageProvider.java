/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.web.page;

import io.nop.api.core.json.JSON;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.i18n.JsonI18nHelper;
import io.nop.core.lang.json.DeltaJsonOptions;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.json.delta.DeltaJsonLoader;
import io.nop.core.lang.json.delta.DeltaJsonSaver;
import io.nop.core.lang.json.delta.JsonDiffer;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.deps.ResourceDependencySet;
import io.nop.xlang.xdsl.json.XJsonLoader;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestPageProvider extends JunitBaseTestCase {
    @Inject
    PageProvider pageProvider;

    @Test
    public void testXmlPage() {
        Map<String, Object> page = pageProvider.getPage("/nop/test/pages/test.page.xml", "");
        assertEquals(attachmentJsonText("test.result.json"), JsonTool.serialize(page, true));
    }

    @Test
    public void testSaveDelta() {
        Map<String, Object> page = pageProvider.getPageSource("/nop/test/pages/test.page.xml");
        System.out.println("merged=\n" + JSON.serialize(page, true));

        JsonI18nHelper.i18nKeyToBindExpr(page);
        IResource resource = VirtualFileSystem.instance().getResource("/nop/test/pages/test.page.xml");
        DeltaJsonOptions options = XJsonLoader.newOptions(null);
        options.setCleaner(WebPageHelper::removeNullEntry);
        Map<String, Object> diff = DeltaJsonSaver.INSTANCE.getJsonDelta(resource, page, options);
        System.out.println("diff=\n" + JSON.serialize(diff, true));
        assertEquals(attachmentJsonText("delta.page.json"), JSON.serialize(diff, true));

        // 根据差量数据重建的对象
        Map<String, Object> resolved = (Map<String, Object>) DeltaJsonLoader.instance().resolveExtends(diff, options);
        WebPageHelper.removeNullEntry(resolved);

        System.out.print("resolved=\n" + JSON.serialize(resolved, true));
        assertEquals(JSON.serialize(page, true), JSON.serialize(resolved, true));
    }

    @Test
    public void testI18nKeyToBindExpr() {
        Map<String, Object> page = attachmentBean("test.page.json", Map.class);
        JsonI18nHelper.i18nKeyToBindExpr(page);
        System.out.println(JSON.serialize(page, true));
        assertEquals(attachmentJsonText("test_no_i18n_key.page.json"), JSON.serialize(page, true));
    }

    @Test
    public void testBak() {
        Map<String, Object> page = pageProvider.getPageSource("/nop/test/pages/test.page.yaml");
        System.out.println("merged=\n" + JSON.serialize(page, true));
        pageProvider.savePageSource("/nop/test/pages/test.page.yaml", page);

        IResource resource = VirtualFileSystem.instance().getResource("/nop/test/pages/test.page.yaml.bak");
        assertEquals("test: a", ResourceHelper.readText(resource));
    }

    @Test
    public void testDependsSet() {
        String path = "/nop/test/pages/test.page.xml";
        pageProvider.getPage(path, "zh-CN");
        ResourceDependencySet deps = ResourceComponentManager.instance().getModelDepends("zh-CN|" + path);
        String depsText = ResourceComponentManager.instance().dumpDependsSet(deps);
        System.out.println(depsText);
        assertEquals(normalizeCRLF(attachmentText("test.page.deps").trim()), normalizeCRLF(depsText.trim()));
    }

    @Test
    public void testGenActions() {
        String path = "/nop/test/pages/test-actions.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, "zh-CN");
        String str = JSON.serialize(page, true);
        System.out.println(str);
        assertEquals(attachmentJsonText("test-actions.result.json"), str);
    }

    @Test
    public void testChangeColName() {
        Map<String, Object> base = attachmentBean("col/old-col.json", Map.class);
        Map<String, Object> json = attachmentBean("col/new-col.json", Map.class);

        Map<String, Object> diff = JsonDiffer.instance().diffMap(json, base);
        System.out.println(JsonTool.serialize(diff, true));
        assertEquals(attachmentJsonText("delta-col.json"), JsonTool.serialize(diff, true));
    }

    @Test
    public void testColLabelChange() {
        Map<String, Object> json = pageProvider.getPageSource("/nop/test/pages/test-col-label.page.yaml");
        System.out.println(JsonTool.serialize(json, true));
        assertEquals(attachmentJsonText("merged-col-label.json"), JsonTool.serialize(json, true));

        json = pageProvider.getPage("/nop/test/pages/test-col-label.page.yaml", null);
        System.out.println(JsonTool.serialize(json, true));
        assertEquals(attachmentJsonText("merged-col-label-resolved.json"), JsonTool.serialize(json, true));
    }

    @Test
    public void testXuiImport() {
        Map<String, Object> json = pageProvider.getPageSource("/nop/test/pages/test-xui-import.page.yaml");
        System.out.println(JsonTool.serialize(json, true));
        String jsonText = JsonTool.serialize(json, true);
        assertTrue(jsonText.contains("/nop/test/pages/test.lib.js"));
    }
}
