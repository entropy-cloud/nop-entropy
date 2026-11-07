package io.nop.task.step;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.concurrent.semaphore.ISemaphore;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import io.nop.task.utils.TaskStepHelper;
import jakarta.annotation.Nonnull;

import static io.nop.core.CoreErrors.ARG_KEY;
import static io.nop.task.TaskErrors.ERR_TASK_THROTTLE_TIMEOUT;

public class ThrottleTaskStepWrapper extends DelegateTaskStep {

    private final boolean global;
    private final int maxConcurrency;
    private final int maxWait;

    private final IEvalAction keyExpr;

    public ThrottleTaskStepWrapper(ITaskStep taskStep, boolean global,
                                   int maxConcurrency, int maxWait, IEvalAction keyExpr) {
        super(taskStep);
        this.global = global;
        this.maxConcurrency = maxConcurrency;
        this.maxWait = maxWait;
        this.keyExpr = keyExpr;
    }

    @Nonnull
    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        String key = getKey(stepRt);
        ISemaphore semaphore = stepRt.getTaskRuntime().getSemaphore(key, maxConcurrency, global);
        if (!semaphore.tryAcquire(1, maxWait)) {
            throw TaskStepHelper.newError(getLocation(), stepRt, ERR_TASK_THROTTLE_TIMEOUT)
                    .param(ARG_KEY, key);
        }
        // W-04（CRA 8.48 R4-08，MA-11 裁定 A）：tryAcquire 成功之后登记执行链持有键（检测时点
        // 契约①——先获取后检测，不误报合法并发兄弟）。重入同 key 即确定性硬失败；重入命中时
        // 本层尚未登记 held 键（acquire 在注册前抛出），下方 catch 以 keyRegistered 门控跳过
        // releaseThrottleKey——外层同 key 的注册保持不动，本层 permit 由 release 成对归还
        boolean keyRegistered = false;
        try {
            TaskStepHelper.acquireThrottleKey(getLocation(), stepRt, key, global);
            keyRegistered = true;

            TaskStepHelper.checkNotCancelled(stepRt);

            return getTaskStep().execute(stepRt).whenComplete((ret, err) -> {
                // 移除点与 release 同点成对（检测时点契约②——held 集成员资格严格镜像 permit
                // 持有权，禁拆分）：先移除 held 键再归还 permit
                TaskStepHelper.releaseThrottleKey(stepRt, key, global);
                semaphore.release(1);
            });
        } catch (Throwable t) {
            // W-06（CRA 8.48 R4-08，MA-13 裁定 B）：catch 收宽为 Throwable——同步抛出的 Error
            // 同样走 releaseThrottleKey+release 成对归还（修复前 Error 泄漏 permit → 该 key
            // 永久全拒仅重启恢复）；Error 原样 rethrow（不 adapt 包装，防 OOM/SOE 期再分配）
            if (keyRegistered)
                TaskStepHelper.releaseThrottleKey(stepRt, key, global);
            semaphore.release(1);
            if (t instanceof Error)
                throw (Error) t;
            throw NopException.adapt(t);
        }
    }

    private String getKey(ITaskStepRuntime stepRt) {
        String key;
        if (keyExpr == null) {
            key = stepRt.getStepPath();
        } else {
            key = ConvertHelper.toString(keyExpr.invoke(stepRt));
            if (StringHelper.isEmpty(key))
                key = stepRt.getStepPath();
        }
        return key;
    }
}