package io.nop.core.module;

import java.util.Map;

public interface ITenantModuleDiscovery {

    Map<String, ModuleModel> getEnabledTenantModules();

    default boolean isEnabledTenantModule(String moduleName) {
        return getEnabledTenantModules().containsKey(moduleName);
    }

}
