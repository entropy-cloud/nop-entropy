/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rpc.core.monitor;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.task.TaskStatusBean;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ICancelToken;
import io.nop.api.core.util.ICancellable;
import io.nop.api.core.util.ProcessResult;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.commons.service.LifeCycleSupport;
import io.nop.commons.util.StringHelper;
import io.nop.rpc.api.IRpcService;
import io.nop.rpc.core.utils.RpcHelper;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 定时抓取正在执行的长时任务的状态，并保存到持久化存储中
 */
public class RpcTaskMonitor extends LifeCycleSupport {
    private Map<String, RpcTask> activeTasks = new ConcurrentHashMap<>();

    private IRpcTaskStatusStore statusStorage;
    private ICancellable cancellable;

    private int monitorInterval = 10000;

    private IScheduledExecutor timer;

    private Future<?> timerFuture;

    public void setStatusStorage(IRpcTaskStatusStore statusStorage) {
        this.statusStorage = statusStorage;
    }

    public void setTimer(IScheduledExecutor timer) {
        this.timer = timer;
    }

    public IRpcTaskStatusStore getStatusStorage() {
        return statusStorage;
    }

    public int getMonitorInterval() {
        return monitorInterval;
    }

    public void setMonitorInterval(int monitorInterval) {
        this.monitorInterval = monitorInterval;
    }

    @Override
    protected void doStart() {
        statusStorage.fetchActiveTasks(request -> {
            addTask0(request);
        });
        timerFuture = timer.scheduleWithFixedDelay(this::checkTaskStatus, monitorInterval, monitorInterval,
                TimeUnit.MILLISECONDS);
    }

    @Override
    protected void doStop() {
        ICancellable cancellable = this.cancellable;
        if (cancellable != null)
            cancellable.cancel();

        if (timerFuture != null) {
            timerFuture.cancel(false);
            timerFuture = null;
        }
    }

    private void checkTaskStatus() {
        Cancellable cancellable = new Cancellable();
        this.cancellable = cancellable;

        for (RpcTask task : activeTasks.values()) {
            String statusMethod = task.getStatusMethod();
            if (!StringHelper.isEmpty(statusMethod)) {
                IRpcService service = task.getRpcService();
                service.callAsync(statusMethod, task.getRequest(), cancellable).whenComplete((ret, err) -> {
                    ApiResponse<TaskStatusBean> res = RpcHelper.toTaskStatusResponse(ret);
                    handleTaskStatus(task, res, err);
                });
            }
        }
    }

    void handleTaskStatus(RpcTask task, ApiResponse<TaskStatusBean> res, Throwable err) {
        if (res != null && res.isOk()) {
            task.setCheckFailCount(0);
        } else {
            task.setCheckFailCount(task.getCheckFailCount() + 1);
        }
        ProcessResult result = statusStorage.saveTaskStatus(task, res, err);
        if (result == ProcessResult.STOP) {
            removeTask(task.getTaskId());
        }
    }

    private void addTask0(RpcTask task) {
        this.activeTasks.put(task.getTaskId(), task);
    }

    public void updateTaskStatus(TaskStatusBean status) {
        RpcTask task = activeTasks.get(status.getTaskId());
        if (task != null) {
            handleTaskStatus(task, ApiResponse.buildSuccess(status), null);
        }
    }

    public void addTask(RpcTask task) {
        Guard.notEmpty(task.getTaskId(), "task.taskId");
        Guard.notNull(task.getRequest(), "task.request");
        Guard.notNull(task.getRpcService(), "task.rpcService");
        Guard.notEmpty(task.getCancelMethod(), "task.cancelMethod");

        statusStorage.saveTask(task);
        addTask0(task);
    }

    public void removeTask(String taskId) {
        this.activeTasks.remove(taskId);
    }

    public CompletionStage<Void> cancelTask(String taskId, ICancelToken cancelToken) {
        RpcTask task = activeTasks.get(taskId);
        if (task != null) {
            IRpcService service = task.getRpcService();
            return service.callAsync(task.getCancelMethod(), task.getRequest(), cancelToken).thenRun(() -> {
            });
        }
        return FutureHelper.success(null);
    }
}