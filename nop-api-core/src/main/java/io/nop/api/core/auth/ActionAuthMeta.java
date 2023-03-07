/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.data.ImmutableBean;

import java.util.Set;

@ImmutableBean
@DataBean
public class ActionAuthMeta {
    private final Set<String> roles;
    private final Set<String> permissions;

    public ActionAuthMeta(@JsonProperty("roles") Set<String> roles,
                          @JsonProperty("permissions") Set<String> permissions
    ) {
        this.roles = roles;
        this.permissions = permissions;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public Set<String> getPermissions() {
        return permissions;
    }
}