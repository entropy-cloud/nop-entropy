package io.nop.task.step;

import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import jakarta.annotation.Nonnull;

import java.util.concurrent.CompletableFuture;

/**
 * 在异步线程上执行
 */
public class ExecutorTaskStepWrapper extends DelegateTaskStep {
    //  static final Logger LOG = LoggerFactory.getLogger(ExecutorTaskStepWrapper.class);
    private final String executorBean;

    public ExecutorTaskStepWrapper(ITaskStep taskStep, String executorBean) {
        super(taskStep);
        this.executorBean = executorBean;
    }

    @Nonnull
    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        IThreadPoolExecutor executor = stepRt.getTaskRuntime().getThreadPoolExecutor(executorBean);

        CompletableFuture<TaskStepReturn> ret = new CompletableFuture<>();
        CompletableFuture<?> future = executor.submit(() -> {
            // LOG.info("in thread");
            try {
                TaskStepReturn result = getTaskStep().execute(stepRt);
                if (result.isDone()) {
                    ret.complete(result.sync());
                } else {
                    result.whenComplete((data, err) -> {
                        if (err != null) {
                            ret.complete(data);
                        } else {
                            ret.completeExceptionally(err);
                        }
                    });
                }
            } catch (Exception e) {
                ret.completeExceptionally(e);
            }
            return null;
        });

        ICancelToken cancelToken = stepRt.getCancelToken();
        FutureHelper.bindCancelToken(cancelToken, future);
        return TaskStepReturn.ASYNC(null, ret);
    }
}