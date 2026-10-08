/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.batch;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;

/**
 * WI2 (plan 2343 Phase 1): enumerator checkpoint state of the single-token batch-loader
 * source — whether the split has been handed out. A restored enumerator must NOT
 * re-assign an already-assigned token (the reader replays it from its own split state).
 */
@DataBean
public class BatchLoaderEnumeratorState implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean assigned;

    public BatchLoaderEnumeratorState() {
    }

    public BatchLoaderEnumeratorState(boolean assigned) {
        this.assigned = assigned;
    }

    public boolean isAssigned() {
        return assigned;
    }

    public void setAssigned(boolean assigned) {
        this.assigned = assigned;
    }
}
