/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.coordinator;

import io.nop.api.core.annotations.core.Internal;

/**
 * G56: job-level terminal status tracked by {@link JobCoordinator}.
 *
 * <p>Transitions:
 * <ul>
 *   <li>{@code CREATED → RUNNING} on {@code JobCoordinator.start()}</li>
 *   <li>{@code RUNNING → FAILED} on {@code failJob(Throwable)} (e.g. when the
 *       global restart cap is exceeded)</li>
 *   <li>{@code RUNNING → CANCELED} on terminate(CANCEL) once we surface that
 *       transition explicitly (currently {@code stop()} path)</li>
 * </ul>
 *
 * <p>Once {@code FAILED} or {@code CANCELED}, the coordinator stops accepting
 * new assignments / triggers.
 */
@Internal
public enum JobStatus {
    CREATED,
    RUNNING,
    FAILED,
    CANCELED;

    public boolean isTerminal() {
        return this == FAILED || this == CANCELED;
    }
}
