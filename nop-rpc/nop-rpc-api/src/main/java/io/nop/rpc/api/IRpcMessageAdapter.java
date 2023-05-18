/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rpc.api;

/**
 * RPC相当于是在单向发送消息的基础上，通过消息配对形成请求响应消息。
 * 利用此适配器从普通的消息对象中获取RPC框架所需要的信息。
 */
public interface IRpcMessageAdapter<S, R> {
    String getRpcAction(S request);

    void setRpcAction(S request, String action);

    /**
     * RPC请求的超时时间
     *
     * @param request 请求对象
     * @return 返回小于等于0的值表示不会考虑超时
     */
    long getTimeout(S request);

    /**
     * 每个RPC请求消息需要有唯一id
     *
     * @param request 请求消息
     * @return 请求消息的唯一id
     */
    Object getMessageId(S request);

    /**
     * 是否单向发送消息
     *
     * @param request
     */
    boolean isOneWay(S request);

    /**
     * 从响应消息获取的原始请求消息id, 用于消息配对
     *
     * @param response
     * @return
     */
    Object getCorrelationId(R response);

    /**
     * 将异常信息转换为响应消息
     */
    default R getErrorResponse(Throwable e, S request) {
        return null;
    }
}