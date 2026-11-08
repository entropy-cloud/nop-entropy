package io.nop.batch.core;

import io.nop.commons.concurrent.thread.NamedThreadLocal;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BatchTaskGlobals {
    static final ThreadLocal<IBatchTaskContext> s_taskContext = new NamedThreadLocal<>("batch-task-context");

    public static IBatchTaskContext useTaskContext() {
        return s_taskContext.get();
    }

    public static void provideTaskContext(IBatchTaskContext taskContext) {
        s_taskContext.set(taskContext);
    }

    public static void removeTaskContext() {
        s_taskContext.remove();
    }

    /**
     * 1.7-C1-001（plan 2545 W-01）：保存当前线程的批任务上下文，供嵌套批任务执行后恢复。
     * <p>
     * 原 provide/remove 严格成对但对「嵌套执行」不设防：子批任务的 provide 覆盖 TL，其 finally
     * 的 remove 清空槽位——外层任务此后失去上下文（xpl 侧 onChunkBegin 等助手 NPE / 静默失聪）。
     * 提供一个显式 save/restore 对，供调用方在嵌套边界使用：
     * <pre>
     * Object prev = BatchTaskGlobals.saveTaskContext();
     * try { ...内层执行... } finally { BatchTaskGlobals.restoreTaskContext(prev); }
     * </pre>
     */
    public static Object saveTaskContext() {
        return s_taskContext.get();
    }

    public static void restoreTaskContext(Object prev) {
        if (prev == null)
            s_taskContext.remove();
        else
            s_taskContext.set((IBatchTaskContext) prev);
    }

    public static void onTaskBegin(Consumer<IBatchTaskContext> action) {
        IBatchTaskContext ctx = useTaskContext();
        ctx.onTaskBegin(() -> action.accept(ctx));
    }

    public static void onTaskEnd(BiConsumer<IBatchTaskContext, Throwable> action) {
        IBatchTaskContext ctx = useTaskContext();
        ctx.onAfterComplete(error -> action.accept(ctx, error));
    }

    public static void onBeforeTaskEnd(Consumer<IBatchTaskContext> action) {
        IBatchTaskContext ctx = useTaskContext();
        ctx.onBeforeComplete(() -> action.accept(ctx));
    }

    public static void onChunkBegin(Consumer<IBatchChunkContext> action) {
        useTaskContext().onChunkBegin(action);
    }

    public static void onBeforeChunkEnd(Consumer<IBatchChunkContext> action) {
        useTaskContext().onBeforeChunkEnd(action);
    }

    public static void onChunkEnd(BiConsumer<IBatchChunkContext, Throwable> action) {
        useTaskContext().onChunkEnd(action);
    }
}
