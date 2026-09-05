/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.vertx.mqtt.server.impl;

import io.nop.vertx.mqtt.server.IMqttConnection;
import io.nop.vertx.mqtt.server.router.MqttTopicMatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 订阅感知的会话表：clientId → 连接 + 该连接的 topic 过滤器集合。
 * 是服务端内路由（应用向设备下发）的数据基础。
 */
public class MqttSessionManager {
    private final Map<String, IMqttConnection> sessions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> subscriptions = new ConcurrentHashMap<>();

    /**
     * 注册连接。按 MQTT-3.1.1 会话接管语义，同一 clientId 的新连接会取代并关闭旧连接。
     *
     * @return 被取代的旧连接
     */
    public IMqttConnection addConnection(IMqttConnection conn) {
        IMqttConnection old = sessions.put(conn.getClientId(), conn);
        if (old != null && old != conn) {
            subscriptions.remove(old.getClientId());
            old.close();
        }
        return old;
    }

    public void removeConnection(IMqttConnection conn) {
        sessions.remove(conn.getClientId(), conn);
        subscriptions.remove(conn.getClientId());
    }

    public IMqttConnection getConnection(String clientId) {
        return sessions.get(clientId);
    }

    public void subscribe(String clientId, List<String> filters) {
        if (filters == null || filters.isEmpty())
            return;
        Set<String> set = subscriptions.computeIfAbsent(clientId, k -> ConcurrentHashMap.newKeySet());
        set.addAll(filters);
    }

    public void unsubscribe(String clientId, List<String> filters) {
        Set<String> set = subscriptions.get(clientId);
        if (set != null && filters != null) {
            set.removeAll(filters);
        }
    }

    /**
     * 找出订阅了指定 topic 的所有活跃连接（任一过滤器命中即命中）
     */
    public List<IMqttConnection> findConnections(String topic) {
        if (topic == null)
            return Collections.emptyList();
        List<IMqttConnection> result = new ArrayList<>();
        for (Map.Entry<String, IMqttConnection> entry : sessions.entrySet()) {
            Set<String> filters = subscriptions.get(entry.getKey());
            if (filters == null)
                continue;
            for (String filter : filters) {
                if (MqttTopicMatcher.matches(filter, topic)) {
                    result.add(entry.getValue());
                    break;
                }
            }
        }
        return result;
    }
}
