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
