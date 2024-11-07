package io.nop.batch.core;

import io.nop.commons.concurrent.thread.NamedThreadLocal;

import java.util.function.BiConsumer;

public class BatchTaskGlobals {
    static final ThreadLocal<IBatchTaskContext> s_taskContext = new NamedThreadLocal<>("batch-task-context");
    static final ThreadLocal<IBatchChunkContext> s_chunkContext = new NamedThreadLocal<>("batch-chunk-context");

    public static IBatchTaskContext useTaskContext() {
        return s_taskContext.get();
    }

    public static void provideTaskContext(IBatchTaskContext taskContext) {
        s_taskContext.set(taskContext);
    }

    public static void removeTaskContext() {
        s_taskContext.remove();
    }

    public static void onTaskComplete(BiConsumer<IBatchTaskContext, Throwable> action) {
        IBatchTaskContext ctx = useTaskContext();
        ctx.onAfterComplete(error -> action.accept(ctx, error));
    }

    public static void addBeforeTaskComplete(Runnable action) {
        useTaskContext().onBeforeComplete(action);
    }
}
