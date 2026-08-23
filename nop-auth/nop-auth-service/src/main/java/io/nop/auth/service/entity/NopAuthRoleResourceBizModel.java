/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.entity;

import io.nop.api.core.annotations.biz.BizAction;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.core.Name;
import io.nop.auth.core.sitemap.ISiteMapProvider;
import io.nop.auth.dao.entity.NopAuthRoleResource;
import io.nop.biz.crud.CrudBizModel;
import io.nop.auth.biz.INopAuthRoleResourceBiz;
import io.nop.core.context.IServiceContext;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

@BizModel("NopAuthRoleResource")
public class NopAuthRoleResourceBizModel extends CrudBizModel<NopAuthRoleResource> implements INopAuthRoleResourceBiz {
    /**
     * 授权关系行是 SiteCacheData.permissionToRoles 的判定源，直接 CRUD 变更后刷新
     * sitemap 权限缓存（对齐 NopAuthResourceBizModel.afterEntityChange 先例）
     */
    @Inject
    @Nullable
    protected ISiteMapProvider siteMapProvider;

    public NopAuthRoleResourceBizModel() {
        setEntityName(NopAuthRoleResource.class.getName());
    }

    @BizAction
    @Override
    protected void afterEntityChange(@Name("entity") NopAuthRoleResource entity, @Name("action") String action,
                                     IServiceContext context) {
        super.afterEntityChange(entity, action, context);
        if (siteMapProvider != null)
            siteMapProvider.refreshCache();
    }
}
