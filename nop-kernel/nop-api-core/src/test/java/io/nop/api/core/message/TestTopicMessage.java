/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.message;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * WI24 (MSG-F-59): {@code TopicMessage} gains field-value equals/hashCode and the
 * {@code @DataBean} no-arg constructor.
 */
class TestTopicMessage {

    @Test
    void equalsAndHashCodeUseFieldValues() {
        TopicMessage a = new TopicMessage("t", "m");
        TopicMessage b = new TopicMessage("t", "m");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        assertNotEquals(a, new TopicMessage("other", "m"));
        assertNotEquals(a, new TopicMessage("t", "other"));
        assertNotEquals(a, "not-a-topic-message");
    }

    @Test
    void noArgConstructorSupportsDataBeanReflection() throws Exception {
        TopicMessage message = TopicMessage.class.getDeclaredConstructor().newInstance();
        message.setTopic("t");
        message.setMessage("m");
        assertEquals("t", message.getTopic());
        assertEquals("m", message.getMessage());
    }
}
