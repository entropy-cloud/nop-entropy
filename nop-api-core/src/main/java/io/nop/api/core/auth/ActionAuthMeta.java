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
import io.nop.api.core.util.MultiCsvSet;

import java.util.Set;

@ImmutableBean
@DataBean
public class ActionAuthMeta {
    private final Set<String> roles;
    private final MultiCsvSet permissions;

    public ActionAuthMeta(@JsonProperty("roles") Set<String> roles,
                          @JsonProperty("permissions") MultiCsvSet permissions
    ) {
        this.roles = roles;
        this.permissions = permissions;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public MultiCsvSet getPermissions() {
        return permissions;
    }
}