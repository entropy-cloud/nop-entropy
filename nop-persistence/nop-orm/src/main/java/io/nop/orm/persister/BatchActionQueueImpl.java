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
import io.nop.orm.OrmErrors;
import io.nop.orm.exceptions.OrmException;
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
import java.util.concurrent.atomic.AtomicReference;

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
            Map<String, Runnable> delayTasks = this.delayTasks;

            List<IEntityModel> sortedList = model.getEntityModelsInTopoOrder(actionMap.keySet());

            // 记录中断时未提交的实体模型范围，这些模型的动作不会收到任何回调
            AtomicReference<Throwable> errorRef = new AtomicReference<>();
            boolean bError = false;
            int phase1FailIndex = sortedList.size();
            for (int i = 0; i < sortedList.size(); i++) {
                IEntityModel entityModel = sortedList.get(i);
                BatchActionHolder holder = actionMap.get(entityModel.getName());
                IEntityPersister persister = model.requireEntityPersister(entityModel.getName());
                CompletionStage<Void> future = persister.batchExecuteAsync(true, querySpace, holder.saveActions,
                        holder.getUpdateActions(), holder.getDeleteActions(), session);

                FutureHelper.collectWaiting(future, futures);
                future.whenComplete((v, err) -> {
                    if (err != null)
                        errorRef.compareAndSet(null, err);
                });
                // 已经出现异常，没有必要再继续执行
                if (FutureHelper.isError(future) || errorRef.get() != null) {
                    bError = true;
                    phase1FailIndex = i;
                    break;
                }
            }

            int phase2FailIndex = -1;
            if (!bError) {
                for (int i = sortedList.size() - 1; i >= 0; i--) {
                    IEntityModel entityModel = sortedList.get(i);
                    BatchActionHolder holder = actionMap.get(entityModel.getName());
                    IEntityPersister persister = model.requireEntityPersister(entityModel.getName());

                    CompletionStage<Void> future = persister.batchExecuteAsync(false, querySpace, holder.saveActions,
                            holder.getUpdateActions(), holder.getDeleteActions(), session);
                    FutureHelper.collectWaiting(future, futures);
                    future.whenComplete((v, err) -> {
                        if (err != null)
                            errorRef.compareAndSet(null, err);
                    });

                    // 已经出现异常，没有必要再继续执行
                    if (FutureHelper.isError(future) || errorRef.get() != null) {
                        bError = true;
                        phase2FailIndex = i;
                        break;
                    }
                }
            }

            if (bError) {
                // 中断后未提交的动作不会收到任何回调，这里统一补发失败通知，
                // 避免依赖onFailure做清理的扩展实现被静默跳过
                notifyUnsubmittedFailure(sortedList, phase1FailIndex, phase2FailIndex, errorRef.get());
            }

            CompletionStage<Void> retFuture = FutureHelper.waitAll(futures);
            Throwable submitError = errorRef.get();

            return retFuture.whenComplete((v, err) -> {
                // 部分future实现(如ResolvedPromise)经过waitAll的thenRun包装后可能不透传异常，
                // 因此这里同时检查waitAll的结果和提交阶段收集到的错误
                Throwable error = err != null ? err : submitError;
                if (error == null) {
                    // 延迟任务只在所有动作都执行成功后才执行
                    runDelayTasks(delayTasks);
                }
                if (collActions != null) {
                    for (IBatchAction.ICollectionAction action : collActions) {
                        if (error == null) {
                            action.onSuccess(null);
                        } else {
                            // 整体执行失败时补发失败通知，否则集合动作的回调既不走onSuccess也不走onFailure
                            action.onFailure(error);
                        }
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

    /**
     * 正序阶段在phase1FailIndex处中断时，其后(下标更大)的实体模型从未被提交；
     * 逆序阶段在phase2FailIndex处中断时，其前(下标更小)且被其他实体依赖的实体模型的delete动作只在逆序阶段执行，也不会被提交
     */
    private void notifyUnsubmittedFailure(List<IEntityModel> sortedList, int phase1FailIndex, int phase2FailIndex,
                                          Throwable err) {
        Throwable error = err != null ? err : new OrmException(OrmErrors.ERR_ORM_BATCH_FLUSH_ABORTED);
        for (int i = phase1FailIndex + 1; i < sortedList.size(); i++) {
            notifyHolderFailure(actionMap.get(sortedList.get(i).getName()), error, true);
        }
        for (int i = 0; i < phase2FailIndex; i++) {
            IEntityModel entityModel = sortedList.get(i);
            if (entityModel.isDependByOtherEntity()) {
                notifyHolderFailure(actionMap.get(entityModel.getName()), error, false);
            }
        }
    }

    private void notifyHolderFailure(BatchActionHolder holder, Throwable error, boolean includeSaveUpdate) {
        if (holder == null)
            return;
        if (includeSaveUpdate && holder.saveActions != null) {
            for (IBatchAction.EntitySaveAction action : holder.saveActions) {
                action.onFailure(error);
            }
        }
        if (includeSaveUpdate && holder.updateActions != null) {
            for (IBatchAction.EntityUpdateAction action : holder.updateActions.values()) {
                action.onFailure(error);
            }
        }
        if (holder.deleteActions != null) {
            for (IBatchAction.EntityDeleteAction action : holder.deleteActions.values()) {
                action.onFailure(error);
            }
        }
    }

    private void runDelayTasks(Map<String, Runnable> delayTasks) {
        if (delayTasks != null) {
            for (Runnable task : delayTasks.values()) {
                try {
                    task.run();
                } catch (Exception e) {
                    LOG.error("nop.err.orm.flush-delay-task-fail", e);
                }
            }
        }
    }
}