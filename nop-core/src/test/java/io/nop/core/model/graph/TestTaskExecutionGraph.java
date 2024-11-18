package io.nop.core.model.graph;

import io.nop.api.core.util.FutureHelper;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.thread.ThreadHelper;
import io.nop.commons.util.MathHelper;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestTaskExecutionGraph {
    @Test
    public void testRun() {
        AtomicInteger count = new AtomicInteger();
        TaskExecutionGraph graph = new TaskExecutionGraph("test");
        Runnable task = () -> {
            ThreadHelper.sleep(MathHelper.random().nextInt(100));
            count.incrementAndGet();
        };
        graph.addTask("a", task);
        graph.addTask("b", task);
        graph.addTask("c", task);
        graph.addDepend("b", "a");
        graph.addDepend("c", "b");
        graph.addDepend("a", "c");
        graph.analyze();

        CompletableFuture<?> future = graph.runOnExecutor(GlobalExecutors.cachedThreadPool(), null);
        FutureHelper.syncGet(future);
        assertEquals(3, count.get());
    }
}
