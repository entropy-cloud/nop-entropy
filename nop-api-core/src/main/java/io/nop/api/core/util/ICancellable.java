/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.util;

public interface ICancellable extends ICancelToken {
    String CANCEL_REASON_KILL = "kill";
    String CANCEL_REASON_TIMEOUT = "timeout";
    String CANCEL_REASON_STOP = "stop";

    boolean isCancelled();

    String getCancelReason();

    void cancel(String reason);

    default void cancel() {
        cancel(CANCEL_REASON_KILL);
    }
}
