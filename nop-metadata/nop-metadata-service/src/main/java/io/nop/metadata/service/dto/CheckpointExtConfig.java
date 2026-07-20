/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;

/**
 * NopMetaQualityCheckpoint.extConfig 强类型 DTO（plan 2026-07-19-1250-3 Phase 4 维度15-03）。
 *
 * <p>对应原 {@code Map<String,Object>} JSON 反序列化路径：{@code {schedule, autoScore}}。
 * JsonOrmComponent 可直接反序列化为强类型 bean，消除一处 {@code @SuppressWarnings("unchecked")}。
 *
 * <p>字段语义：
 * <ul>
 *   <li>{@code schedule} — cron 表达式（MetaQualityCheckpointScheduler.registerCheckpoint 解析）</li>
 *   <li>{@code autoScore} — checkpoint 执行完成后是否自动触发评分（默认 true；null 视为 true）</li>
 * </ul>
 */
@DataBean
public class CheckpointExtConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    /** cron 表达式（可为空：表示该 checkpoint 不挂定时调度）。 */
    private String schedule;

    /** 执行完成后是否自动评分；null/缺失视为 true（与既有 readAutoScoreConfig 默认值一致）。 */
    private Boolean autoScore;

    public String getSchedule() {
        return schedule;
    }

    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }

    public Boolean getAutoScore() {
        return autoScore;
    }

    public void setAutoScore(Boolean autoScore) {
        this.autoScore = autoScore;
    }

    /**
     * autoScore 是否生效（null/缺失 → 默认 true）。
     */
    public boolean isAutoScoreEffective() {
        return autoScore == null || autoScore;
    }
}
