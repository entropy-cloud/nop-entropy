package io.nop.dev.service.biz;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.dev.core.module.DevModuleDiscovery;
import io.nop.dev.core.module.DevModuleResourcePaths;

import java.util.List;

@BizModel("DevModule")
public class DevModuleBizModel {

    @BizQuery
    public List<DevModuleResourcePaths> getModules() {
        DevModuleDiscovery discovery = new DevModuleDiscovery();
        String rootPath = "dev:/";
        return discovery.discover(rootPath);
    }
}
