/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.health;

/**
 * Item 16 (P-REQ-7): registrable health-state listener. Callbacks fire on
 * every legal transition of {@link JobHealthStateMachine}; listener failures
 * are caught and logged by the machine and never break the control path.
 */
public interface JobHealthListener {

    /**
     * @param jobId  the job identity
     * @param from   the previous health state
     * @param to     the new health state (always different from {@code from})
     * @param cause  human-readable transition cause (never null)
     */
    void onHealthTransition(String jobId, StreamJobHealth from, StreamJobHealth to, String cause);
}
