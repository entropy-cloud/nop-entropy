package io.nop.batch.core;

import io.nop.api.core.exceptions.NopException;
import io.nop.batch.core.consumer.BatchProcessorConsumer;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.batch.core.loader.ListBatchLoader;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2545 R4-11 红钉族（不依赖数据库的纯单元钉；DB 相关项由既有 nop-batch 全量测试承载）。
 */
public class TestCraR411BatchPins {

    // ==================== 红①（C1-001）：TL save/restore ====================

    /**
     * 红①：外层上下文在「内层 provide + remove」之后必须仍可读。
     * 缺陷态：裸 provide/remove 使外层 TL 被确定性清空。
     */
    @Test
    public void pin01_nestedTaskContextIsRestored() {
        IBatchTaskContext outer = new BatchTaskContextImpl();
        IBatchTaskContext inner = new BatchTaskContextImpl();

        BatchTaskGlobals.provideTaskContext(outer);
        try {
            Object prev = BatchTaskGlobals.saveTaskContext();
            assertSame(outer, prev);

            // 模拟内层任务执行
            BatchTaskGlobals.provideTaskContext(inner);
            assertSame(inner, BatchTaskGlobals.useTaskContext());

            // 恢复（生产由 BatchTask 两站点 finally 调用）
            BatchTaskGlobals.restoreTaskContext(prev);
            assertSame(outer, BatchTaskGlobals.useTaskContext(),
                    "内层任务结束后外层上下文必须恢复（缺陷态=被清空）");
        } finally {
            BatchTaskGlobals.removeTaskContext();
        }

        assertNull(BatchTaskGlobals.useTaskContext());
    }

    /** 红①b：外层本无上下文时，restore(null) 必须回到「无上下文」而非残留内层。 */
    @Test
    public void pin01b_restoreNullClearsInnerContext() {
        BatchTaskGlobals.removeTaskContext();
        Object prev = BatchTaskGlobals.saveTaskContext();
        assertNull(prev);

        BatchTaskGlobals.provideTaskContext(new BatchTaskContextImpl());
        BatchTaskGlobals.restoreTaskContext(prev);
        assertNull(BatchTaskGlobals.useTaskContext(), "外层无上下文时恢复必须回到无上下文");
    }

    // ==================== 红③（C1-003）：swap-on-fire 守卫 ====================

    /**
     * 红③：`onTaskBegin` 在 fire 之后注册必须显式失败（而非静默接受后永不触发）。
     * 缺陷态：只查 isDone → 接受注册、回调永不执行。
     */
    @Test
    public void pin03_onTaskBeginAfterFireFailsFast() {
        BatchTaskContextImpl ctx = new BatchTaskContextImpl();

        AtomicInteger fired = new AtomicInteger();
        ctx.onTaskBegin(fired::incrementAndGet);
        ctx.fireTaskBegin();
        assertEquals(1, fired.get(), "fire 前注册的回调必须执行");

        // fire 之后注册：必须响亮失败（缺陷态=静默接受）
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ctx.onTaskBegin(() -> {
                }));
        assertTrue(ex.getMessage().contains("already-fired"), "错误消息须指向已触发语义: " + ex.getMessage());
    }

    // ==================== 红④（C5-004）：List 常驻告警 ====================

    /** 红④：超大 List 构造期打 WARN（可观测）；小 List 不告警且行为不变。 */
    @Test
    public void pin04_largeResidentListIsObservable() {
        List<Object> small = new ArrayList<>();
        for (int i = 0; i < 10; i++)
            small.add(i);
        ListBatchLoader<Object, Object> loader = new ListBatchLoader<>(small);
        assertEquals(10, loader.load(100, null).size(), "小 List 分页行为不变");

        // 构造规模超过阈值不抛（只告警）——断言构造与后续分页均可完成
        List<Object> large = new ArrayList<>();
        for (int i = 0; i < 1_000_001; i++)
            large.add(null);
        ListBatchLoader<Object, Object> largeLoader = new ListBatchLoader<>(large);
        assertNotNull(largeLoader.load(10, null), "超大 List 只告警不拒绝（既有部署兼容）");
    }

    // ==================== 红⑤（C5-003）：输出集上界 ====================

    /**
     * 红⑤：单 chunk 输出数超上界必须 fail-fast。
     * 缺陷态：累积到 totalCount 才下发（totalCount 50 万 ×500B ≈ 数百 MB）。
     */
    @Test
    public void pin05_outputBoundFailsFast() {
        IBatchProcessorProvider.IBatchProcessor<Object, Object> expanding = (item, consumer, ctx) -> {
            for (int i = 0; i < 100; i++)
                consumer.accept("out-" + i);
        };
        BatchProcessorConsumer<Object, Object> consumer = new BatchProcessorConsumer<>(
                expanding, (outputs, ctx) -> {
        });
        consumer.setMaxOutputItemsPerChunk(10);

        List<Object> items = List.of(new Object());
        NopException ex = assertThrows(NopException.class, () -> consumer.consume(items, newTaskChunkContext()));
        assertEquals("nop.err.batch.too-many-processing-items", ex.getErrorCode(),
                "超限必须 fail-fast 为 ERR_BATCH_TOO_MANY_PROCESSING_ITEMS");
    }

    /** 红⑤b：未设上界（0）时保持既有行为（不限制）。 */
    @Test
    public void pin05b_noBoundKeepsExistingBehavior() {
        AtomicInteger emitted = new AtomicInteger();
        IBatchProcessorProvider.IBatchProcessor<Object, Object> expanding = (item, consumer, ctx) -> {
            for (int i = 0; i < 100; i++)
                consumer.accept("out-" + i);
        };
        BatchProcessorConsumer<Object, Object> consumer = new BatchProcessorConsumer<>(
                expanding, (outputs, ctx) -> emitted.addAndGet(outputs.size()));

        consumer.consume(List.of(new Object()), newTaskChunkContext());
        assertEquals(100, emitted.get());
    }

    // ==================== 测试载体 ====================

    private static IBatchChunkContext newTaskChunkContext() {
        return new io.nop.batch.core.impl.BatchTaskContextImpl().newChunkContext();
    }
}
