/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.debugger.IDebugger;
import io.nop.api.debugger.IDebuggerAsync;
import io.nop.commons.lang.IDestroyable;
import io.nop.commons.util.retry.RetryPolicy;
import io.nop.rpc.api.IRpcProxy;
import io.nop.rpc.simple.SimpleRpcClientFactory;
import io.nop.socket.ClientConfig;

import java.util.function.Consumer;

/**
 * 通过socket连接到远程XLang服务器。
 *
 * <p>CRA 8.40 W-02（C6-001）：连接器持有 {@link #connect()} 返回的通道代理，{@link #destroy()}
 * 按「先通道后池」配对关闭——先 {@link IRpcProxy#proxy_destroy()}（RpcInvocationHandler →
 * SimpleRpcClient.close → SocketClient.close：置 closed + 关 socket + 取消心跳；recv 线程因
 * read 抛错退出、autoReconnect 因 closed 短路停止），后 {@link SimpleRpcClientFactory#destroy()}
 * （executor/timer shutdown）；try/finally 保证池销毁必然执行。{@link #connect()} 与
 * {@link #destroy()} 以连接器监视器锁串行化（IDE 插件侧 EDT 的 stop 与后台 Task 的 connect
 * 并发窗），幂等语义 = 重复 destroy 关闭当前持有代理（不采 destroyed 早退标志——该形态会让
 * 「destroy 先于代理存入」竞态下后到的代理永不关闭）。</p>
 *
 * <p>本类原位于 nop-idea-plugin（CRA 8.40 CL-301 通道② 可测逻辑下沉）：零 IntelliJ 平台 API
 * 依赖，插件经既有依赖坐标 {@code nop-xlang-debugger} 可见，侧 diff 仅 1 条 import。</p>
 */
public class XLangDebugConnector implements IDestroyable {
    private final SimpleRpcClientFactory<IDebuggerAsync> clientFactory = new SimpleRpcClientFactory<>();
    private IDebuggerAsync proxy;

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

    /**
     * 建立调试通道并持有返回的代理（单会话单代理）。同一连接器已持有活代理时再次 connect 为
     * 编程错误（fail-fast 显式化，与 SimpleRpcClientFactory destroy 后 newInstance fail-fast
     * 同纪律）；destroy 之后 connect 经工厂既有 fail-fast 抛出。
     */
    public synchronized IDebuggerAsync connect() {
        if (proxy != null) {
            throw new IllegalStateException("XLangDebugConnector already connected: connect must not be called twice");
        }
        this.proxy = clientFactory.newInstance();
        return proxy;
    }

    /**
     * 配对关闭（W-02）：先通道后池，幂等。未成功 connect 过时仅销毁工厂自有线程资源（首次
     * newInstance 已建出的 timer/executor 不遗留）。
     */
    @Override
    public synchronized void destroy() {
        try {
            IDebuggerAsync held = this.proxy;
            if (held != null) {
                this.proxy = null;
                if (held instanceof IRpcProxy) {
                    // 关闭路径尽力而为：proxy_destroy 内部经 DestroyHelper 吞并记录，不阻断池销毁
                    ((IRpcProxy) held).proxy_destroy();
                }
            }
        } finally {
            clientFactory.destroy();
        }
    }
}
