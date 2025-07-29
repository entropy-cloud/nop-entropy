/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.debugger;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.debugger.IDebugger;
import io.nop.api.debugger.IDebuggerAsync;
import io.nop.commons.lang.IDestroyable;
import io.nop.commons.util.retry.RetryPolicy;
import io.nop.rpc.simple.SimpleRpcClientFactory;
import io.nop.socket.ClientConfig;

import java.util.function.Consumer;

/**
 * 通过socket连接到远程XLang服务器
 */
public class XLangDebugConnector implements IDestroyable {
    private final SimpleRpcClientFactory<IDebuggerAsync> clientFactory = new SimpleRpcClientFactory<>();

    public XLangDebugConnector(int debugPort, Consumer<ApiResponse<?>> action, Runnable onChannelOpen) {
        ClientConfig config = new ClientConfig();
        config.setReadTimeout(0);
        config.setPort(debugPort);

        RetryPolicy<?> policy = new RetryPolicy<>();
        policy.setRetryDelay(200);
        policy.setExponentialDelay(true);
        policy.setMaxRetryDelay(1000);

        config.setReconnectPolicy(policy);

        clientFactory.setClientConfig(config);
        clientFactory.setServiceName(IDebugger.class.getName());
        clientFactory.setRpcInterface(IDebuggerAsync.class);
        clientFactory.setNoticeReceiver(action);
        clientFactory.setOnChannelOpen(onChannelOpen);
    }

    public int getDebugPort() {
        return clientFactory.getClientConfig().getPort();
    }

    public void destroy() {
        clientFactory.destroy();
    }

    public IDebuggerAsync connect() {
        return clientFactory.newInstance();
    }
}
