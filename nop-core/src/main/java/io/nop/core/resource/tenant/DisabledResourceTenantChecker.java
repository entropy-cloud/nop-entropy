package io.nop.core.resource.tenant;

public class DisabledResourceTenantChecker implements IResourceTenantChecker {
    public static DisabledResourceTenantChecker INSTANCE = new DisabledResourceTenantChecker();

    @Override
    public boolean isSupportTenant(String resourcePath) {
        return false;
    }

    @Override
    public boolean isEnableTenant() {
        return false;
    }
}
