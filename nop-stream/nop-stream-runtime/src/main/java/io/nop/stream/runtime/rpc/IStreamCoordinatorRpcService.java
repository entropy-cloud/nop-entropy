/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.rpc;

import io.nop.api.core.annotations.core.Internal;

import io.nop.stream.runtime.taskmanager.CheckpointAckMessage;

@Internal
public interface IStreamCoordinatorRpcService {

    void receiveCheckpointAck(CheckpointAckMessage ack);
}
