/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.dev;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.annotations.directive.Auth;
import io.nop.commons.cache.GlobalCacheRegistry;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;

/**
 * 破坏性维护操作（清空组件缓存/刷新虚拟文件系统会引发全量缓存失效），
 * 因此所有操作均要求admin角色（auth==null时平台按公开访问处理）。[G5-13-01]
 */
@Locale("zh-CN")
@BizModel("DevTool")
public class DevToolBizModel {

    @Inject
    IGraphQLEngine graphQLEngine;

    @BizMutation
    @Auth(roles = "admin")
    @Description("清空组件缓存")
    public void clearComponentCache() {
        refreshVirtualFileSystem();
        ResourceComponentManager.instance().clearAllCache();
        GlobalCacheRegistry.instance().clearAllCache();
        graphQLEngine.clearCache();
    }

    @BizMutation
    @Auth(roles = "admin")
    @Description("刷新虚拟文件系统")
    public void refreshVirtualFileSystem() {
        VirtualFileSystem.instance().refresh(true);
    }
}
