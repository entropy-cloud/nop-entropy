/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.api;

public interface AuthApiConstants {
    int USER_STATUS_DISABLED = 0;

    int USER_STATUS_ACTIVE = 1;

    int USER_STATUS_SUSPENDED = 2;

    int USER_STATUS_ABANDONED = 3;

    int RESOURCE_STATUS_ACTIVE = 1;

    int RESOURCE_STATUS_DISABLED = 0;

    int USER_GENDER_DEFAULT = 1;

    int USER_TYPE_DEFAULT = 1;

    String SITE_MAIN = "main";

    /**
     * 尚未退出
     */
    int LOGOUT_TYPE_NONE = 0;

    /**
     * 点击退出按钮退出
     */
    int LOGOUT_TYPE_MANUAL = 1;

    /**
     * 超时退出
     */
    int LOGOUT_TYPE_EXPIRE = 2;

    /**
     * 管理员强制退出
     */
    int LOGOUT_TYPE_KILL = 3;

    /**
     * 每个用户只有一个活动session，重新登录将导致原有的session被退出
     */
    int LOGOUT_TYPE_RELOGIN = 4;

    int LOGOUT_TYPE_SSO_CALLBACK = 5;


    int LOGIN_TYPE_USERNAME_PASSWORD = 1; // "username-password";

    int LOGIN_TYPE_EMAIL_PASSWORD = 2; // "email-password";

    int LOGIN_TYPE_PHONE_PASSWORD = 3; // "phone-password";

    int LOGIN_TYPE_SSO = 4;

    String RESOURCE_TYPE_TOP_MENU = "TOPM";
    String RESOURCE_TYPE_SUB_MENU = "SUBM";
    String RESOURCE_TYPE_FUNCTION_POINT = "FNPT";

    String JWT_CLAIMS_USERNAME = "preferred_username";
    String JWT_CLAIMS_SID = "sid";

    String JWT_CLAIMS_REALM_ACCESS = "realm_access";

    String JWT_CLAIMS_ROLES = "roles";
}
