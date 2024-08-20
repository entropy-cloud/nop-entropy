package io.nop.core.resource.tenant;

import io.nop.core.module.ModuleModel;

import java.util.Map;

public interface ITenantModuleDiscovery {

    Map<String, ModuleModel> getEnabledTenantModules();

    default boolean isEnabledTenantModule(String moduleName) {
        return getEnabledTenantModules().containsKey(moduleName);
    }

    /**
     * 检查在租户模块中是否定义了业务对象。如果业务对象存在，则生成相关资源文件
     */
    boolean checkBizObjExists(String bizObjName);
}
