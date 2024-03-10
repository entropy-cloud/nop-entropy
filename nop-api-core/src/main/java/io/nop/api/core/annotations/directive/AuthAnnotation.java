/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.annotations.directive;

import java.lang.annotation.Annotation;

public class AuthAnnotation implements Auth {
    private String roles = "";
    private String permissions = "";
    private boolean publicAccess;

    public String roles() {
        return roles;
    }

    @Override
    public boolean publicAccess() {
        return publicAccess;
    }

    public void setPublicAccess(boolean publicAccess) {
        this.publicAccess = publicAccess;
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
