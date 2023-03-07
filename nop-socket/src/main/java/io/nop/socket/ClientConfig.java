/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.socket;

import io.nop.api.core.annotations.config.ConfigBean;
import io.nop.commons.util.NetHelper;
import io.nop.commons.util.retry.RetryPolicy;

@ConfigBean
public class ClientConfig extends AbstractSocketConfig {
    private String clientName;
    private String host = NetHelper.LOCALHOST4().getHostAddress();
    private int port;

    private int minDataLen;
    private int maxDataLen = 1024 * 1024;
    private int readTimeout = 1000;
    private int connectTimeout = 5000;
    private int heartbeatInterval = 5000;

    private int responseTimeout = 1000;

    private boolean autoReconnect = true;
    private RetryPolicy reconnectPolicy;

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public int getResponseTimeout() {
        return responseTimeout;
    }

    public void setResponseTimeout(int responseTimeout) {
        this.responseTimeout = responseTimeout;
    }

    public boolean isAutoReconnect() {
        return autoReconnect;
    }

    public void setAutoReconnect(boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
    }

    public RetryPolicy getReconnectPolicy() {
        return reconnectPolicy;
    }

    public void setReconnectPolicy(RetryPolicy reconnectPolicy) {
        this.reconnectPolicy = reconnectPolicy;
    }

    public int getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(int heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getMinDataLen() {
        return minDataLen;
    }

    public void setMinDataLen(int minDataLen) {
        this.minDataLen = minDataLen;
    }

    public int getMaxDataLen() {
        return maxDataLen;
    }

    public void setMaxDataLen(int maxDataLen) {
        this.maxDataLen = maxDataLen;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }
}
