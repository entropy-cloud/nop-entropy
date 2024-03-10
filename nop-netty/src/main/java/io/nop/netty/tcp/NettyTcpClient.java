/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.netty.tcp;

import io.nop.commons.service.LifeCycleSupport;
import io.nop.netty.config.NettyTcpClientConfig;
import jakarta.inject.Inject;

public class NettyTcpClient extends LifeCycleSupport {
    private NettyTcpClientConfig clientConfig;

    @Inject
    public void setClientConfig(NettyTcpClientConfig config) {
        this.clientConfig = config;
    }

    @Override
    protected void doStart() {

    }

    @Override
    protected void doStop() {

    }
}