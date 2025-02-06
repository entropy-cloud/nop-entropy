package io.nop.core.resource.tenant;

import io.nop.core.resource.IResourceStore;

import java.util.Set;

public interface ITenantResourceProvider {
    Set<String> getUsedTenantIds();

    IResourceStore getTenantResourceStore(String tenantId);

    void clearForTenant(String tenantId);
}
