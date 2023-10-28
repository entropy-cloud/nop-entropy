/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.biz.dev;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.commons.cache.GlobalCacheRegistry;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;

@Locale("zh-CN")
@BizModel("DevTool")
public class DevToolBizModel {

    @Inject
    IGraphQLEngine graphQLEngine;

    @BizMutation
    @Description("清空组件缓存")
    public void clearComponentCache() {
        refreshVirtualFileSystem();
        ResourceComponentManager.instance().clearAllCache();
        GlobalCacheRegistry.instance().clearAllCache();
        graphQLEngine.clearCache();
    }

    @BizMutation
    @Description("刷新虚拟文件系统")
    public void refreshVirtualFileSystem() {
        VirtualFileSystem.instance().refresh(true);
    }
}
