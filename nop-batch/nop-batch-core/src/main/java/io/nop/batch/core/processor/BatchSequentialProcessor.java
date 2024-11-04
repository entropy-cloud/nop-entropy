/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.processor;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchProcessorProvider.IBatchProcessor;
import io.nop.batch.core.IBatchRequestGenerator;

import java.util.function.Consumer;

/**
 * 根据前一个请求的执行结果，不断产生新的请求
 *
 * @param <S>
 * @param <R>
 */
public class BatchSequentialProcessor<S, R> implements IBatchProcessor<Object, R> {
    private final IBatchProcessor<S, R> processor;

    public BatchSequentialProcessor(IBatchProcessor<S, R> processor) {
        this.processor = processor;
    }

    @Override
    public void process(Object item, Consumer<R> consumer, IBatchChunkContext context) {
        if (item instanceof IBatchRequestGenerator) {
            IBatchRequestGenerator<S, R> seq = (IBatchRequestGenerator) item;
            do {
                // 生成一个新的请求对象
                S request = seq.nextRequest(context);
                if (request == null)
                    break;

                // 处理单条请求，产生结果数据
                processor.process(request, response -> {
                    // 向生成器反馈结果数据
                    seq.onResponse(response, context);

                    // 向外输出结果数据
                    consumer.accept(response);
                }, context);

            } while (true);
        } else {
            processor.process((S) item, consumer, context);
        }
    }
}