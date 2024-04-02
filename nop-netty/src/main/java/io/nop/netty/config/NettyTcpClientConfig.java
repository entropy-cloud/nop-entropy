/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.netty.config;

public class NettyTcpClientConfig extends NettyBaseConfig {
    private boolean autoReconnect = true;

    private int connectTimeout = 2000;

    private int connectRetryDelay = 100;

    private int maxConnectRetryCount = -1;

    private int maxConnectRetryDelay = 3000;

    private int responseTimeout = 10000;

    private boolean useResponseHandler;

    private String remoteHost;
    private int remotePort;

    public boolean isAutoReconnect() {
        return autoReconnect;
    }

    public void setAutoReconnect(boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getConnectRetryDelay() {
        return connectRetryDelay;
    }

    public void setConnectRetryDelay(int connectRetryDelay) {
        this.connectRetryDelay = connectRetryDelay;
    }

    public int getMaxConnectRetryCount() {
        return maxConnectRetryCount;
    }

    public void setMaxConnectRetryCount(int maxConnectRetryCount) {
        this.maxConnectRetryCount = maxConnectRetryCount;
    }

    public int getMaxConnectRetryDelay() {
        return maxConnectRetryDelay;
    }

    public void setMaxConnectRetryDelay(int maxConnectRetryDelay) {
        this.maxConnectRetryDelay = maxConnectRetryDelay;
    }

    public int getResponseTimeout() {
        return responseTimeout;
    }

    public void setResponseTimeout(int responseTimeout) {
        this.responseTimeout = responseTimeout;
    }

    public boolean isUseResponseHandler() {
        return useResponseHandler;
    }

    public void setUseResponseHandler(boolean useResponseHandler) {
        this.useResponseHandler = useResponseHandler;
    }

    public String getRemoteHost() {
        return remoteHost;
    }

    public void setRemoteHost(String remoteHost) {
        this.remoteHost = remoteHost;
    }

    public int getRemotePort() {
        return remotePort;
    }

    public void setRemotePort(int remotePort) {
        this.remotePort = remotePort;
    }
}
