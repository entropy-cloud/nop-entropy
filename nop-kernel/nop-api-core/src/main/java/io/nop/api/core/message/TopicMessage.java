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
 *
 * <p>WI24 (MSG-F-59): fields are no longer final so the {@code @DataBean} no-arg
 * constructor requirement can be met; {@link #equals(Object)}/{@link #hashCode()} use
 * field-value semantics — note the payload's own {@code equals} applies, and {@code
 * ApiMessage} currently has none (reference equality residue, declared in
 * message-contract-design.md 3.2).
 */
@DataBean
public class TopicMessage {
    private String topic;
    private Object message;

    public TopicMessage() {
    }

    public TopicMessage(String topic, Object message) {
        this.topic = topic;
        this.message = message;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public Object getMessage() {
        return message;
    }

    public void setMessage(Object message) {
        this.message = message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof TopicMessage))
            return false;
        TopicMessage other = (TopicMessage) o;
        return java.util.Objects.equals(topic, other.topic)
                && java.util.Objects.equals(message, other.message);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(topic, message);
    }
}
