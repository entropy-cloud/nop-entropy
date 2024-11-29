/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.batch.core.IBatchTaskRecord;
import io.nop.batch.dao.NopBatchDaoConstants;
import io.nop.batch.dao.entity._gen._NopBatchTask;


@BizObjName("NopBatchTask")
public class NopBatchTask extends _NopBatchTask implements IBatchTaskRecord {
    public NopBatchTask() {
    }

    public boolean isHistory() {
        return getTaskStatus() >= NopBatchDaoConstants.TASK_STATUS_COMPLETED;
    }

    public boolean isSuspended() {
        return getTaskStatus() == NopBatchDaoConstants.TASK_STATUS_SUSPENDED;
    }

    public void incExecCount() {
        Integer count = getExecCount();
        if (count == null)
            count = 0;
        setExecCount(count + 1);
    }
}
