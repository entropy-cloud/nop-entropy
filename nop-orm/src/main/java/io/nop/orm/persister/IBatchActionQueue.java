/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.persister;

import io.nop.api.core.annotations.core.Internal;
import io.nop.api.core.util.FutureHelper;
import io.nop.orm.session.IOrmSessionImplementor;

import java.util.concurrent.CompletionStage;

public interface IBatchActionQueue {
    /**
     * 只针对一个querySpace
     *
     * @return
     */
    String getQuerySpace();

    void enqueueSave(IBatchAction.EntitySaveAction action);

    void enqueueUpdate(IBatchAction.EntityUpdateAction action);

    void enqueueDelete(IBatchAction.EntityDeleteAction action);

    // void enqueueSaveManyToMany(IBatchAction.ISaveManyToManyAction action);
    //
    // void enqueueDeleteManyToMany(IBatchAction.IDeleteManyToManyAction action);

    void enqueueCollection(IBatchAction.CollectionBatchAction action);

    void addDelayTask(String key, Runnable task);

    Runnable getDelayTask(String key);

    boolean isFlushing();

    default void flush() {
        FutureHelper.syncGet(flushAsync());
    }

    /**
     * 异步刷新。这是一个内部接口，异步执行过程中可能在别的线程上更新实体状态，所以需要谨慎使用。
     */
    @Internal
    CompletionStage<Void> flushAsync();
}