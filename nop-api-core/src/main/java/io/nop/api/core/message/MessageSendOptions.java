/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.message;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.util.ICancelToken;

@DataBean
public class MessageSendOptions {

    /**
     * 消息延迟时间，发送后多久接收端才能够接收到消息
     */
    private long delay;

    /**
     * 发送超时时间，多长时间内未发送出去认为消息已超时。单位为毫秒
     */
    private long sendTimeout;

    private ICancelToken cancelToken;

    public ICancelToken getCancelToken() {
        return cancelToken;
    }

    public void setCancelToken(ICancelToken cancelToken) {
        this.cancelToken = cancelToken;
    }

    public long getDelay() {
        return delay;
    }

    public void setDelay(long delay) {
        this.delay = delay;
    }

    public long getSendTimeout() {
        return sendTimeout;
    }

    public void setSendTimeout(long sendTimeout) {
        this.sendTimeout = sendTimeout;
    }
}