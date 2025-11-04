/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.message;

import io.nop.api.core.annotations.data.DataBean;

/**
 * 将topic+message包装为一个简单对象，便于存放在列表中
 */
@DataBean
public class TopicMessage {
    private final String topic;
    private final Object message;

    public TopicMessage(String topic, Object message) {
        this.topic = topic;
        this.message = message;
    }

    public String getTopic() {
        return topic;
    }

    public Object getMessage() {
        return message;
    }
}
