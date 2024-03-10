/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.persister;

import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import io.nop.core.model.graph.TopoEntry;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.session.IOrmSessionImplementor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.CompletionStage;

/**
 * @author canonical_entropy@163.com
 */
public class BatchActionQueueImpl implements IBatchActionQueue {
    static final Logger LOG = LoggerFactory.getLogger(BatchActionQueueImpl.class);

    private final String querySpace;

    private final NavigableMap<TopoEntry<? extends IEntityModel>, BatchActionHolder> actionMap = new TreeMap<>();
    private final IPersistEnv env;

    private List<IBatchAction.CollectionBatchAction> collectionActions;

    private Map<String, Runnable> delayTasks;
    private boolean flushing;

    public BatchActionQueueImpl(String querySpace, IPersistEnv env) {
        this.querySpace = querySpace;
        this.env = env;
    }

    static class BatchActionHolder {
        List<IBatchAction.EntitySaveAction> saveActions;
        // 按照id进行排序，避免更新时发生锁冲突
        Map<String, IBatchAction.EntityUpdateAction> updateActions;
        List<IBatchAction.EntityDeleteAction> deleteActions;

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

        public void addDeleteAction(IBatchAction.EntityDeleteAction action) {
            if (deleteActions == null)
                deleteActions = new ArrayList<>();
            deleteActions.add(action);
        }
    }

    @Override
    public String getQuerySpace() {
        return querySpace;
    }

    BatchActionHolder makeActionHolder(IBatchAction.IEntityBatchAction action) {
        TopoEntry<? extends IEntityModel> topoEntry = env.getEntityModelTopoEntry(action.getEntityName());
        Guard.notNull(topoEntry, "topoEntry");
        BatchActionHolder holder = actionMap.get(topoEntry);
        if (holder == null) {
            holder = new BatchActionHolder();
            actionMap.put(topoEntry, holder);
        }
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
    public CompletionStage<Void> flushAsync(IOrmSessionImplementor session) {
        try {
            this.flushing = true;
            List<CompletionStage<?>> futures = new ArrayList<>();

            List<IBatchAction.CollectionBatchAction> collActions = this.collectionActions;

            boolean bError = false;
            for (Map.Entry<TopoEntry<? extends IEntityModel>, BatchActionHolder> entry : actionMap.entrySet()) {
                IEntityModel entityModel = entry.getKey().getValue();
                BatchActionHolder holder = entry.getValue();
                IEntityPersister persister = env.requireEntityPersister(entityModel.getName());
                CompletionStage<Void> future = persister.batchExecuteAsync(true, querySpace, holder.saveActions,
                        holder.getUpdateActions(), holder.deleteActions, session);

                FutureHelper.collectWaiting(future, futures);
                // 已经出现异常，没有必要再继续执行
                if(FutureHelper.isError(future)) {
                    bError = true;
                    break;
                }
            }

            if(!bError) {
                for (Map.Entry<TopoEntry<? extends IEntityModel>, BatchActionHolder> entry : actionMap.descendingMap()
                        .entrySet()) {
                    IEntityModel entityModel = entry.getKey().getValue();
                    BatchActionHolder holder = entry.getValue();
                    IEntityPersister persister = env.requireEntityPersister(entityModel.getName());

                    CompletionStage<Void> future = persister.batchExecuteAsync(false, querySpace, holder.saveActions,
                            holder.getUpdateActions(), holder.deleteActions, session);
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