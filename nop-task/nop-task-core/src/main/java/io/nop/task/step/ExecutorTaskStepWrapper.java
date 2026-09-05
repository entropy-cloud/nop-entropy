package io.nop.task.step;

import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import io.nop.task.exceptions.NopTaskCancelledException;
import jakarta.annotation.Nonnull;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

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
                            ret.completeExceptionally(err);
                        } else {
                            ret.complete(data);
                        }
                    });
                }
            } catch (Exception e) {
                ret.completeExceptionally(e);
            }
            return null;
        });

        ICancelToken cancelToken = stepRt.getCancelToken();
        // 取消兜底（plan 349 Phase 4）：body 不完成（排队期被取消/运行中忽略取消）时 ret 无 complete 路径，
        // 上层续延会永久挂死。取消发生时以携带 reason 的取消异常强制终结返回 promise
        // （取消/超时终结不变量）。修复前仅在 future 上绑定取消（bindCancelToken），
        // future 已正常运行完成时 ret 仍永不完成。
        if (cancelToken != null) {
            Consumer<String> onCancel = reason -> {
                if (!ret.isDone())
                    ret.completeExceptionally(NopTaskCancelledException.forReason(reason));
            };
            cancelToken.appendOnCancel(onCancel);
            ret.whenComplete((v, e) -> cancelToken.removeOnCancel(onCancel));
        }
        FutureHelper.bindCancelToken(cancelToken, future);
        return TaskStepReturn.ASYNC(null, ret);
    }
}