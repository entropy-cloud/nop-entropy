/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.web.page;

import io.nop.api.core.annotations.data.DataBean;

/**
 * 页面批量导出选项。缺省值对齐生产 getPage 语义（{@code PageProviderBizModel__getPage} 恒定启用
 * i18n/cfg/load 解析器与权限转换），保证导出产物即浏览器实际消费的页面 JSON；
 * threadCount 缺省 1，与 validateAllPages 等全部既有全量页面路径的串行形态一致。
 */
@DataBean
public class PageExportOptions {
    private String moduleId;

    private String pattern = "pages/*/*.page.yaml";

    /**
     * 非空时导出前切换 nop.web.render-mode 并清 xlib/xpage 缓存。导出后不自动恢复，由调用方负责。
     */
    private String renderMode;

    private String locale;

    private boolean useResolver = true;

    private boolean resolveI18n = true;

    private boolean transformPermissions = true;

    private int threadCount = 1;

    public String getModuleId() {
        return moduleId;
    }

    public void setModuleId(String moduleId) {
        this.moduleId = moduleId;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public String getRenderMode() {
        return renderMode;
    }

    public void setRenderMode(String renderMode) {
        this.renderMode = renderMode;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public boolean isUseResolver() {
        return useResolver;
    }

    public void setUseResolver(boolean useResolver) {
        this.useResolver = useResolver;
    }

    public boolean isResolveI18n() {
        return resolveI18n;
    }

    public void setResolveI18n(boolean resolveI18n) {
        this.resolveI18n = resolveI18n;
    }

    public boolean isTransformPermissions() {
        return transformPermissions;
    }

    public void setTransformPermissions(boolean transformPermissions) {
        this.transformPermissions = transformPermissions;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    public PageRenderOptions toRenderOptions() {
        PageRenderOptions renderOptions = new PageRenderOptions();
        renderOptions.setLocale(locale);
        renderOptions.setUseResolver(useResolver);
        renderOptions.setResolveI18n(resolveI18n);
        renderOptions.setTransformPermissions(transformPermissions);
        renderOptions.setThreadCount(threadCount);
        return renderOptions;
    }
}
