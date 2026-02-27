/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.retry.api;

/**
 * Retry module API constants (default values).
 */
public interface NopRetryApiConstants {

    // ==================== 默认值常量 ====================

    int DEFAULT_MAX_RETRY_COUNT = 3;

    long DEFAULT_INITIAL_INTERVAL_MS = 5000L;
    long DEFAULT_MAX_INTERVAL_MS = 60000L;
    double DEFAULT_JITTER_RATIO = 0.5;

    int DEFAULT_EXECUTION_TIMEOUT_SECONDS = 60;
    long DEFAULT_DEADLINE_TIMEOUT_MS = 24 * 60 * 60 * 1000L;

    int DEFAULT_IMMEDIATE_RETRY_COUNT = 0;
    long DEFAULT_IMMEDIATE_RETRY_INTERVAL_MS = 1000L;

    long DEFAULT_RETRYING_TIMEOUT_MS = 10 * 60 * 1000L;

    int DEFAULT_PARTITION_COUNT = 16;

    String BOOL_YES = "1";
    String BOOL_NO = "0";
}
