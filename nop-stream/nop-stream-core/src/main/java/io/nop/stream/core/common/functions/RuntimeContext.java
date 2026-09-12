/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.functions;

import io.nop.stream.core.exceptions.StreamException;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_OPERATION;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_UNSUPPORTED;
import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.common.state.KeyedStateStore;
import io.nop.stream.core.time.TimerService;

/**
 * @Internal
 */
@Internal
public interface RuntimeContext {

    int getIndexOfThisSubtask();

    int getNumberOfParallelSubtasks();

    String getTaskName();

    default KeyedStateStore getKeyedStateStore() {
        throw new StreamException(ERR_STREAM_UNSUPPORTED).param(ARG_OPERATION, "Keyed state is only available on a keyed stream.");
    }

    default TimerService getTimerService() {
        throw new StreamException(ERR_STREAM_UNSUPPORTED).param(ARG_OPERATION, "Timers are only available on a keyed stream.");
    }
}
