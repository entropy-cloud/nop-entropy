package io.nop.core.resource.tenant;

public interface IResourceTenantChecker {
    boolean isEnableTenant();

    /**
     * 此资源路径是否支持租户定制。如果支持，则加载结果需要使用租户专用的缓存
     */
    boolean isSupportTenant(String resourcePath);
}
