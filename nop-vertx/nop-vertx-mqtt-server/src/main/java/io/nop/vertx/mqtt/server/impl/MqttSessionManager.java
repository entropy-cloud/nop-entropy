/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.vertx.mqtt.server.impl;

import io.nop.vertx.mqtt.server.IMqttConnection;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MqttSessionManager {
    private Map<String, IMqttConnection> sessions = new ConcurrentHashMap<>();

    public void addConnection(IMqttConnection conn) {
        sessions.put(conn.getClientId(), conn);
    }

    public void removeConnection(IMqttConnection conn) {
        sessions.remove(conn.getClientId(), conn);
    }
}
