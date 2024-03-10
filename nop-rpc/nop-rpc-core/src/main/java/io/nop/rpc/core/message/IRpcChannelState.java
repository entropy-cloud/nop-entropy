/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.core.message;

import java.util.concurrent.CompletableFuture;

/**
 * 用于RPC客户端的帮助类，它负责记录正在等待响应消息的RPC请求状态。参见MessageRpcClient的实现
 */
public interface IRpcChannelState<S, R> {
    /**
     * 记录正在等待响应消息的RPC调用。RPC调用成功后会通过Promise返回响应消息，调用失败或者超时则会接收到对应异常对象。
     *
     * @param id      请求消息的id。响应消息需要携带同样的id来实现消息配对
     * @param req     请求消息
     * @param timeout 等待响应消息的超时时间。超时之后会自动调用取消函数
     * @return 对应于响应消息的Promise对象。如果超时，会接收到TimeoutException异常
     */
    CompletableFuture<R> prepareSend(Object id, S req, long timeout);

    /**
     * 接收到响应消息后调用此方法
     *
     * @param id   请求消息id
     * @param resp 响应消息
     * @return 是否找到与之匹配的响应消息
     */
    boolean onReceive(Object id, R resp);

    void onFailure(Object id, Throwable e);

    void onChannelOpen();

    /**
     * 整个RPC通道关闭，自动释放取消所有正在等待的调用
     *
     * @param e 如果信道异常关闭，这里传递对应的异常消息
     */
    void onChannelClose(Throwable e);
}