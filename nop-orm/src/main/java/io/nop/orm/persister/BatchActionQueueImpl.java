/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.persister;

import io.nop.api.core.util.FutureHelper;
import io.nop.commons.util.CollectionHelper;
import io.nop.orm.ILoadedOrmModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.session.IOrmSessionImplementor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletionStage;

/**
 * @author canonical_entropy@163.com
 */
public class BatchActionQueueImpl implements IBatchActionQueue {
    static final Logger LOG = LoggerFactory.getLogger(BatchActionQueueImpl.class);

    private final String querySpace;

    private final Map<String, BatchActionHolder> actionMap = new HashMap<>();
    private final IOrmSessionImplementor session;

    private List<IBatchAction.CollectionBatchAction> collectionActions;

    private Map<String, Runnable> delayTasks;
    private boolean flushing;

    public BatchActionQueueImpl(String querySpace, IOrmSessionImplementor session) {
        this.querySpace = querySpace;
        this.session = session;
    }

    static class BatchActionHolder {
        List<IBatchAction.EntitySaveAction> saveActions;
        // 按照id进行排序，避免更新时发生锁冲突
        Map<String, IBatchAction.EntityUpdateAction> updateActions;
        Map<String, IBatchAction.EntityDeleteAction> deleteActions;

        public void addSaveAction(IBatchAction.EntitySaveAction action) {
            if (saveActions == null)
                saveActions = new ArrayList<>();
            saveActions.add(action);
        }

        public void addUpdateAction(IBatchAction.EntityUpdateAction action) {
            if (updateActions == null)
                updateActions = new TreeMap<>();
            updateActions.put(String.valueOf(action.getEntityId()), action);
        }

        List<IBatchAction.EntityUpdateAction> getUpdateActions() {
            if (updateActions == null)
                return null;

            if (updateActions.size() == 1) {
                return Collections.singletonList(updateActions.values().iterator().next());
            }

            return new ArrayList<>(updateActions.values());
        }

        List<IBatchAction.EntityDeleteAction> getDeleteActions() {
            if (deleteActions == null)
                return null;
            if (deleteActions.size() == 1)
                return Collections.singletonList(CollectionHelper.first(deleteActions.values()));
            return new ArrayList<>(deleteActions.values());
        }

        public void addDeleteAction(IBatchAction.EntityDeleteAction action) {
            if (deleteActions == null)
                deleteActions = new TreeMap<>();
            deleteActions.put(String.valueOf(action.getEntityId()), action);
        }
    }

    @Override
    public String getQuerySpace() {
        return querySpace;
    }

    BatchActionHolder makeActionHolder(IBatchAction.IEntityBatchAction action) {
        String entityName = action.getEntityName();
        BatchActionHolder holder = actionMap.computeIfAbsent(entityName, k -> new BatchActionHolder());
        return holder;
    }

    @Override
    public void enqueueSave(IBatchAction.EntitySaveAction action) {
        makeActionHolder(action).addSaveAction(action);
    }

    @Override
    public void enqueueUpdate(IBatchAction.EntityUpdateAction action) {
        makeActionHolder(action).addUpdateAction(action);
    }

    @Override
    public void enqueueDelete(IBatchAction.EntityDeleteAction action) {
        makeActionHolder(action).addDeleteAction(action);
    }

    @Override
    public void enqueueCollection(IBatchAction.CollectionBatchAction action) {
        if (collectionActions == null)
            collectionActions = new ArrayList<>();
        collectionActions.add(action);
    }

    @Override
    public void addDelayTask(String key, Runnable task) {
        if (delayTasks == null)
            delayTasks = new LinkedHashMap<>();
        delayTasks.put(key, task);
    }

    @Override
    public Runnable getDelayTask(String key) {
        return delayTasks == null ? null : delayTasks.get(key);
    }

    public boolean isFlushing() {
        return flushing;
    }

    @Override
    public CompletionStage<Void> flushAsync() {
        ILoadedOrmModel model = session.getLoadedOrmModel();
        try {
            this.flushing = true;
            List<CompletionStage<?>> futures = new ArrayList<>();

            List<IBatchAction.CollectionBatchAction> collActions = this.collectionActions;

            List<IEntityModel> sortedList = model.getEntityModelsInTopoOrder(actionMap.keySet());

            boolean bError = false;
            for (IEntityModel entityModel : sortedList) {
                BatchActionHolder holder = actionMap.get(entityModel.getName());
                IEntityPersister persister = model.requireEntityPersister(entityModel.getName());
                CompletionStage<Void> future = persister.batchExecuteAsync(true, querySpace, holder.saveActions,
                        holder.getUpdateActions(), holder.getDeleteActions(), session);

                FutureHelper.collectWaiting(future, futures);
                // 已经出现异常，没有必要再继续执行
                if (FutureHelper.isError(future)) {
                    bError = true;
                    break;
                }
            }

            if (!bError) {
                for (int i = sortedList.size() - 1; i >= 0; i--) {
                    IEntityModel entityModel = sortedList.get(i);
                    BatchActionHolder holder = actionMap.get(entityModel.getName());
                    IEntityPersister persister = model.requireEntityPersister(entityModel.getName());

                    CompletionStage<Void> future = persister.batchExecuteAsync(false, querySpace, holder.saveActions,
                            holder.getUpdateActions(), holder.getDeleteActions(), session);
                    FutureHelper.collectWaiting(future, futures);

                    // 已经出现异常，没有必要再继续执行
                    if (FutureHelper.isError(future))
                        break;
                }
            }

            flushDelayTasks();

            CompletionStage<Void> retFuture = FutureHelper.waitAll(futures);

            return retFuture.thenRun(() -> {
                if (collActions != null) {
                    for (IBatchAction.ICollectionAction action : collActions) {
                        action.onSuccess(null);
                    }
                }
            });
        } finally {
            // 无论是否异步返回，这里都可以清空map了
            actionMap.clear();
            delayTasks = null;
            this.collectionActions = null;
            this.flushing = false;
        }
    }

    private void flushDelayTasks() {
        if (delayTasks != null) {
            for (Runnable task : delayTasks.values()) {
                try {
                    task.run();
                } catch (Exception e) {
                    LOG.error("nop.err.orm.flush-delay-task-fail", e);
                }
            }
            delayTasks = null;
        }
    }
}