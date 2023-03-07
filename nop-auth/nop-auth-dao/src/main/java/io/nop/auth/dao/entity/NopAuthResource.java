/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.dao.entity._gen._NopAuthResource;

@BizObjName("NopAuthResource")
public class NopAuthResource extends _NopAuthResource {
    public NopAuthResource() {
    }

    public NopAuthResource getRoot() {
        NopAuthResource parent = getParent();
        if (parent == null)
            return this;
        return parent.getRoot();
    }

    public boolean isTopMenu() {
        return AuthApiConstants.RESOURCE_TYPE_TOP_MENU.equals(getResourceType());
    }
}
