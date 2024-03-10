/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.session;

import io.nop.api.core.util.FutureHelper;
import io.nop.dao.DaoConstants;
import io.nop.orm.persister.BatchActionQueueImpl;
import io.nop.orm.persister.IBatchActionQueue;
import io.nop.orm.persister.IPersistEnv;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletionStage;

public class SessionBatchActionQueue {
    /**
     * 每一个querySpace都有自己的执行队列
     */
    Map<String, IBatchActionQueue> queues;

    public IBatchActionQueue getBatchActionQueue(String querySpace, IPersistEnv env) {
        // querSpace为map的key时，不允许为null
        if (querySpace == null)
            querySpace = DaoConstants.DEFAULT_QUERY_SPACE;

        // 按照querySpace的名称顺序flush，确保运行时按照固定顺序执行，减少随机性
        if (queues == null) {
            queues = new TreeMap<>();
        }

        IBatchActionQueue queue = queues.get(querySpace);
        if (queue == null) {
            queue = new BatchActionQueueImpl(querySpace, env);
        }
        queues.put(querySpace, queue);
        return queue;
    }

    public void flush(IOrmSessionImplementor session) {
        FutureHelper.syncGet(flushAsync(session));
    }

    public CompletionStage<Void> flushAsync(IOrmSessionImplementor session) {
        if (queues != null) {
            List<CompletionStage<?>> futures = new ArrayList<>();
            for (IBatchActionQueue queue : queues.values()) {
                CompletionStage<?> future = queue.flushAsync(session);
                FutureHelper.collectWaiting(future, futures);
                if(FutureHelper.isError(future)){
                    break;
                }
            }
            return FutureHelper.waitAll(futures);
        } else {
            return FutureHelper.voidPromise();
        }
    }
}