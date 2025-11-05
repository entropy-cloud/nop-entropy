/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.socket;

import io.nop.api.core.annotations.config.ConfigBean;
import io.nop.commons.util.NetHelper;

@ConfigBean
public class ServerConfig extends AbstractSocketConfig {
    private String serverName;
    private String host = NetHelper.LOCALHOST4().getHostAddress();
    private int port = 10203;

    private int minDataLen = 0;
    private int maxDataLen = 1024 * 1024;

    private int maxConnections = 10;

    private int idleTimeout = 10000;

    private int threadPoolSize = 10;

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }

    public int getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(int idleTimeout) {
        this.idleTimeout = idleTimeout;
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

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }
}
