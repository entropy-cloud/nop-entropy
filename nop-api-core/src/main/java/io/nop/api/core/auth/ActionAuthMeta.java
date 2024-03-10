/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
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

    private boolean publicAccess;
    private Set<String> roles;
    private MultiCsvSet permissions;

    private boolean frozen;

    public ActionAuthMeta(@JsonProperty("publicAccess") boolean publicAccess,
                          @JsonProperty("roles") Set<String> roles,
                          @JsonProperty("permissions") MultiCsvSet permissions) {
        this.publicAccess = publicAccess;
        this.roles = roles;
        this.permissions = permissions;
        this.frozen = true;
    }

    public ActionAuthMeta() {
    }

    public ActionAuthMeta freeze(){
        frozen = true;
        return this;
    }

    private void checkFrozen(){
        if(frozen)
            throw new IllegalStateException("action auth meta is frozen");
    }

    public boolean isPublicAccess() {
        return publicAccess;
    }

    public void setPublicAccess(boolean publicAccess) {
        checkFrozen();
        this.publicAccess = publicAccess;
    }

    public void setRoles(Set<String> roles) {
        checkFrozen();
        this.roles = roles;
    }

    public void setPermissions(MultiCsvSet permissions) {
        checkFrozen();
        this.permissions = permissions;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public MultiCsvSet getPermissions() {
        return permissions;
    }
}