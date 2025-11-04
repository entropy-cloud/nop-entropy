/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.message;

public enum SeekMode {
    DEFAULT,

    /**
     * 具有同样consumerId的消费者，第一次消费消息时从头开始消费，以后再次连接到broker上时从上次消费后的位置开始消费
     */
    INIT_SEEK_TO_BEGIN,

    INIT_SEEK_TO_END,

    /**
     * 每次重新连接上消息队列，都从最新的消息开始消费，将跳过所有此前未接收的消息
     */
    ALWAYS_SEEK_TO_END,
}