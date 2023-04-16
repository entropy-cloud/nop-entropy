/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.core;

public interface AuthCoreConstants {
    String PLACEHOLDER_BACK_URL = "{backUrl}";
    String PLACEHOLDER_HOST = "{host}";
    String PLACEHOLDER_PORT = "{port}";

    String PARAM_CODE = "code";
    String PARAM_STATE = "state";

    String COOKIE_NOP_TOKEN = "nop-token";

    String VAR_ACTION = "action";
    String VAR_ENTITY = "entity";
    String VAR_USER_CONTEXT = "userContext";
    String VAR_FILTER = "filter";
}
