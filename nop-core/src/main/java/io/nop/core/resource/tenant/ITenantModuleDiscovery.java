package io.nop.core.resource.tenant;

import io.nop.core.module.ModuleModel;

import java.util.Map;

public interface ITenantModuleDiscovery {

    Map<String, ModuleModel> getEnabledTenantModules();

    default boolean isEnabledTenantModule(String moduleName) {
        return getEnabledTenantModules().containsKey(moduleName);
    }

}
