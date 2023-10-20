/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
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
        if (!Objects.equals(password, users.get(userName)))
            return FutureHelper.success(false);
        return FutureHelper.success(true);
    }
}
