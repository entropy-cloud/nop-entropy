/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.biz.batch.task;

import io.nop.batch.core.BatchTaskBuilder;
import io.nop.batch.core.IBatchTask;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;

public class BizImportTaskBuilder {
    private IThreadPoolExecutor executor;

    private BizImportConfig config;


    public IBatchTask build() {
        BatchTaskBuilder builder = new BatchTaskBuilder();

        return null;
    }
}
