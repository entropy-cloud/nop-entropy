/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.message;

import java.util.EnumSet;
import java.util.Set;

/**
 * 消息服务提供单向信息发送与接收
 */
public interface IMessageService extends IMessageSender, IMessageSubscriber {

    /**
     * Declares the capabilities this backend honors (adjudication D1). The default is the
     * minimal set; every production backend overrides it per the capability table in
     * {@code ai-dev/design/nop-message/message-contract-design.md}. Narrowing-only
     * addition — implementations that never call {@link MessageOptionsValidator} are
     * unaffected.
     */
    default Set<MessageCapability> getCapabilities() {
        return EnumSet.of(MessageCapability.SEND, MessageCapability.SUBSCRIBE,
                MessageCapability.ACK_REPLY);
    }
}