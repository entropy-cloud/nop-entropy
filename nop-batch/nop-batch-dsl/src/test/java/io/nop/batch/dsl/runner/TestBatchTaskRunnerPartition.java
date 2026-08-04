package io.nop.batch.dsl.runner;

import io.nop.api.core.beans.IntRangeSet;
import io.nop.batch.core.IBatchTask;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.batch.core.manager.IBatchTaskManager;
import io.nop.cluster.naming.PartitionResolver;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 验证 BatchTaskRunner 的 partition resolver 接线：注入 resolver 时 context.partitionRange 被 resolve
 * 并设置；未注入或 resolve 返回 null 时为 null。
 * <p>
 * StubManager 在 newBatchTaskContext 时捕获 context 引用，executeAsync 内 loadBatchTaskFromPath
 * 会触发 BeanContainer.instance()，测试环境若无容器会抛异常，但此时 partitionRange 已写入捕获的 context。
 */
public class TestBatchTaskRunnerPartition {

    @Test
    public void testResolverInjectsPartitionRange() {
        StubManager mgr = new StubManager();
        BatchTaskRunner runner = new BatchTaskRunner();
        runner.setBatchTaskManager(mgr);
        runner.setPartitionResolver(stubResolver("10,20"));

        runIgnoreContainerError(runner);

        assertEquals("10,20", partitionOf(mgr));
    }

    @Test
    public void testNoResolverLeavesPartitionRangeNull() {
        StubManager mgr = new StubManager();
        BatchTaskRunner runner = new BatchTaskRunner();
        runner.setBatchTaskManager(mgr);
        runner.setPartitionResolver(null);

        runIgnoreContainerError(runner);

        assertNull(partitionOf(mgr));
    }

    @Test
    public void testResolverReturningNullLeavesPartitionRangeNull() {
        StubManager mgr = new StubManager();
        BatchTaskRunner runner = new BatchTaskRunner();
        runner.setBatchTaskManager(mgr);
        // assignedPartitions 为空 + enableCluster=false → resolvePartitions 返回 null
        runner.setPartitionResolver(stubResolver(null));

        runIgnoreContainerError(runner);

        assertNull(partitionOf(mgr));
    }

    private static void runIgnoreContainerError(BatchTaskRunner runner) {
        try {
            runner.execute("/any.batch.xml");
        } catch (Exception ignored) {
            // BeanContainer 未初始化导致 loadBatchTaskFromPath 抛异常，与 partition 接线无关
        }
    }

    private static String partitionOf(StubManager mgr) {
        IntRangeSet p = mgr.captured.getPartitionRange();
        return p == null ? null : p.toString();
    }

    private static PartitionResolver stubResolver(String assignedPartitions) {
        PartitionResolver r = new PartitionResolver();
        r.setAssignedPartitions(assignedPartitions);
        return r;
    }

    static class StubManager implements IBatchTaskManager {
        IBatchTaskContext captured;

        @Override
        public IBatchTaskContext newBatchTaskContext() {
            captured = new BatchTaskContextImpl();
            return captured;
        }

        @Override
        public IBatchTaskContext newBatchTaskContext(io.nop.core.context.IServiceContext svcCtx,
                                                      io.nop.core.lang.eval.IEvalScope scope) {
            return newBatchTaskContext();
        }

        @Override
        public IBatchTask newBatchTask(String batchTaskName, Long batchTaskVersion,
                                       io.nop.api.core.ioc.IBeanProvider beanProvider) {
            return new NoopTask();
        }

        @Override
        public IBatchTask newBatchTaskFromModel(io.nop.core.lang.xml.XNode node,
                                                 io.nop.api.core.ioc.IBeanProvider beanProvider,
                                                 io.nop.xlang.api.IXLangCompileScope scope) {
            return new NoopTask();
        }

        @Override
        public IBatchTask loadBatchTaskFromPath(String path, io.nop.api.core.ioc.IBeanProvider beanProvider) {
            return new NoopTask();
        }
    }

    static class NoopTask implements IBatchTask {
        @Override
        public CompletionStage<Void> executeAsync(IBatchTaskContext context) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
