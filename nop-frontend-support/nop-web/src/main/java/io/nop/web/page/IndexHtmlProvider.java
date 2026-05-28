/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.web.page;

import io.nop.api.core.config.AppConfig;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XplModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.nop.web.WebConfigs.CFG_WEB_INDEX_EXTENSIONS_PATH;
import static io.nop.web.WebConfigs.CFG_WEB_INDEX_TITLE;

public class IndexHtmlProvider {
    static final Logger LOG = LoggerFactory.getLogger(IndexHtmlProvider.class);

    static final String PLACEHOLDER = "<!--NOP_EXTENSIONS_INJECT-->";
    static final String INDEX_HTML_PATH = "classpath:META-INF/resources/index.html";
    static final String TITLE_TAG_START = "<title>";
    static final String TITLE_TAG_END = "</title>";

    public String getIndexHtml() {
        IResource resource = VirtualFileSystem.instance().getResource(INDEX_HTML_PATH);
        String html = ResourceHelper.readText(resource);

        String title = CFG_WEB_INDEX_TITLE.get();
        if (!StringHelper.isEmpty(title))
            html = replaceTitle(html, title);

        String extensionsPath = CFG_WEB_INDEX_EXTENSIONS_PATH.get();
        if (StringHelper.isEmpty(extensionsPath))
            return html;

        int pos = html.indexOf(PLACEHOLDER);
        if (pos < 0)
            return html;

        String extensions = loadExtensions(extensionsPath);
        return html.replace(PLACEHOLDER, extensions);
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

    private String loadExtensions(String extensionsPath) {
        IResource resource = VirtualFileSystem.instance().getResource(extensionsPath);
        if (!resource.exists()) {
            LOG.debug("nop.web.index-extensions:extensions file not found, path={}", extensionsPath);
            return "";
        }

        String fileExt = StringHelper.fileExt(extensionsPath);
        if ("xpl".equals(fileExt)) {
            XplModel xplModel = XLang.loadTpl(extensionsPath);
            return ((ITextTemplateOutput) xplModel).generateText(XLang.newEvalScope());
        }

        return ResourceHelper.readText(resource);
    }
}
