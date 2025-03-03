package io.nop.web.page;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.core.lang.eval.IEvalFunction;

@DataBean
public class PageRenderOptions {
    private String moduleId;
    private String pattern;

    private String locale;

    private boolean useResolver;
    private boolean resolveI18n;

    private boolean transformPermissions;

    private int threadCount;

    private IEvalFunction postProcess;

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

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public boolean isResolveI18n() {
        return resolveI18n;
    }

    public void setResolveI18n(boolean resolveI18n) {
        this.resolveI18n = resolveI18n;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    public boolean isUseResolver() {
        return useResolver;
    }

    public void setUseResolver(boolean useResolver) {
        this.useResolver = useResolver;
    }

    public boolean isTransformPermissions() {
        return transformPermissions;
    }

    public void setTransformPermissions(boolean transformPermissions) {
        this.transformPermissions = transformPermissions;
    }

    public IEvalFunction getPostProcess() {
        return postProcess;
    }

    public void setPostProcess(IEvalFunction postProcess) {
        this.postProcess = postProcess;
    }
}
