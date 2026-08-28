/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.web.page;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.cache.GlobalCacheRegistry;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.cache.CacheEntryManagement;
import io.nop.core.resource.cache.ResourceCacheEntry;
import io.nop.core.resource.impl.ClassPathResource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;

import java.util.ArrayList;
import java.util.List;

import static io.nop.web.WebConfigs.*;

/**
 * 提供index.html内容，支持标题替换和扩展注入
 */
public class IndexHtmlProvider {
    static final Logger LOG = LoggerFactory.getLogger(IndexHtmlProvider.class);
    static final SourceLocation s_loc = SourceLocation.fromClass(IndexHtmlProvider.class);

    static final String PLACEHOLDER = "<!--NOP_EXTENSIONS_INJECT-->";
    static final String INDEX_HTML_PATH = "classpath:META-INF/resources/index.html";
    static final String TITLE_TAG_START = "<title>";
    static final String TITLE_TAG_END = "</title>";
    static final String EXTENSION_JSON = "extension.json";

    private static final String CACHE_NAME = "nop-web-extension-meta-cache";
    private static final Type EXTENSION_META_TYPE = ExtensionMeta.class;

    private final ResourceCacheEntry<List<ExtensionMeta>> extensionCache =
            new ResourceCacheEntry<>(CACHE_NAME);
    private final CacheEntryManagement<List<ExtensionMeta>> cacheManagement =
            new CacheEntryManagement<>(CACHE_NAME, extensionCache);

    @PostConstruct
    public void init() {
        GlobalCacheRegistry.instance().register(cacheManagement);
    }

    @PreDestroy
    public void destroy() {
        GlobalCacheRegistry.instance().unregister(cacheManagement);
        extensionCache.clear();
    }

    public String getIndexHtml() {
        IResource resource = VirtualFileSystem.instance().getResource(INDEX_HTML_PATH);
        String html = ResourceHelper.readText(resource);

        String title = CFG_WEB_INDEX_TITLE.get();
        if (!StringHelper.isEmpty(title))
            html = replaceTitle(html, title);

        String extensionsHtml = buildExtensionsHtml();
        if (!StringHelper.isEmpty(extensionsHtml)) {
            html = html.replace(PLACEHOLDER, extensionsHtml);
        }

        return html;
    }

    private String replaceTitle(String html, String title) {
        int start = html.indexOf(TITLE_TAG_START);
        if (start < 0)
            return html;
        int end = html.indexOf(TITLE_TAG_END, start);
        if (end < 0)
            return html;
        String resolved = StringHelper.renderTemplate(title, AppConfig::var);
        return html.substring(0, start + TITLE_TAG_START.length()) + resolved + html.substring(end);
    }

    private String buildExtensionsHtml() {
        String extensionsDir = CFG_WEB_INDEX_EXTENSIONS_DIR.get();
        if (StringHelper.isEmpty(extensionsDir))
            return "";

        return loadExtensionsFromDir(extensionsDir);
    }

    private String loadExtensionsFromDir(String extensionsDir) {
        List<ExtensionMeta> extensions = getExtensionMetas(extensionsDir);
        if (extensions == null || extensions.isEmpty())
            return "";

        String basePath = CFG_WEB_INDEX_EXTENSIONS_BASE_PATH.get();
        if (StringHelper.isEmpty(basePath))
            basePath = "/extensions";

        StringBuilder sb = new StringBuilder();
        for (ExtensionMeta ext : extensions) {
            appendExtensionHtml(sb, basePath, ext);
        }
        return sb.toString();
    }

    private void appendExtensionHtml(StringBuilder sb, String basePath, ExtensionMeta ext) {
        String extensionBasePath = basePath + "/" + ext.getId();
        String extensionId = ext.getId();

        // 添加样式文件。data-nop-extension / data-nop-extension-id 是前端 DOM 扫描锚点，
        // apps/main/src/extensions/config.ts 的 getDomExtensionSources() 通过这两个属性
        // 识别由本 Java IndexHtmlProvider 注入的扩展资源。
        if (ext.getStyleAssets() != null) {
            for (String cssPath : ext.getStyleAssets()) {
                sb.append("<link rel=\"stylesheet\" data-nop-extension data-nop-extension-id=\"")
                        .append(extensionId)
                        .append("\" href=\"")
                        .append(extensionBasePath)
                        .append("/")
                        .append(normalizePath(cssPath))
                        .append("\" />\n");
            }
        }

        // 添加入口JS文件。
        if (!StringHelper.isEmpty(ext.getEntry())) {
            sb.append("<script type=\"module\" data-nop-extension data-nop-extension-id=\"")
                    .append(extensionId)
                    .append("\" src=\"")
                    .append(extensionBasePath)
                    .append("/")
                    .append(normalizePath(ext.getEntry()))
                    .append("\"></script>\n");
        }
    }

