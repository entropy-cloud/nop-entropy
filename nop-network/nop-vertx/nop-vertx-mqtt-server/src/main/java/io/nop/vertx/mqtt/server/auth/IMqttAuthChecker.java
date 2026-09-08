/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.vertx.mqtt.server.auth;

import io.nop.vertx.mqtt.server.IMqttConnection;

import java.util.concurrent.CompletionStage;

public interface IMqttAuthChecker {
    CompletionStage<Boolean> checkAuthAsync(String userName, String password, IMqttConnection conn);

    /**
     * 携带 clientId 的认证入口。MQTT CONNECT 的主身份是 clientId，认证实现如需按 clientId
     * 解析主体（例如 IoT 设备以 clientId=deviceKey 接入），应覆写本方法。
     * 缺省实现忽略 clientId，委托三参方法，保持既有实现向后兼容。
     */
    default CompletionStage<Boolean> checkAuthAsync(String clientId, String userName, String password,
                                                    IMqttConnection conn) {
        return checkAuthAsync(userName, password, conn);
    }
}