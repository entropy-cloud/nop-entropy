/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
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
