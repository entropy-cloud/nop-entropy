/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.loader;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchLoaderProvider.IBatchLoader;
import io.nop.batch.core.exceptions.BatchCancelException;
import io.nop.commons.concurrent.thread.ThreadHelper;
import io.nop.commons.util.retry.IRetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 消费失败之后可以重试retryCount次。如果设置了retryOneByOne，则重试的时候放弃批处理，进行逐条重试。
 */
public class RetryBatchLoader<S> implements IBatchLoader<S> {
    static final Logger LOG = LoggerFactory.getLogger(RetryBatchLoader.class);

    private final IBatchLoader<S> loader;
    private final IRetryPolicy<IBatchChunkContext> retryPolicy;

    public RetryBatchLoader(IBatchLoader<S> loader, IRetryPolicy<IBatchChunkContext> retryPolicy) {
        this.loader = Guard.notNull(loader, "loader");
        this.retryPolicy = retryPolicy;
    }

    @Override
    public List<S> load(int batchSize, IBatchChunkContext context) {
        try {
            return loader.load(batchSize, context);
        } catch (Exception e) {
            return retryLoad(batchSize, e, context);
        }
    }

    List<S> retryLoad(int batchSize, Exception exception, IBatchChunkContext context) {
        do {
            context.incLoadRetryCount();

            long delay = retryPolicy.getRetryDelay(exception, context.getLoadRetryCount(), context);
            if (delay < 0) {
                throw NopException.adapt(exception);
            }

            if (delay > 0) {
                ThreadHelper.sleep(delay);
            }

            try {
                return loader.load(batchSize, context);
            } catch (BatchCancelException e) {
                throw e;
            } catch (Exception e) {
                exception = e;
                LOG.error("nop.err.batch.retry-load-fail:loadRetryCount={}", context.getLoadRetryCount(), e);
            }
        } while (true);
    }
}