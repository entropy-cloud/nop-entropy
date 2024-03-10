/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.tcc.api;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.FutureHelper;

import java.util.concurrent.CompletionStage;

public interface ITccTransaction {
    default String getTxnGroup() {
        return getTccRecord().getTxnGroup();
    }

    default String getTxnId() {
        return getTccRecord().getTxnId();
    }

    ITccRecord getTccRecord();

    default TccStatus getTccStatus() {
        return getTccRecord().getTccStatus();
    }

    /**
     * 当前服务是否是事务的发起者
     */
    boolean isInitiator();

    CompletionStage<Void> beginAsync();

    CompletionStage<Void> endAsync(boolean timeout, ApiResponse<?> response, Throwable ex);

    default void begin() {
        FutureHelper.syncGet(beginAsync());
    }

    default void end(boolean timeout, ApiResponse<?> response, Throwable ex) {
        FutureHelper.syncGet(endAsync(timeout, response, ex));
    }
}
