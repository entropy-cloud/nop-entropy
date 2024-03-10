/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.tcc.api;

import java.sql.Timestamp;

public interface ITccRecord {
    String getTxnGroup();

    String getTxnId();

    // int getPartitionIndex();

    TccStatus getTccStatus();

    /**
     * 如果超过此时间尚未结束，则由监控引擎负责进行超时回滚
     */
    Timestamp getExpireTime();
}