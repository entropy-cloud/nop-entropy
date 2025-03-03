/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dyn.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.core.resource.ResourceHelper;
import io.nop.dyn.dao.NopDynDaoConstants;
import io.nop.dyn.dao.entity._gen._NopDynModule;


@BizObjName("NopDynModule")
public class NopDynModule extends _NopDynModule {

    public String getNopModuleId() {
        return ResourceHelper.getModuleIdFromModuleName(getModuleName());
    }

    public boolean isPublished(){
        Integer status = getStatus();
        if(status == null)
            return false;

        return NopDynDaoConstants.MODULE_STATUS_PUBLISHED == status;
    }
}
