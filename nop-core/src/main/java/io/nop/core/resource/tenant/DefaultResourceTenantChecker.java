package io.nop.core.resource.tenant;

import io.nop.core.resource.ResourceConstants;

import static io.nop.core.CoreConfigs.CFG_TENANT_RESOURCE_ENABLED;

public class DefaultResourceTenantChecker implements IResourceTenantChecker {
    public static DefaultResourceTenantChecker INSTANCE = new DefaultResourceTenantChecker();

    @Override
    public boolean isSupportTenant(String resourcePath) {
        return resourcePath.startsWith(ResourceConstants.RESOLVE_PREFIX);
    }

    @Override
    public boolean isEnableTenant() {
        return CFG_TENANT_RESOURCE_ENABLED.get();
    }
}
