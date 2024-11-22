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
