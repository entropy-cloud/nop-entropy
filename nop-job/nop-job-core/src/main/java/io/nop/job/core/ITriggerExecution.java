/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.core;

import io.nop.api.core.util.ICancellable;
import io.nop.job.api.ITriggerState;

import java.util.concurrent.CompletionStage;

public interface ITriggerExecution extends ICancellable {

    CompletionStage<Void> getFinishPromise();

    ITriggerState getTriggerState();

    boolean isRunning();

    boolean isDone();

    default void pause() {
        cancel(NopJobCoreConstants.CANCEL_REASON_PAUSE);
    }

    default void deactivate() {
        cancel(NopJobCoreConstants.CANCEL_REASON_DEACTIVATE);
    }
}
