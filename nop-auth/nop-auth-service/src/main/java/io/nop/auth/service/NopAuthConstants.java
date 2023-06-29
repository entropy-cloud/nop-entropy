/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.service;

import io.nop.auth.api.AuthApiConstants;

public interface NopAuthConstants {

    String SEQUENCE_LOGIN_SESSION = "login-session";

    String SITE_ID_MAIN = "main";

    int USER_STATUS_ACTIVE = AuthApiConstants.USER_STATUS_ACTIVE;

    String RESOURCE_TYPE_TOP_MENU = AuthApiConstants.RESOURCE_TYPE_TOP_MENU;
    String RESOURCE_TYPE_SUB_MENU = AuthApiConstants.RESOURCE_TYPE_SUB_MENU;
    String RESOURCE_TYPE_FUNCTION_POINT = AuthApiConstants.RESOURCE_TYPE_FUNCTION_POINT;

    String PATH_MAIN_ACTION_AUTH = "/nop/main/auth/main.action-auth.xml";

    String PATH_MAIN_DATA_AUTH = "/nop/main/auth/main.data-auth.xml";
}