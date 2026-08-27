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

        // 添加样式文件
        if (ext.getStyleAssets() != null) {
            for (String cssPath : ext.getStyleAssets()) {
                sb.append("<link rel=\"stylesheet\" href=\"")
                        .append(extensionBasePath)
                        .append("/")
                        .append(normalizePath(cssPath))
                        .append("\" />\n");
            }
        }

        // 添加入口JS文件
        if (!StringHelper.isEmpty(ext.getEntry())) {
            sb.append("<script type=\"module\" src=\"")
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
        IResource resource = VirtualFileSystem.instance().getResource(metaPath);
        if (!resource.exists()) {
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

    public void invalidateCache() {
        extensionCache.clear();
    }
}