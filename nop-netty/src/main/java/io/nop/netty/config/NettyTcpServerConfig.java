/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.netty.config;

public class NettyTcpServerConfig extends NettyBaseConfig {
    private int bossGroupSize;

    private String host;
    private int port;

    public int getBossGroupSize() {
        return bossGroupSize;
    }

    public void setBossGroupSize(int bossGroupSize) {
        this.bossGroupSize = bossGroupSize;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
