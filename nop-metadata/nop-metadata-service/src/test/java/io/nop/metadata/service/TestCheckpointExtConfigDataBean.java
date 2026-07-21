/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service;

import io.nop.core.lang.json.JsonTool;
import io.nop.metadata.core.dto.CheckpointExtConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2026-07-19-1250-3 Phase 4 Proof：验证 extConfig JSON 可反序列化为强类型 bean（维度15-03）。
 *
 * <p>{@link CheckpointExtConfig} 是 plan Phase 4 引入的 3 个高频 JSON 字段（extConfig / actions / validations）
 * 之一的 @DataBean，消除一处 {@code @SuppressWarnings("unchecked")}。
 */
public class TestCheckpointExtConfigDataBean {

    @Test
    public void testEmptyJsonGivesDefaults() {
        CheckpointExtConfig cfg = new CheckpointExtConfig();
        assertNull(cfg.getSchedule());
        assertNull(cfg.getAutoScore());
        // null autoScore 视为 true（默认开启）
        assertTrue(cfg.isAutoScoreEffective(), "null autoScore must default to true");
    }

    @Test
    public void testExplicitAutoScoreFalse() {
        CheckpointExtConfig cfg = new CheckpointExtConfig();
        cfg.setAutoScore(false);
        assertFalse(cfg.isAutoScoreEffective(), "explicit false must be respected");
    }

    @Test
    public void testExplicitAutoScoreTrue() {
        CheckpointExtConfig cfg = new CheckpointExtConfig();
        cfg.setAutoScore(true);
        assertTrue(cfg.isAutoScoreEffective());
    }

    @Test
    public void testScheduleValue() {
        CheckpointExtConfig cfg = new CheckpointExtConfig();
        cfg.setSchedule("0 0/5 * * * ?");
        assertEquals("0 0/5 * * * ?", cfg.getSchedule());
    }

    @Test
    public void testJsonRoundTrip() {
        CheckpointExtConfig original = new CheckpointExtConfig();
        original.setSchedule("0 0 12 * * ?");
        original.setAutoScore(false);

        String json = JsonTool.stringify(original);
        assertTrue(json.contains("0 0 12 * * ?"));
        assertTrue(json.contains("autoScore"));

        CheckpointExtConfig parsed = JsonTool.parseBeanFromText(json, CheckpointExtConfig.class);
        assertEquals("0 0 12 * * ?", parsed.getSchedule());
        assertEquals(false, parsed.getAutoScore());
        assertFalse(parsed.isAutoScoreEffective());
    }

    @Test
    public void testParseFromRealisticJson() {
        // 模拟真实 NopMetaQualityCheckpoint.extConfig JSON 字符串
        String realJson = "{\"schedule\":\"0 0/15 * * * ?\",\"autoScore\":true}";
        CheckpointExtConfig parsed = JsonTool.parseBeanFromText(realJson, CheckpointExtConfig.class);
        assertEquals("0 0/15 * * * ?", parsed.getSchedule());
        assertEquals(true, parsed.getAutoScore());
        assertTrue(parsed.isAutoScoreEffective());
    }

    @Test
    public void testParseFromJsonMissingAutoScore() {
        // 仅 schedule 键，无 autoScore（应默认 true）
        String realJson = "{\"schedule\":\"0 0/15 * * * ?\"}";
        CheckpointExtConfig parsed = JsonTool.parseBeanFromText(realJson, CheckpointExtConfig.class);
        assertEquals("0 0/15 * * * ?", parsed.getSchedule());
        assertNull(parsed.getAutoScore());
        assertTrue(parsed.isAutoScoreEffective(), "missing autoScore must default to true");
    }
}
