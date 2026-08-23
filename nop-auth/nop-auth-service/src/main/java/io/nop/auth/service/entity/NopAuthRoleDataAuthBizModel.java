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
import io.nop.auth.dao.entity.NopAuthRoleDataAuth;
import io.nop.auth.biz.INopAuthRoleDataAuthBiz;
import io.nop.auth.service.auth.DefaultDataAuthChecker;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

@BizModel("NopAuthRoleDataAuth")
public class NopAuthRoleDataAuthBizModel extends CrudBizModel<NopAuthRoleDataAuth> implements INopAuthRoleDataAuthBiz {
    /**
     * 表模式数据权限行变更后必须重建 DataAuthModel 缓存：modelCache 默认无 TTL、check-changed
     * 只探测资源文件变化，不清理会导致表内收紧/放宽的权限永不生效（对齐
     * NopAuthResourceBizModel → siteMapProvider.refreshCache() 先例）。
     */
    @Inject
    @Nullable
    protected DefaultDataAuthChecker dataAuthChecker;

    public NopAuthRoleDataAuthBizModel(){
        setEntityName(NopAuthRoleDataAuth.class.getName());
    }

    @BizAction
    @Override
    protected void afterEntityChange(@Name("entity") NopAuthRoleDataAuth entity, @Name("action") String action,
                                     IServiceContext context) {
        super.afterEntityChange(entity, action, context);
        if (dataAuthChecker != null)
            dataAuthChecker.clearCache();
    }
}
