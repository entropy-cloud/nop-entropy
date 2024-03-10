/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service;

import io.nop.auth.api.messages.LoginRequest;
import io.nop.core.lang.json.JsonTool;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestLoginRequest {
    @Test
    public void testDeserialize() {
        String str = "{\"internal\":true, \"loginType\":1}";
        LoginRequest request = (LoginRequest) JsonTool.parseBeanFromText(str, LoginRequest.class);
        assertEquals(1, request.getLoginType());
        assertEquals(false, request.isInternal());
    }
}
