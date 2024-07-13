package io.nop.core.resource.tenant;

public interface IResourceTenantInitializer {
    Runnable initializeTenant(String tenantId);
}
