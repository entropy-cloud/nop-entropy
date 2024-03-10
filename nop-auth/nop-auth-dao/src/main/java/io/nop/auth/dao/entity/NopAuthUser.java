/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.auth.dao.entity._gen._NopAuthUser;

import java.util.Set;
import java.util.stream.Collectors;

@BizObjName("NopAuthUser")
public class NopAuthUser extends _NopAuthUser {
    public NopAuthUser() {
    }

    public Set<NopAuthRole> getRoles() {
        return getRoleMappings().stream().map(NopAuthUserRole::getRole).collect(Collectors.toSet());
    }
}
