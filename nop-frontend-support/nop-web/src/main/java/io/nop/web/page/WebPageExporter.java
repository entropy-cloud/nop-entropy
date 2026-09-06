/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.web.page;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.concurrent.executor.ExecutorHelper;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.util.DateHelper;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.module.ModuleManager;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.web.WebConfigs;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

/**
 * 将 VFS 中的页面批量渲染导出为 JSON 文件，并产出 manifest.json 清单。
 * <p>
 * 导出语义与生产 getPage 对齐：flux 模式下应用 flux.yaml 回退（见 {@link PageProvider#resolveFluxFallback}）；
 * 解析器选项缺省启用（{@link PageExportOptions}）。单页失败被收集到 failedPages 而不中断整体导出。
 */
public class WebPageExporter {

    public static final String MANIFEST_FILE_NAME = "manifest.json";

    private final PageProvider pageProvider;

    public WebPageExporter() {
        this(new PageProvider());
    }

    public WebPageExporter(PageProvider pageProvider) {
        this.pageProvider = pageProvider;
    }

    public PageExportResult exportPages(PageExportOptions options, File targetDir) {
        if (!StringHelper.isEmpty(options.getRenderMode())) {
            // 先改配置再清缓存：缓存键 locale|path 不含渲染模式，web.xlib 编译产物含模式分支
            AppConfig.getConfigProvider().updateConfigValue(WebConfigs.CFG_WEB_RENDER_MODE, options.getRenderMode());
            ResourceComponentManager.instance().clearCache("xlib");
            ResourceComponentManager.instance().clearCache("xpage");
        }

        List<IResource> resources = findPageResources(options);

        Queue<PageExportResult.ErrorItem> failedPages = new ConcurrentLinkedQueue<>();
        List<String> pages = new CopyOnWriteArrayList<>();

        PageRenderOptions renderOptions = options.toRenderOptions();
        runParallel(options.getThreadCount(), resources, resource -> {
            try {
                pageProvider.renderPageTo(resource, renderOptions, targetDir);
                pages.add(toOutputPath(resource.getPath()));
            } catch (Exception e) {
                failedPages.add(toErrorItem(resource.getPath(), e));
            }
        });

        List<String> sortedPages = new ArrayList<>(pages);
        sortedPages.sort(Comparator.naturalOrder());
        List<PageExportResult.ErrorItem> sortedFailures = new ArrayList<>(failedPages);
        sortedFailures.sort(Comparator.comparing(PageExportResult.ErrorItem::getPath));

        File manifestFile = new File(targetDir, MANIFEST_FILE_NAME);
        FileHelper.writeText(manifestFile, JsonTool.serialize(buildManifest(options, sortedPages, sortedFailures), true), null);

        return new PageExportResult(sortedPages, sortedFailures, manifestFile);
    }

    private List<IResource> findPageResources(PageExportOptions options) {
        List<IResource> resources = new ArrayList<>();
        String pattern = options.getPattern();
        String moduleId = options.getModuleId();
        if (!StringHelper.isEmpty(moduleId)) {
            resources.addAll(VirtualFileSystem.instance().findAll("/" + moduleId, pattern));
        } else {
            // 与 validateAllPages 同集合语义：仅枚举 enabled modules，不扫描整个 VFS
            ModuleManager.instance().getEnabledModules(true).forEach(module ->
                    resources.addAll(VirtualFileSystem.instance().findAll("/" + module.getModuleId(), pattern)));
        }
        resources.sort(Comparator.comparing(IResource::getPath));
        return resources;
    }

    private void runParallel(int threadCount, List<IResource> resources, Consumer<IResource> action) {
        if (threadCount <= 1) {
            for (IResource resource : resources) {
                action.accept(resource);
            }
            return;
        }
        Semaphore semaphore = new Semaphore(threadCount);
        Executor executor = GlobalExecutors.globalWorker();
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (IResource resource : resources) {
            futures.add(ExecutorHelper.throttleExecute(executor, semaphore, () -> action.accept(resource)));
        }
        FutureHelper.syncGet(FutureHelper.waitAll(futures));
    }

    static String toOutputPath(String resourcePath) {
        return StringHelper.replaceFileExt(resourcePath, "json").substring(1);
    }

    static PageExportResult.ErrorItem toErrorItem(String resourcePath, Exception e) {
        Throwable root = NopException.getErrorMessageManager().getRealCause(e);
        String errorCode = root instanceof NopException ? String.valueOf(((NopException) root).getErrorCode()) : "";
        String message = root.toString();
        return new PageExportResult.ErrorItem(resourcePath, errorCode, message);
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String errorCode = root instanceof NopException ? String.valueOf(((NopException) root).getErrorCode()) : "";
        return new PageExportResult.ErrorItem(resourcePath, errorCode, root.getMessage());
    }

    private Map<String, Object> buildManifest(PageExportOptions options, List<String> pages,
                                              List<PageExportResult.ErrorItem> failedPages) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("generatedAt", DateHelper.formatDateTime(CoreMetrics.currentDateTime(), "yyyy-MM-dd HH:mm:ss"));
        manifest.put("renderMode", WebConfigs.CFG_WEB_RENDER_MODE.get());
        manifest.put("pattern", options.getPattern());
        manifest.put("locale", StringHelper.isEmpty(options.getLocale()) ? AppConfig.defaultLocale() : options.getLocale());
        manifest.put("pageCount", pages.size());
        manifest.put("pages", pages);

        List<Map<String, Object>> failures = new ArrayList<>(failedPages.size());
        for (PageExportResult.ErrorItem item : failedPages) {
            Map<String, Object> failure = new LinkedHashMap<>();
            failure.put("path", item.getPath());
            failure.put("errorCode", item.getErrorCode());
            failure.put("message", item.getMessage());
            failures.add(failure);
        }
        manifest.put("failedPages", failures);
        return manifest;
    }
}
