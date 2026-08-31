package io.nop.web.page;

import io.nop.api.core.config.AppConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.util.FileHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.web.WebConfigs;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WebPageExporter 导出语义验证：flux.yaml 回退等价（导出内容 == flux 模式 getPage 返回）、
 * manifest 结构与计数一致、单页失败被收集不中断。参见跨仓设计
 * nop-app-erp docs/architecture/flux-page-export-and-validation.md（D1/D2/D5）。
 */
public class TestWebPageExporter extends JunitBaseTestCase {

    @Inject
    PageProvider pageProvider;

    private static final String FALLBACK_PAGE_PATH = "/nop/test/pages/test-flux-fallback/main.page.yaml";

    @AfterEach
    public void tearDownMode() {
        setRenderMode("amis");
    }

    private void setRenderMode(String mode) {
        AppConfig.getConfigProvider().updateConfigValue(WebConfigs.CFG_WEB_RENDER_MODE, mode);
        ResourceComponentManager.instance().clearCache("xpage");
        ResourceComponentManager.instance().clearCache("xlib");
    }

    private PageExportOptions options(String pattern) {
        PageExportOptions options = new PageExportOptions();
        options.setModuleId("nop/test");
        options.setPattern(pattern);
        options.setRenderMode("flux");
        options.setLocale("zh-CN");
        return options;
    }

    @Test
    public void testFluxFallbackExportMatchesGetPage() {
        File targetDir = getTargetFile("web-page-exporter-test/fallback");
        PageExportResult result = new WebPageExporter(pageProvider).exportPages(
                options("pages/test-flux-fallback/*.page.yaml"), targetDir);

        assertEquals(1, result.getPages().size());
        assertEquals(0, result.getFailedPages().size());

        File exported = new File(targetDir, "nop/test/pages/test-flux-fallback/main.page.json");
        assertTrue(exported.exists(), "输出文件应按原始 page.yaml 路径落盘");
        String text = FileHelper.readText(exported, null);
        assertTrue(text.contains("__RENDERED_BY_FLUX__"), "flux 模式导出应回退到 flux.yaml");
        assertFalse(text.contains("__RENDERED_BY_AMIS__"), "flux 模式导出不应加载 page.yaml 内容");

        Map<String, Object> loaded = pageProvider.getPage(FALLBACK_PAGE_PATH, "zh-CN");
        assertNotNull(loaded);
        assertEquals(JsonTool.serialize(loaded, true), text, "导出内容应与生产 getPage 完全一致");
    }

    @Test
    public void testAmisModeExportIgnoresFluxFallback() {
        PageExportOptions options = options("pages/test-flux-fallback/*.page.yaml");
        options.setRenderMode(null);
        File targetDir = getTargetFile("web-page-exporter-test/amis");
        PageExportResult result = new WebPageExporter(pageProvider).exportPages(options, targetDir);

        assertEquals(1, result.getPages().size());
        String text = FileHelper.readText(new File(targetDir, "nop/test/pages/test-flux-fallback/main.page.json"), null);
        assertTrue(text.contains("__RENDERED_BY_AMIS__"), "amis 模式导出应加载 page.yaml，忽略 flux.yaml");
    }

    @Test
    public void testManifestAndFailureCollection() {
        File targetDir = getTargetFile("web-page-exporter-test/failures");
        PageExportResult result = new WebPageExporter(pageProvider).exportPages(
                options("pages/test-export-fail/*.page.yaml"), targetDir);

        assertEquals(1, result.getPages().size());
        assertEquals(List.of("nop/test/pages/test-export-fail/good.page.json"), result.getPages());

        assertEquals(1, result.getFailedPages().size());
        PageExportResult.ErrorItem error = result.getFailedPages().get(0);
        assertEquals("/nop/test/pages/test-export-fail/broken.page.yaml", error.getPath());
        assertNotNull(error.getMessage(), "失败项应包含根因信息");

        File goodFile = new File(targetDir, "nop/test/pages/test-export-fail/good.page.json");
        assertTrue(goodFile.exists(), "单页失败不应中断整体导出");
        assertTrue(FileHelper.readText(goodFile, null).contains("__EXPORT_GOOD_PAGE__"));

        File manifestFile = result.getManifestFile();
        assertEquals(new File(targetDir, WebPageExporter.MANIFEST_FILE_NAME), manifestFile);
        Map<String, Object> manifest = (Map<String, Object>) JsonTool.parseBeanFromText(
                FileHelper.readText(manifestFile, null), Map.class);
        assertEquals(1, ((Number) manifest.get("pageCount")).intValue());
        assertEquals("flux", manifest.get("renderMode"));
        assertEquals("pages/test-export-fail/*.page.yaml", manifest.get("pattern"));
        assertEquals(1, ((List<?>) manifest.get("pages")).size());
        assertEquals(1, ((List<?>) manifest.get("failedPages")).size());
    }

    @Test
    public void testThreadedExportMatchesSerialResult() {
        PageExportOptions serial = options("pages/test-flux-fallback/*.page.yaml");
        File serialDir = getTargetFile("web-page-exporter-test/serial");
        PageExportResult serialResult = new WebPageExporter(pageProvider).exportPages(serial, serialDir);

        PageExportOptions threaded = options("pages/test-flux-fallback/*.page.yaml");
        threaded.setThreadCount(2);
        File threadedDir = getTargetFile("web-page-exporter-test/threaded");
        PageExportResult threadedResult = new WebPageExporter(pageProvider).exportPages(threaded, threadedDir);

        assertEquals(serialResult.getPages(), threadedResult.getPages());
        assertEquals(0, threadedResult.getFailedPages().size());
        assertEquals(
                FileHelper.readText(new File(serialDir, "nop/test/pages/test-flux-fallback/main.page.json"), null),
                FileHelper.readText(new File(threadedDir, "nop/test/pages/test-flux-fallback/main.page.json"), null),
                "并发导出内容应与串行一致");
    }
}
