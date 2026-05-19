/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.api;

/**
 * Constants for nop-job API module.
 * Defines header names used in RPC communication between coordinator and worker.
 */
public interface NopJobApiConstants {

    /**
     * Header carrying the job fire ID — identifies a specific scheduled fire event.
     * Injected automatically by RpcJobInvoker for all RPC calls.
     */
    String HEADER_JOB_FIRE_ID = "nop-job-fire-id";

    /**
     * Header carrying the job task ID — identifies a specific task within a fire.
     * Injected automatically by RpcJobInvoker for all RPC calls.
     */
    String HEADER_JOB_TASK_ID = "nop-job-task-id";

    /**
     * Header carrying the job name.
     * Injected automatically by RpcJobInvoker for all RPC calls.
     */
    String HEADER_JOB_NAME = "nop-job-name";

    /**
     * Header carrying the job group.
     * Injected automatically by RpcJobInvoker for all RPC calls.
     */
    String HEADER_JOB_GROUP = "nop-job-group";

    /**
     * Header carrying the sharding index for this task execution (0-based).
     * Injected automatically by RpcJobInvoker when sharding info is available.
     */
    String HEADER_JOB_SHARDING_INDEX = "nop-job-sharding-index";

    /**
     * Header carrying the total number of shards for this fire.
     * Injected automatically by RpcJobInvoker when sharding info is available.
     */
    String HEADER_JOB_SHARDING_TOTAL = "nop-job-sharding-total";
}
