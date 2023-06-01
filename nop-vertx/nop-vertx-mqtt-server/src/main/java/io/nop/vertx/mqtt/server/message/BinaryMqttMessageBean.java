package io.nop.vertx.mqtt.server.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class BinaryMqttMessageBean extends MqttMessageBean {
    private ByteBuf binaryPayload;

    private MqttProperties mqttProperties;

    @JsonIgnore
    public ByteBuf getBinaryPayload() {
        return binaryPayload;
    }

    public void setBinaryPayload(ByteBuf binaryPayload) {
        this.binaryPayload = binaryPayload;
    }

    @JsonIgnore
    public MqttProperties getMqttProperties() {
        return mqttProperties;
    }

    public void setMqttProperties(MqttProperties mqttProperties) {
        this.mqttProperties = mqttProperties;
    }
}