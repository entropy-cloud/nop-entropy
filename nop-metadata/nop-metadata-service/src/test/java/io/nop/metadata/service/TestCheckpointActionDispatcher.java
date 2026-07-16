/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service;

import io.nop.metadata.dao.entity.NopMetaQualityCheckpoint;
import io.nop.metadata.service.quality.CheckpointActionDispatcher;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 直接单元测试 {@link CheckpointActionDispatcher} 的 null-dep / config-validation 路径，
 * 补充集成测试（{@link TestNopMetaQualityCheckpointBizModel}，mock beans 始终注册）无法覆盖的
 * 「IHttpClient/IMessageService 为 null」场景。
 *
 * <p>覆盖 Exit Criteria：(d) IHttpClient 为 null → webhook 显式失败 (ErrorCode，不 NPE)；
 * (c-notify) IMessageService 为 null → notify 显式失败。dispatch 方法 per-action try/catch 隔离，
 * 失败记入 summary errors（不抛出、不 NPE、不静默跳过）。
 */
public class TestCheckpointActionDispatcher {

    private NopMetaQualityCheckpoint cp(String actions) {
        NopMetaQualityCheckpoint cp = new NopMetaQualityCheckpoint();
        cp.setCheckpointId("cp-test");
        cp.setActions(actions);
        return cp;
    }

    private Map<String, Object> summary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("checkpointId", "cp-test");
        summary.put("executedCount", 1);
        summary.put("errors", new ArrayList<>());
        return summary;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> errorsOf(Map<String, Object> summary) {
        return (List<Map<String, Object>>) summary.get("errors");
    }

    private static boolean hasActionError(List<Map<String, Object>> errors, String actionType) {
        return errors.stream().anyMatch(e -> actionType.equals(e.get("actionType")));
    }

    /** IHttpClient 为 null 时 webhook 显式失败（ErrorCode，不 NPE、不静默跳过）。 */
    @Test
    public void testWebhookNullClientFailsExplicitly() {
        CheckpointActionDispatcher dispatcher = new CheckpointActionDispatcher(null, null);
        NopMetaQualityCheckpoint cp = cp("[{\"actionType\":\"webhook\",\"enabled\":true,"
                + "\"config\":{\"url\":\"http://mock/quality\"}}]");
        Map<String, Object> summary = summary();
        assertDoesNotThrow(() -> dispatcher.dispatch(cp, summary));
        assertTrue(hasActionError(errorsOf(summary), "webhook"),
                "webhook null-client failure must be recorded in errors (no NPE, no silent skip)");
        assertEquals(1, errorsOf(summary).size(), "exactly one error for webhook null-client");
    }

    /** webhook config 缺 url → 显式失败（直接验证 ErrorCode，不依赖 IoC）。 */
    @Test
    public void testWebhookMissingUrlFailsDirectly() {
        CheckpointActionDispatcher dispatcher = new CheckpointActionDispatcher(null, null);
        NopMetaQualityCheckpoint cp = cp("[{\"actionType\":\"webhook\",\"enabled\":true,\"config\":{}}]");
        Map<String, Object> summary = summary();
        assertDoesNotThrow(() -> dispatcher.dispatch(cp, summary));
        assertTrue(hasActionError(errorsOf(summary), "webhook"),
                "webhook missing-url failure must be recorded in errors");
    }

    /** IMessageService 为 null 时 notify 显式失败（ErrorCode，不 NPE、不静默跳过）。 */
    @Test
    public void testNotifyNullServiceFailsExplicitly() {
        CheckpointActionDispatcher dispatcher = new CheckpointActionDispatcher(null, null);
        NopMetaQualityCheckpoint cp = cp("[{\"actionType\":\"notify\",\"enabled\":true,"
                + "\"config\":{\"channel\":\"test-channel\"}}]");
        Map<String, Object> summary = summary();
        assertDoesNotThrow(() -> dispatcher.dispatch(cp, summary));
        assertTrue(hasActionError(errorsOf(summary), "notify"),
                "notify null-service failure must be recorded in errors (no NPE, no silent skip)");
    }

    /** notify config 缺 channel → 显式失败（直接验证 ErrorCode，不依赖 IoC）。 */
    @Test
    public void testNotifyMissingChannelFailsDirectly() {
        CheckpointActionDispatcher dispatcher = new CheckpointActionDispatcher(null, null);
        NopMetaQualityCheckpoint cp = cp("[{\"actionType\":\"notify\",\"enabled\":true,\"config\":{}}]");
        Map<String, Object> summary = summary();
        assertDoesNotThrow(() -> dispatcher.dispatch(cp, summary));
        assertTrue(hasActionError(errorsOf(summary), "notify"),
                "notify missing-channel failure must be recorded in errors");
    }

    /** 空 actions → 无投递、无错误（store-only 默认，合法）。 */
    @Test
    public void testEmptyActionsNoDispatch() {
        CheckpointActionDispatcher dispatcher = new CheckpointActionDispatcher(null, null);
        NopMetaQualityCheckpoint cp = cp(null);
        Map<String, Object> summary = summary();
        assertDoesNotThrow(() -> dispatcher.dispatch(cp, summary));
        assertEquals(0, errorsOf(summary).size(), "no errors for empty/null actions");
    }

    /** store 动作 → 不投递、无错误（executor 已隐式完成 store）。 */
    @Test
    public void testStoreActionNoDispatch() {
        CheckpointActionDispatcher dispatcher = new CheckpointActionDispatcher(null, null);
        NopMetaQualityCheckpoint cp = cp("[{\"actionType\":\"store\",\"enabled\":true}]");
        Map<String, Object> summary = summary();
        assertDoesNotThrow(() -> dispatcher.dispatch(cp, summary));
        assertEquals(0, errorsOf(summary).size(), "no errors for store action (executor handles it)");
    }
}
