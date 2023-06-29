/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.api;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface AuthApiErrors {
    String ARG_BIZ_OBJ_NAME = "bizObjName";
    String ARG_ACTION_NAME = "actionName";
    String ARG_OBJ_TYPE_NAME = "objTypeName";

    String ARG_PERMISSION = "permission";

    String ARG_USER_NAME = "userName";
    String ARG_USER_ID = "userId";

    String ARG_LOGIN_TYPE = "loginType";
    String ARG_PRINCIPAL_ID = "principalId";

    String ARG_ID = "id";

    String ARG_ROLES = "roles";

    String ARG_FIELD_NAME = "fieldName";

    String ARG_FIELD_DISPLAY_NAME = "{fieldDisplayName}";
    ErrorCode ERR_AUTH_NO_PERMISSION =
            define("nop.err.auth.no-permission",
                    "没有访问权限", ARG_PERMISSION);

    ErrorCode ERR_AUTH_NO_PERMISSION_FOR_FIELD =
            define("nop.err.auth.no-permission",
                    "没有对字段[{fieldName}]的访问权限", ARG_PERMISSION, ARG_FIELD_NAME, ARG_OBJ_TYPE_NAME);
    ErrorCode ERR_AUTH_NO_ROLE =
            define("nop.err.auth.no-role",
                    "没有访问权限", ARG_ROLES);

    ErrorCode ERR_AUTH_NO_DATA_AUTH =
            define("nop.err.auth.no-data-auth",
                    "没有访问类型为[{bizObjName}]的指定实体的权限");
}