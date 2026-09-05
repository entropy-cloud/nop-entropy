/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.ops;

import io.nop.stream.runtime.coordinator.JobCoordinator;

/**
 * Item 16 (P-REQ-5/6): registry of the coordinators hosted in this process.
 * The ops HTTP server resolves job-scoped endpoints against it. The default
 * single-job launch registers its one coordinator; the multi-job ops manager
 * (submit path) maintains the full registry.
 */
public interface IOpsJobRegistry {

    /** Ids of all coordinators hosted in this process. */
    java.util.Set<String> jobIds();

    /** The coordinator for a jobId, or null when unknown (callers answer 404). */
    JobCoordinator coordinator(String jobId);
}
