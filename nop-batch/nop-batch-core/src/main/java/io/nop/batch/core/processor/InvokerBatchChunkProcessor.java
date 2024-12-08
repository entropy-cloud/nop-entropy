/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.processor;

import io.nop.api.core.util.ProcessResult;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchChunkProcessorProvider;
import io.nop.commons.functional.IFunctionInvoker;

public class InvokerBatchChunkProcessor<S> implements IBatchChunkProcessorProvider.IBatchChunkProcessor<S> {
    private final IFunctionInvoker invoker;
    private final IBatchChunkProcessorProvider.IBatchChunkProcessor<S> processor;

    public InvokerBatchChunkProcessor(IFunctionInvoker invoker, IBatchChunkProcessorProvider.IBatchChunkProcessor<S> processor) {
        this.invoker = invoker;
        this.processor = processor;
    }

    @Override
    public ProcessResult process(IBatchChunkContext context) {
        return invoker.invoke(processor::process, context);
    }
}