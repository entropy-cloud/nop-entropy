package io.nop.task.step;

import io.nop.api.core.context.IContext;
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

        // W-03（CRA 8.49 R4-09，1.5-C3-004 裁定 C）：缺省上下文重投递——body 在池线程直跑时
        // ThreadLocal 依赖（ContextProvider.currentLocale 等）静默失效或串号；以 runtime 构造时
        // 已捕获的 context 重投递（RunOnContextTaskStepWrapper 同构先例），无 opt-out 键。
        // 2541 W-06 Throwable 收宽产物零弱化：包裹位于 try 块内首行，catch(Throwable) 边界随之
        // 外扩；Error rethrow/onCancel/bindCancelToken 三点语义零触碰。
        IContext context = stepRt.getContext();

        CompletableFuture<TaskStepReturn> ret = new CompletableFuture<>();
        CompletableFuture<?> future = executor.submit(() -> {
            // LOG.info("in thread");
            try {
                // body 与内层终结守卫（设计评审 R1-F-1）：BaseContext.execute 为双派发腿
                // （inline / 排队 enqueue）——生产请求下池线程调用走排队腿，body 异常发生在
                // context 线程、不经本池线程 catch(Throwable)；lambda 内自带 catch 保证两腿
                // 派发下 ret 均必然终结（无新悬挂路径）
                Runnable body = () -> {
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
                    } catch (Throwable t) {
                        ret.completeExceptionally(t);
                        if (t instanceof Error)
                            throw (Error) t;
                    }
                };
                if (context != null) {
                    context.execute(body);
                } else {
                    // 无 context 捕获（编程式构造极端路径）：保持直跑语义，不静默丢执行
                    body.run();
                }
            } catch (Throwable t) {
                // W-06（CRA 8.48 R4-08，MA-13 裁定 B）：catch 收宽为 Throwable——executor 派发
                // 回调抛出的 Error 也保证 ret promise 必然终结（修复前 Error 穿透 → ret 无终结
                // 路径 → 任务永驻非终态、清理/终态 driver 全跳过零信号）。Error 原样 rethrow
                // （池 worker 线程语义：Error 属 JVM 级信号，complete 仅保证 promise 终结，
                // 不豁免线程侧传播；不 adapt 包装防 OOM/SOE 期再分配）。
                // plan 349 取消兜底（下方 onCancel/bindCancelToken）零弱化——本腿补 Error 残余，
                // 两腿叠加语义独立
                ret.completeExceptionally(t);
                if (t instanceof Error)
                    throw (Error) t;
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