package io.nop.batch.core;

import io.nop.api.core.exceptions.NopException;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * C8-001（plan 2516 裁定②，←C5-005；C10-003）：dispatch 容量装配期 fail-fast。
 * loadBatchSize<=0 五层装配链（DSL/程序化 → BatchTaskBuilder.buildLoader → dispatch provider →
 * PartitionDispatchQueue → Semaphore）零护栏时，orm-reader 全表单页加载对 Semaphore(0) 永久阻塞挂死、
 * 其他 reader 静默产出空任务。修复后 buildLoader 必经点单点 Guard，携 loadBatchSize 配置上下文。
 * 次序约束（族 A4）：C8-001 Guard 先于 R4-12 C8-009 幽灵旋钮参数化。
 */
@Timeout(30)
public class TestBatchDispatchLoadBatchSizeGuard {

    private static BatchTaskBuilder<String, String> newBuilder(int loadBatchSize) {
        BatchDispatchConfig<String> config = new BatchDispatchConfig<>();
        config.setLoadBatchSize(loadBatchSize);
        config.setPartitionFn((item, ctx) -> 0);
        return BatchTaskBuilder.<String, String>create()
                .taskName("guard-test")
                .loader(taskCtx -> (batchSize, chunkCtx) -> Collections.emptyList())
                .consumer(taskCtx -> (items, ctx) -> {
                })
                .dispatchConfig(config);
    }

    @Test
    public void zeroLoadBatchSize_failsFastAtAssembly_withConfigContext() {
        // 缺陷态（红）：loadBatchSize=0（Java API 缺省）静默装配 Semaphore(0) → 运行期永久挂死
        NopException ex = assertThrows(NopException.class,
                () -> newBuilder(0).buildLoader(new BatchTaskContextImpl()));
        assertEquals("nop.err.batch.dispatch-load-batch-size-invalid", ex.getErrorCode());
        // 携配置上下文（C10-003 断言形态：Guard fail-fast 用例带 loadBatchSize 配置上下文）
        assertEquals(0, ex.getParam("loadBatchSize"));
    }

    @Test
    public void negativeLoadBatchSize_failsFastAtAssembly() {
        NopException ex = assertThrows(NopException.class,
                () -> newBuilder(-5).buildLoader(new BatchTaskContextImpl()));
        assertEquals("nop.err.batch.dispatch-load-batch-size-invalid", ex.getErrorCode());
        assertEquals(-5, ex.getParam("loadBatchSize"));
    }

    /**
     * 复核修正①口径（C10-003）：合法 loadBatchSize 装配出非空 dispatch loader——
     * fail-fast 不是把 dispatch 路径整体禁用，orm-reader 路径修复后期望终态 = fail-fast 非空任务成功。
     */
    @Test
    public void validLoadBatchSize_assemblesDispatchLoader() {
        IBatchLoaderProvider.IBatchLoader<String> loader = newBuilder(10).buildLoader(new BatchTaskContextImpl());
        assertNotNull(loader, "valid loadBatchSize must assemble a dispatch loader");
    }
}
