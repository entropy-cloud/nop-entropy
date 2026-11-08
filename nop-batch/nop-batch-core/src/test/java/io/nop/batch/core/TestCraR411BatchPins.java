package io.nop.batch.core;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.batch.core.consumer.BatchProcessorConsumer;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.batch.core.loader.ListBatchLoader;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    // ==================== 红⑥（C5-003 接线，closure audit B-2）：配置键真实接线 ====================

    /**
     * 红⑥（closure audit B-2）：上界必须在**生产接线**上生效——`BatchTaskBuilder.buildChunkProcessor`
     * 从 `nop.batch.max-output-items-per-chunk` 读取并注入 `BatchProcessorConsumer`。
     * <p>
     * 缺陷态（审计实测）：唯一设置入口 `setMaxOutputItemsPerChunk` 在生产代码零调用方，
     * 该上界在全部装配路径恒为 0（不限制）——"接口存在 ≠ 行为完成"。
     * <p>
     * 本钉走**真实 builder → 真实 executeAsync**：只设置配置键（不碰 setter），任务必须因超限失败。
     */
    @Test
    public void pin06_outputBoundConfigKeyWiredThroughBuilder() {
        int prev = BatchConfigs.CFG_BATCH_MAX_OUTPUT_ITEMS_PER_CHUNK.get();
        AppConfig.getConfigProvider().updateConfigValue(BatchConfigs.CFG_BATCH_MAX_OUTPUT_ITEMS_PER_CHUNK, 10);
        try {
            BatchTaskBuilder<Object, Object> builder = new BatchTaskBuilder<>();
            builder.taskName("c5-003-pin");
            // 一次性 loader：只在首 chunk 返回 1 条，之后返回空（避免变异时无限循环）
            java.util.concurrent.atomic.AtomicBoolean loaded = new java.util.concurrent.atomic.AtomicBoolean();
            builder.loader(ctx -> (batchSize, chunkCtx) -> loaded.compareAndSet(false, true)
                    ? new ArrayList<>(java.util.Collections.singletonList(new Object()))
                    : new ArrayList<>());
            builder.processor(ctx -> (item, consumer, chunkCtx) -> {
                for (int i = 0; i < 100; i++)
                    consumer.accept("out-" + i);
            });
            builder.consumer(ctx -> (items, chunkCtx) -> {
            });
            builder.concurrency(1);

            IBatchTask task = builder.buildTask();
            Throwable thrown = assertThrows(Throwable.class,
                    () -> FutureHelper.syncGet(task.executeAsync(newTaskTaskContext())));
            // 注意：loader 返回同一对象后需停止——用一次性 loader 避免无限循环
            assertTrue(containsErrorCode(thrown, "nop.err.batch.too-many-processing-items"),
                    "配置键 10 必须经 builder 接线生效并 fail-fast，实际: " + thrown);
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(BatchConfigs.CFG_BATCH_MAX_OUTPUT_ITEMS_PER_CHUNK, prev);
        }
    }

    // ==================== 红⑦（C6-008，closure audit B-1）：saveState + 异步派发 fail-fast ====================

    /**
     * 红⑦（closure audit B-1）：saveState + fetchThreadCount>0 是**不支持**的组合，必须装配期响亮失败。
     * <p>
     * 缺陷态（审计实测）：原实现只对 fetch 线程自建的一次性 ctx 调 `complete()`，但该 ctx 的
     * `getChunkItems()` 恒为 null，压实回调只是空转——completedIndex 永不推进，任务 100% 以
     * ERR_BATCH_PROCESSING_ITEMS_NOT_EMPTY 失败，且错误信息不指向真实根因（dispatcher 组合）。
     */
    @Test
    public void pin07_saveStateWithAsyncDispatchFailsFast() {
        // 压实 loader（saveState=true 的 ResourceRecord）+ task 级 stateStore 双轴同时成立
        io.nop.batch.core.loader.ResourceRecordLoaderProvider<String> provider =
                new io.nop.batch.core.loader.ResourceRecordLoaderProvider<>();
        provider.setSaveState(true);

        BatchTaskBuilder<String, String> builder = new BatchTaskBuilder<>();
        builder.taskName("c6-008-pin");
        builder.loader(provider);
        builder.consumer(ctx -> (items, chunkCtx) -> {
        });
        // 非 null state store 表达 task 级 saveState=true 语义
        builder.stateStore(new StateStoreStub());

        BatchDispatchConfig<String> dispatch = new BatchDispatchConfig<>();
        dispatch.setLoadBatchSize(10);
        dispatch.setFetchThreadCount(1);
        dispatch.setPartitionFn((item, ctx) -> 0);
        builder.dispatchConfig(dispatch);

        IBatchTask task = builder.buildTask();
        // 守卫在 buildLoader（setup 期）而非构造期，故经 executeAsync 触发；
        // 异常经 onTaskComplete → future 异常终态 → syncGet 重抛（CompletionException 包裹），故走 cause 链。
        Throwable thrown = assertThrows(Throwable.class,
                () -> FutureHelper.syncGet(task.executeAsync(newTaskTaskContext())));
        assertTrue(containsErrorCode(thrown, "nop.err.batch.save-state-with-dispatcher-unsupported"),
                "saveState + 异步派发必须以专属错误码响亮失败，实际: " + thrown);
    }

    @Test
    public void pin08_loaderLevelSaveStateWithAsyncDispatchFailsFast() {
        // R2 BL-1：saveState 有两条独立轴——任务级（stateStore）与 loader 级
        // （ResourceRecordLoaderProvider.setSaveState(true)，DSL <loader saveState="true">）。
        // 本钉只置 loader 级、**不**设 stateStore，证明守卫判定轴是压实能力而非 stateStore。
        io.nop.batch.core.loader.ResourceRecordLoaderProvider<String> provider =
                new io.nop.batch.core.loader.ResourceRecordLoaderProvider<>();
        provider.setSaveState(true);
        assertTrue(provider.hasChunkCompaction(), "saveState=true 的 ResourceRecord 必须声明 chunk 压实能力");

        BatchTaskBuilder<String, String> builder = new BatchTaskBuilder<>();
        builder.taskName("c6-008-pin-loader-level");
        builder.loader(provider);
        builder.consumer(ctx -> (items, chunkCtx) -> {
        });
        // 关键：不设 stateStore（缺陷态守卫只判 stateStore 时本用例不会失败）

        BatchDispatchConfig<String> dispatch = new BatchDispatchConfig<>();
        dispatch.setLoadBatchSize(10);
        dispatch.setFetchThreadCount(1);
        dispatch.setPartitionFn((item, ctx) -> 0);
        builder.dispatchConfig(dispatch);

        IBatchTask task = builder.buildTask();
        Throwable thrown = assertThrows(Throwable.class,
                () -> FutureHelper.syncGet(task.executeAsync(newTaskTaskContext())));
        assertTrue(containsErrorCode(thrown, "nop.err.batch.save-state-with-dispatcher-unsupported"),
                "loader 级 saveState + 异步派发同样必须响亮失败（R2 BL-1），实际: " + thrown);
    }

    /**
     * R2 MJ-4：守卫不得过宽——不承担 chunk 压实能力的 loader（jdbc/orm/列表等）在 task 级
     * saveState + 异步派发下是合法组合，不得被拒。
     */
    @Test
    public void pin09_nonCompactingLoaderWithAsyncDispatchIsAllowed() {
        BatchTaskBuilder<String, String> builder = new BatchTaskBuilder<>();
        builder.taskName("c6-008-pin-allowed");
        // ArrayList 返回的普通 provider：hasChunkCompaction() 缺省 false
        builder.loader(ctx -> (batchSize, chunkCtx) -> new ArrayList<String>());
        builder.consumer(ctx -> (items, chunkCtx) -> {
        });
        builder.stateStore(new StateStoreStub());

        BatchDispatchConfig<String> dispatch = new BatchDispatchConfig<>();
        dispatch.setLoadBatchSize(10);
        dispatch.setFetchThreadCount(1);
        dispatch.setPartitionFn((item, ctx) -> 0);
        builder.dispatchConfig(dispatch);

        // 守卫必须放行：装配成功即证明未过宽（任务随后正常跑完空数据）
        IBatchTask task = builder.buildTask();
        FutureHelper.syncGet(task.executeAsync(newTaskTaskContext()));
    }

    /**
     * R3 Major-1：包装 provider 必须转发 chunk 压实能力——`withHook` 与 adapter 形态的裸 lambda
     * 会静默把 `hasChunkCompaction()` 打回 false，使装配期 fail-fast 守卫失效。
     */
    @Test
    public void pin10_wrapperProvidersPreserveChunkCompaction() {
        io.nop.batch.core.loader.ResourceRecordLoaderProvider<String> provider =
                new io.nop.batch.core.loader.ResourceRecordLoaderProvider<>();
        provider.setSaveState(true);
        assertTrue(provider.hasChunkCompaction(), "真实压实 provider 必须返回 true");

        // withHook 缺省方法形态
        IBatchLoaderProvider<String> hooked = provider.withHook(loader -> loader);
        assertTrue(hooked.hasChunkCompaction(),
                "withHook 包装必须转发压实能力（裸 lambda 会擦除 → 守卫失效）");

        // adapter 形态（等价 class 载体）
        IBatchLoaderProvider<String> adapted =
                new io.nop.batch.core.loader.AdaptedBatchLoaderProvider<>((loader, ctx) -> loader, provider);
        assertTrue(adapted.hasChunkCompaction(), "AdaptedBatchLoaderProvider 必须转发压实能力");

        // 非压实 provider 经包装后仍为 false（不得反向误标）
        IBatchLoaderProvider<String> plain = ctx -> (batchSize, chunkCtx) -> new ArrayList<String>();
        assertFalse(plain.hasChunkCompaction(), "普通 provider 缺省必须为 false");
        assertFalse(plain.withHook(loader -> loader).hasChunkCompaction(), "包装不得凭空制造压实能力");
    }

    private static boolean containsErrorCode(Throwable e, String code) {
        while (e != null) {
            if (e instanceof NopException && code.equals(((NopException) e).getErrorCode()))
                return true;
            e = e.getCause();
        }
        return false;
    }

    private static IBatchTaskContext newTaskTaskContext() {
        return new io.nop.batch.core.impl.BatchTaskContextImpl();
    }

    /** 最小 state store 桩：仅用于表达 saveState=true（不参与本钉断言）。 */
    static class StateStoreStub implements IBatchStateStore {
        @Override
        public void loadTaskState(IBatchTaskContext context) {
        }

        @Override
        public void saveTaskState(boolean complete, Throwable err, IBatchTaskContext context) {
        }
    }

    // ==================== 测试载体 ====================

    private static IBatchChunkContext newTaskChunkContext() {
        return new io.nop.batch.core.impl.BatchTaskContextImpl().newChunkContext();
    }
}
