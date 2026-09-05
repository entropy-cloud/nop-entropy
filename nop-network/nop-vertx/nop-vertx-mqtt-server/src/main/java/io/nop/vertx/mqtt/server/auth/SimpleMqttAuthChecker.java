/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.vertx.mqtt.server.auth;

import io.nop.api.core.util.FutureHelper;
import io.nop.vertx.mqtt.server.IMqttConnection;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

public class SimpleMqttAuthChecker implements IMqttAuthChecker {
    private Map<String, String> users = Collections.emptyMap();

    public void setUsers(Map<String, String> users) {
        this.users = users;
    }

    @Override
    public CompletionStage<Boolean> checkAuthAsync(String userName, String password,
                                                   IMqttConnection conn) {
        // 匿名（null/null）凭据不得通过空用户表的 Objects.equals(null,null) 匹配；
        // 未知用户的口令同样为 null，也必须拒绝
        if (userName == null || userName.isEmpty())
            return FutureHelper.success(false);
        String expected = users.get(userName);
        if (expected == null)
            return FutureHelper.success(false);
        if (!Objects.equals(password, expected))
            return FutureHelper.success(false);
        return FutureHelper.success(true);
    }
}
