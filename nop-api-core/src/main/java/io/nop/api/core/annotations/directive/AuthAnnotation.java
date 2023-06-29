/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.annotations.directive;

import java.lang.annotation.Annotation;

public class AuthAnnotation implements Auth {
    private String roles = "";
    private String permissions = "";
    private boolean noAuth;

    public String roles() {
        return roles;
    }

    @Override
    public boolean noAuth() {
        return noAuth;
    }

    public void setNoAuth(boolean noAuth) {
        this.noAuth = noAuth;
    }

    public void setRoles(String roles) {
        this.roles = roles;
    }

    public String permissions() {
        return permissions;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return Auth.class;
    }
}
