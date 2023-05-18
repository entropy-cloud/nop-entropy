/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rpc.core.message;

/**
 * 记录RPC调用的状态转换过程
 */
public interface IRpcHook {
    /**
     * 在实际发送请求消息之前调用
     *
     * @param id      请求消息的id
     * @param req     请求消息
     * @param timeout 记录RPC请求的超时时间
     */
    void onSend(Object id, Object req, long timeout);

    /**
     * 在实际接收到匹配的响应消息之后调用
     *
     * @param id   请求消息的id
     * @param req  请求消息
     * @param resp 响应消息
     */
    void onReceiveMatched(Object id, Object req, Object resp);

    /**
     * 当接收到响应消息，但是没有找到对应的请求消息时调用
     *
     * @param id   请求消息的id
     * @param resp 响应消息
     */
    void onReceiveUnmatched(Object id, Object resp);

    /**
     * 当等待响应消息超时的时候调用
     *
     * @param id      请求消息的id
     * @param req     请求消息
     * @param timeout 等待响应消息的超时时间
     */
    void onTimeout(Object id, Object req, long timeout);

    /**
     * 等待响应消息的期间发生异常的时候调用
     *
     * @param id        请求消息的id
     * @param req       请求消息
     * @param exception 异常对象
     */
    void onError(Object id, Object req, Throwable exception);
}