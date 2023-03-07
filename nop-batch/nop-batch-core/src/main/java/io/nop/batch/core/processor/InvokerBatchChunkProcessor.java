/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.batch.core.processor;

import io.nop.api.core.util.ProcessResult;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchChunkProcessor;
import io.nop.commons.functional.IFunctionInvoker;

public class InvokerBatchChunkProcessor implements IBatchChunkProcessor {
    private final IFunctionInvoker invoker;
    private final IBatchChunkProcessor processor;

    public InvokerBatchChunkProcessor(IFunctionInvoker invoker, IBatchChunkProcessor processor) {
        this.invoker = invoker;
        this.processor = processor;
    }

    @Override
    public ProcessResult process(IBatchChunkContext context) {
        return invoker.invoke(processor::process, context);
    }
}