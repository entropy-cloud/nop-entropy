/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.tdengine.message;

import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.MessageCapability;
import io.nop.api.core.message.MessageOptionsValidator;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscribeOptions;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletionStage;

public class TdMessageService implements IMessageService {
    /**
     * Empty capability set (D1): every non-default option field is typed-rejected, the
     * operations themselves are explicit failures. Placeholder backend — see roadmap WI2
     * capability matrix.
     */
    @Override
    public Set<MessageCapability> getCapabilities() {
        return Collections.emptySet();
    }

    @Override
    public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
        MessageOptionsValidator.validateSend(getCapabilities(), options, "tdengine");
        throw new UnsupportedOperationException("TdMessageService.sendAsync is not implemented");
    }

    @Override
    public IMessageSubscription subscribe(String topic, IMessageConsumer listener, MessageSubscribeOptions options) {
        MessageOptionsValidator.validateSubscribe(getCapabilities(), options, "tdengine");
        throw new UnsupportedOperationException("TdMessageService.subscribe is not implemented");
    }
}
