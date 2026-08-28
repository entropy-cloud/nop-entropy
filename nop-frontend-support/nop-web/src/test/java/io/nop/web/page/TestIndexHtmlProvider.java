/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.web.page;

import io.nop.api.core.config.AppConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestIndexHtmlProvider extends JunitBaseTestCase {
    @Inject
    IndexHtmlProvider indexHtmlProvider;

    @Test
    public void testLoadSingleExtension() {
        // 配置单个扩展
        AppConfig.getConfigProvider().assignConfigValue(
                "nop.web.index-extensions-dir", "/nop/test/extensions");
        AppConfig.getConfigProvider().assignConfigValue(
                "nop.web.index-extension-names", "test-extension");
        AppConfig.getConfigProvider().assignConfigValue(
                "nop.web.index-extensions-base-path", "/extensions");

        // 清除缓存
        indexHtmlProvider.invalidateCache();

        // 获取扩展元数据
        List<ExtensionMeta> metas = indexHtmlProvider.getExtensionMetas("/nop/test/extensions");

        assertNotNull(metas);
        assertEquals(1, metas.size());

        ExtensionMeta ext = metas.get(0);
        assertEquals("test-extension", ext.getId());
        assertEquals("Test Extension", ext.getName());
        assertEquals("1.0.0", ext.getVersion());
        assertEquals("./assets/index.js", ext.getEntry());
        assertNotNull(ext.getStyleAssets());
        assertEquals(1, ext.getStyleAssets().size());
        assertEquals("./assets/style.css", ext.getStyleAssets().get(0));
    }

    @Test
    public void testLoadMultipleExtensions() {
        // 配置多个扩展
        AppConfig.getConfigProvider().assignConfigValue(
                "nop.web.index-extensions-dir", "/nop/test/extensions");
        AppConfig.getConfigProvider().assignConfigValue(
                "nop.web.index-extension-names", "test-extension,another-extension");
        AppConfig.getConfigProvider().assignConfigValue(
                "nop.web.index-extensions-base-path", "/extensions");

        // 清除缓存
        indexHtmlProvider.invalidateCache();

        // 获取扩展元数据
        List<ExtensionMeta> metas = indexHtmlProvider.getExtensionMetas("/nop/test/extensions");

        assertNotNull(metas);
        assertEquals(2, metas.size());

        // 验证第一个扩展
        ExtensionMeta ext1 = metas.get(0);
        assertEquals("test-extension", ext1.getId());
        assertEquals("Test Extension", ext1.getName());

        // 验证第二个扩展
        ExtensionMeta ext2 = metas.get(1);
        assertEquals("another-extension", ext2.getId());
        assertEquals("Another Extension", ext2.getName());
        assertEquals(2, ext2.getStyleAssets().size());
    }

    @Test
    public void testGenerateHtml() {
        // 配置扩展
        AppConfig.getConfigProvider().assignConfigValue(
                "nop.web.index-extensions-dir", "/nop/test/extensions");
        AppConfig.getConfigProvider().assignConfigValue(
                "nop.web.index-extension-names", "test-extension");
        AppConfig.getConfigProvider().assignConfigValue(
                "nop.web.index-extensions-base-path", "/extensions");

        // 清除缓存
        indexHtmlProvider.invalidateCache();

        // 获取HTML
        String html = indexHtmlProvider.getIndexHtml();

        // 验证HTML包含扩展引用，并携带前端 DOM 扫描锚点 data-nop-extension / data-nop-extension-id
        assertTrue(html.contains(
                "<link rel=\"stylesheet\" data-nop-extension data-nop-extension-id=\"test-extension\" href=\"/extensions/test-extension/assets/style.css\" />"));
        assertTrue(html.contains(
                "<script type=\"module\" data-nop-extension data-nop-extension-id=\"test-extension\" src=\"/extensions/test-extension/assets/index.js\"></script>"));
    }

    @Test
    public void testExtensionNotFound() {
        // 配置不存在的扩展
        AppConfig.getConfigProvider().assignConfigValue(
                "nop.web.index-extensions-dir", "/nop/test/extensions");
        AppConfig.getConfigProvider().assignConfigValue(
                "nop.web.index-extension-names", "non-existent-extension");

        // 清除缓存
        indexHtmlProvider.invalidateCache();

        // 获取扩展元数据
        List<ExtensionMeta> metas = indexHtmlProvider.getExtensionMetas("/nop/test/extensions");

        assertNotNull(metas);
        assertTrue(metas.isEmpty());
    }

    @Test
    public void testNoExtensionNamesConfigured() {
        // 不配置扩展名称
        AppConfig.getConfigProvider().assignConfigValue(
                "nop.web.index-extensions-dir", "/nop/test/extensions");
        AppConfig.getConfigProvider().assignConfigValue(
                "nop.web.index-extension-names", "");

        // 清除缓存
        indexHtmlProvider.invalidateCache();

        // 获取扩展元数据
        List<ExtensionMeta> metas = indexHtmlProvider.getExtensionMetas("/nop/test/extensions");

        assertNotNull(metas);
        assertTrue(metas.isEmpty());
    }

    @Test
    public void testCacheInvalidation() {
        // 配置扩展
        AppConfig.getConfigProvider().assignConfigValue(
                "nop.web.index-extensions-dir", "/nop/test/extensions");
        AppConfig.getConfigProvider().assignConfigValue(
                "nop.web.index-extension-names", "test-extension");

        // 清除缓存
        indexHtmlProvider.invalidateCache();

        // 第一次加载
        List<ExtensionMeta> metas1 = indexHtmlProvider.getExtensionMetas("/nop/test/extensions");
        assertNotNull(metas1);
        assertEquals(1, metas1.size());

        // 第二次加载（应该使用缓存）
        List<ExtensionMeta> metas2 = indexHtmlProvider.getExtensionMetas("/nop/test/extensions");
        assertNotNull(metas2);
        assertEquals(1, metas2.size());

        // 验证是同一个对象（缓存生效）
        assertSame(metas1, metas2);

        // 清除缓存
        indexHtmlProvider.invalidateCache();

        // 第三次加载（应该重新加载）
        List<ExtensionMeta> metas3 = indexHtmlProvider.getExtensionMetas("/nop/test/extensions");
        assertNotNull(metas3);
        assertEquals(1, metas3.size());

        // 验证是不同对象（缓存已清除）
        assertNotSame(metas1, metas3);
    }

    @Test
    public void testFallbackToClassPathResource() {
        // extensions-dir 指向 VFS 中不存在的目录，
        // 但 META-INF/resources/extensions/static-extension/extension.json 存在于 classpath：
        // 应该 fallback 到 classpath 读取。
        AppConfig.getConfigProvider().assignConfigValue(
                "nop.web.index-extensions-dir", "/extensions");
        AppConfig.getConfigProvider().assignConfigValue(
                "nop.web.index-extension-names", "static-extension");
        AppConfig.getConfigProvider().assignConfigValue(
                "nop.web.index-extensions-base-path", "/extensions");

        // 清除缓存
        indexHtmlProvider.invalidateCache();

        // 通过 getIndexHtml() 触发完整加载路径
        String html = indexHtmlProvider.getIndexHtml();

        // 占位符被替换，且 classpath fallback 的扩展被注入
        assertTrue(html.contains("<link rel=\"stylesheet\" data-nop-extension"
                + " data-nop-extension-id=\"static-extension\" href=\"/extensions/static-extension/assets/static.css\" />"));
        assertTrue(html.contains("<script type=\"module\" data-nop-extension"
                + " data-nop-extension-id=\"static-extension\" src=\"/extensions/static-extension/assets/static.js\"></script>"));
    }
}