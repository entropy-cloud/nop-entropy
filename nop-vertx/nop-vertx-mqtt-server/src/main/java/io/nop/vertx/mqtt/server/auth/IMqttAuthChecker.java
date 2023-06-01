package io.nop.vertx.mqtt.server.auth;

import io.nop.vertx.mqtt.server.IMqttConnection;

import java.util.concurrent.CompletionStage;

public interface IMqttAuthChecker {
    CompletionStage<Boolean> checkAuthAsync(String userName, String password, IMqttConnection conn);
}