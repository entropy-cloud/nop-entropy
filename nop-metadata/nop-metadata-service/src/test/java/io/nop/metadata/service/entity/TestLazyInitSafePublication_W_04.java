
package io.nop.metadata.service.entity;

import io.nop.metadata.service.quality.CheckpointActionDispatcher;
import io.nop.metadata.service.quality.QualityAlertWorkflowProcessor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-04 回归钉（plan 2528 红③，CRA 5.5 C1-102+C1-001+C4-001）：ensure* 懒初始化族统一安全发布。
 *
 * <p>结构钉（确定性当前即红）：反射扫描硬编码 9 载体 12 字段清单（裁定③表逐名），断言全部
 * {@code volatile}——pre-fix 0/12 volatile → 结构钉确定性红。
 *
 * <p>可见性钉（publish-then-configure 中间态闭合）：两线程并发首触发 {@code ensureActionDispatcher}
 * （等价于两线程经两个不同 checkpointId 首并发 executeCheckpoint 到达同一 ensure 缝——同一
 * checkpointId 被 R4.3 运行标记挡在 ensure 之前），断言每线程实际取到的 dispatcher 生效 webhook
 * 超时 == 配置值（configure 先于字段发布——记录型断言）。裁定③ M2：无 identity 单实例断言
 * （volatile 下重复构造为良性竞态，identity 断言即构造性 flake）。pre-fix 存在可观察竞态窗口
 * （间歇红——非确定性红面如实标注）；修复后中间态不可达、恒绿。
 */
public class TestLazyInitSafePublication_W_04 {

    /** 裁定③ 12 字段硬编码清单（9 载体逐一，字段级分配与设计文档一致）。 */
    private static final Map<Class<?>, List<String>> LAZY_INIT_FIELDS = buildFieldInventory();

    private static Map<Class<?>, List<String>> buildFieldInventory() {
        Map<Class<?>, List<String>> inv = new LinkedHashMap<>();
        inv.put(NopMetaQualityCheckpointBizModel.class,
                List.of("tableRefExecutor", "checkpointExecutor", "actionDispatcher"));
        inv.put(NopMetaEntityBizModel.class, List.of("tableRefExecutor"));
        inv.put(NopMetaDataSourceBizModel.class, List.of("tableRefExecutor"));
        inv.put(NopMetaQualityRuleBizModel.class, List.of("tableRefExecutor"));
        inv.put(NopMetaProfilingRuleBizModel.class, List.of("tableRefExecutor"));
        inv.put(NopMetaQualityScoreBizModel.class, List.of("scorer"));
        inv.put(NopMetaLineageEdgeBizModel.class, List.of("queryAction"));
        inv.put(NopMetaEntityQueryAction.class, List.of("sqlFieldTypeInferrer", "tableRefExecutor"));
        inv.put(QualityAlertWorkflowProcessor.class, List.of("tableRefExecutor"));
        return inv;
    }

    /** 结构钉：12 字段全部 volatile（pre-fix 0/12 → 确定性红）。 */
    @Test
    public void testAllLazyInitInstanceFieldsAreVolatile() throws Exception {
        List<String> nonVolatile = new ArrayList<>();
        int checked = 0;
        for (Map.Entry<Class<?>, List<String>> entry : LAZY_INIT_FIELDS.entrySet()) {
            for (String fieldName : entry.getValue()) {
                Field field = entry.getKey().getDeclaredField(fieldName);
                checked++;
                if (!Modifier.isVolatile(field.getModifiers())) {
                    nonVolatile.add(entry.getKey().getSimpleName() + "#" + fieldName);
                }
            }
        }
        assertEquals(12, checked, "inventory must cover the full 裁定③ 12-field list");
        assertTrue(nonVolatile.isEmpty(),
                "lazy-init instance fields must be volatile (safe publication, W-04): " + nonVolatile);
    }

    /**
     * 可见性钉：两线程并发首触发 ensureActionDispatcher，各自取到的 dispatcher 生效 webhook 超时
     * 必须 == 配置值（45≠缺省 30——区分配置生效与缺省值）。多轮扫描扩大 pre-fix 竞态窗口命中面；
     * post-fix configure 先于 volatile 发布 → 中间态不可达 → 恒绿。
     */
    @Test
    public void testConcurrentEnsurePublishesConfiguredDispatcher() throws Exception {
        int rounds = 120;
        for (int round = 0; round < rounds; round++) {
            NopMetaQualityCheckpointBizModel bizModel = new NopMetaQualityCheckpointBizModel();
            bizModel.webhookTimeoutSeconds = 45;
            Method ensure = NopMetaQualityCheckpointBizModel.class
                    .getDeclaredMethod("ensureActionDispatcher");
            ensure.setAccessible(true);

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            Callable<Integer> trigger = () -> {
                start.await(10, TimeUnit.SECONDS);
                CheckpointActionDispatcher dispatcher =
                        (CheckpointActionDispatcher) ensure.invoke(bizModel);
                return effectiveWebhookTimeoutSeconds(dispatcher);
            };
            Future<Integer> first = pool.submit(trigger);
            Future<Integer> second = pool.submit(trigger);
            start.countDown();
            int timeoutA = first.get(30, TimeUnit.SECONDS);
            int timeoutB = second.get(30, TimeUnit.SECONDS);
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "round " + round + " must finish");
            assertEquals(45, timeoutA,
                    "round " + round + ": published dispatcher must carry the configured webhook timeout (45),"
                            + " never the unconfigured default 30 (publish-then-configure window, W-04)");
            assertEquals(45, timeoutB,
                    "round " + round + ": concurrently read dispatcher must carry the configured webhook"
                            + " timeout (45) (W-04 visibility)");
        }
    }

    /** CheckpointActionDispatcher#webhookTimeoutSeconds 为 protected 字段——同包外经反射读取生效值。 */
    private static int effectiveWebhookTimeoutSeconds(CheckpointActionDispatcher dispatcher)
            throws IllegalAccessException {
        Field field;
        try {
            field = CheckpointActionDispatcher.class.getDeclaredField("webhookTimeoutSeconds");
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("dispatcher must expose webhookTimeoutSeconds field", e);
        }
        field.setAccessible(true);
        return (Integer) field.get(dispatcher);
    }
}