    private String normalizePath(String path) {
        if (path == null)
            return "";
        // 移除开头的./
        if (path.startsWith("./"))
            return path.substring(2);
        return path;
    }

    List<ExtensionMeta> getExtensionMetas(String extensionsDir) {
        List<ExtensionMeta> cached = extensionCache.getNow();
        if (cached != null)
            return cached;

        return extensionCache.getObject(true, path -> loadAllExtensionMetas(extensionsDir));
    }

    private List<ExtensionMeta> loadAllExtensionMetas(String extensionsDir) {
        String enabledNames = CFG_WEB_INDEX_EXTENSION_NAMES.get();
        if (StringHelper.isEmpty(enabledNames)) {
            LOG.debug("nop.web.index-no-extension-names-configured");
            return List.of();
        }

        String[] namesArray = StringHelper.splitToArray(enabledNames, ',');
        List<ExtensionMeta> result = new ArrayList<>(namesArray.length);

        for (String name : namesArray) {
            String trimmedName = name.trim();
            if (StringHelper.isEmpty(trimmedName))
                continue;

            ExtensionMeta meta = loadExtensionMeta(extensionsDir, trimmedName);
            if (meta != null) {
                result.add(meta);
            }
        }

        LOG.info("nop.web.loaded-extensions:count={},names={}", result.size(), enabledNames);
        return result;
    }

    private ExtensionMeta loadExtensionMeta(String extensionsDir, String extensionName) {
        String metaPath = extensionsDir + "/" + extensionName + "/" + EXTENSION_JSON;
        IResource resource = getExtensionResource(metaPath);
        if (resource == null || !resource.exists()) {
            LOG.warn("nop.web.extension-meta-not-found:path={}", metaPath);
            return null;
        }

        try {
            String content = ResourceHelper.readText(resource);
            return JsonTool.parseBeanFromText(content, EXTENSION_META_TYPE);
        } catch (Exception e) {
            LOG.error("nop.web.load-extension-meta-fail:path={}", metaPath, e);
            return null;
        }
    }

    /**
     * 优先从 VFS 中查找扩展资源（允许通过 Delta 定制机制覆盖），
     * VFS 中不存在时再从 classpath 直接查找。
     *
     * classpath 查找顺序：
     * 1. {@code META-INF/resources/}{metaPath} —— 生产环境静态资源标准部署位置
     *    （Spring/Quarkus 默认把 {@code classpath:/META-INF/resources/**} 暴露为
     *    {@code /}，与 {@code /extensions/{id}/...} HTTP 路径对齐）。
     * 2. {@code classpath:}{metaPath} —— 允许其它约定（如扩展直接放在 classpath 根）。
     */
    IResource getExtensionResource(String metaPath) {
        // 优先从 VFS 查找（允许 Delta 定制覆盖）。returnNullIfNotExists=true：VFS 中不存在时返回 null
        // 而不是抛异常，从而允许 fallback 到 classpath。
        IResource resource = VirtualFileSystem.instance().getResource(metaPath, true);
        if (resource != null && resource.exists())
            return resource;

        // 未通过 VFS 定制时，直接访问 classpath 上的资源
        String rel = stripLeadingSlash(metaPath);
        IResource staticResource = new ClassPathResource("classpath:META-INF/resources/" + rel);
        if (staticResource.exists())
            return staticResource;

        return new ClassPathResource("classpath:" + rel);
    }

    private String stripLeadingSlash(String path) {
        while (path.startsWith("/"))
            path = path.substring(1);
        return path;
    }

    public void invalidateCache() {
        extensionCache.clear();
    }
}