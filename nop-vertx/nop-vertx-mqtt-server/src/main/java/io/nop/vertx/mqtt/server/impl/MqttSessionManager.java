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
