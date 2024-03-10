/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.util;

import java.util.function.Consumer;

public interface ICancelToken {

    boolean isCancelled();

    String getCancelReason();

    /**
     * 增加取消操作时的回调函数。
     *
     * @param task 回调函数，参数为cancelReason
     */
    void appendOnCancel(Consumer<String> task);

    default void appendOnCancelTask(Runnable task) {
        if (task == null)
            return;

        appendOnCancel(r -> task.run());
    }

    void removeOnCancel(Consumer<String> task);
}
