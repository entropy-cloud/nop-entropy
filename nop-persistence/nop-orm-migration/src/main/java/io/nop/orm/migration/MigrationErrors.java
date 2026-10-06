/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

/**
 * WI9 数据搬迁编排模块错误码族（设计 §4.8）。
 *
 * <p>编排层错误全部落本接口；写停止关的执行错误随检查点所在层登记
 * {@code OrmErrors.ERR_ORM_MIGRATION_WRITE_GATE_CLOSED}；数据面错误复用既有码
 * （路由策略 {@code ERR_DAO_SHARD_*}、sink 写入 {@code ERR_STREAM_*}、batch 门控
 * {@code ERR_BATCH_TASK_*}）。</p>
 */
@Locale("zh-CN")
public interface MigrationErrors {
    String PREFIX = "nop.err.orm.migration.";

    String ARG_REASON = "reason";
    String ARG_JOB_ID = "jobId";
    String ARG_PHASE = "phase";
    String ARG_FROM_PHASE = "fromPhase";
    String ARG_TO_PHASE = "toPhase";
    String ARG_GATE = "gate";
    String ARG_ENTITY_NAME = "entityName";
    String ARG_ENTITY_ID = "entityId";
    String ARG_TABLE_NAME = "tableName";
    String ARG_SOURCE_TABLE = "sourceTable";
    String ARG_TARGET_TABLE = "targetTable";
    String ARG_DIALECT = "dialect";
    String ARG_DETAIL = "detail";
    String ARG_SOURCE_COUNT = "sourceCount";
    String ARG_TARGET_COUNT = "targetCount";
    String ARG_MISMATCH_COUNT = "mismatchCount";
    String ARG_MISMATCH_DETAIL = "mismatchDetail";
    String ARG_CONNECTOR_TYPE = "connectorType";

    /**
     * 编排配置解析/校验失败（缺实体、缺目标策略、场景与字段矛盾、坏 JSON、未知写门取值等）。
     */
    ErrorCode ERR_MIG_CONFIG_INVALID = define(PREFIX + "config-invalid",
            "数据搬迁编排配置非法:{reason}", ARG_REASON);

    /**
     * 非法状态机阶段迁移（设计 §4.1：状态迁移只能由编排器按门禁推进）。
     */
    ErrorCode ERR_MIG_PHASE_TRANSITION_INVALID = define(PREFIX + "phase-transition-invalid",
            "数据搬迁作业[{jobId}]不允许从阶段[{fromPhase}]迁移到阶段[{toPhase}]:仅支持按设计状态机推进",
            ARG_JOB_ID, ARG_FROM_PHASE, ARG_TO_PHASE);

    /**
     * 门禁未满足即执行阶段动作（如 VERIFY 未通过即 confirm SWITCH、写门未关即校验）。
     */
    ErrorCode ERR_MIG_GATE_NOT_SATISFIED = define(PREFIX + "gate-not-satisfied",
            "数据搬迁作业[{jobId}]的阶段[{phase}]门禁未满足:{gate}", ARG_JOB_ID, ARG_PHASE, ARG_GATE);

    /**
     * 存量行分片键为 null：不静默跳过也不猜测落片，类型化失败携带实体/主键/源物理表定位（设计 m3）。
     */
    ErrorCode ERR_MIG_NULL_SHARD_KEY = define(PREFIX + "null-shard-key",
            "数据搬迁遇到分片键为null的行,无法确定落片:entity={entityName},pk={entityId},sourceTable={sourceTable}",
            ARG_ENTITY_NAME, ARG_ENTITY_ID, ARG_SOURCE_TABLE);

    /**
     * PREPARE 目标物理表就绪校验失败（设计 §4.3：新 M 物理表由 auto-upgrade 先行建立，管道不建表）。
     */
    ErrorCode ERR_MIG_TARGET_TABLE_NOT_READY = define(PREFIX + "target-table-not-ready",
            "数据搬迁目标物理表不存在或不就绪:{targetTable}", ARG_TARGET_TABLE);

    /**
     * PREPARE 密钥一致门禁失败（设计 M4：不同 key 下密文比对恒真，校验会放行数据错误）。
     */
    ErrorCode ERR_MIG_ENCRYPT_KEY_MISMATCH = define(PREFIX + "encrypt-key-mismatch",
            "数据搬迁源/目标密码器配置不一致:v1要求搬迁前后加密密钥一致:{reason}", ARG_REASON);

    /**
     * P0 位点钉定失败（查询失败或方言不支持）。
     */
    ErrorCode ERR_MIG_P0_PIN_FAILED = define(PREFIX + "p0-pin-failed",
            "数据搬迁源位点P0钉定失败:dialect={dialect},{detail}", ARG_DIALECT, ARG_DETAIL);

    /**
     * 增量搬迁要求 CDC 白名单数据库（mysql/postgres）；白名单外只能停写全量（设计 §三.4）。
     */
    ErrorCode ERR_MIG_INCREMENTAL_NOT_SUPPORTED = define(PREFIX + "incremental-not-supported",
            "数据库方言[{dialect}]不支持增量搬迁(CDC白名单为mysql/postgres),仅支持停写全量形态",
            ARG_DIALECT);

    /**
     * 目标方言无 native upsert（m2 边界：mysql/mariadb/postgresql 之外 v1 无增量+幂等搬迁路径，显式失败）。
     */
    ErrorCode ERR_MIG_UPSERT_DIALECT_NOT_SUPPORTED = define(PREFIX + "upsert-dialect-not-supported",
            "目标方言[{dialect}]不支持native upsert,无法承载keyed搬迁写入(表{tableName}):"
                    + "v1目标端限mysql/mariadb/postgresql(H2仅供测试)", ARG_DIALECT, ARG_TABLE_NAME);

    /**
     * count 校验失配（设计 §4.6 一级）。
     */
    ErrorCode ERR_MIG_COUNT_MISMATCH = define(PREFIX + "count-mismatch",
            "数据搬迁一致性校验失败(行数不一致):table={tableName},sourceCount={sourceCount},targetCount={targetCount}",
            ARG_TABLE_NAME, ARG_SOURCE_COUNT, ARG_TARGET_COUNT);

    /**
     * checksum 校验失配（设计 §4.6 二/三级），失配明细必须可见，不允许"总体一致"掩盖行级差异。
     */
    ErrorCode ERR_MIG_CHECKSUM_MISMATCH = define(PREFIX + "checksum-mismatch",
            "数据搬迁一致性校验失败(行内容不一致):table={tableName},mismatchCount={mismatchCount},detail={mismatchDetail}",
            ARG_TABLE_NAME, ARG_MISMATCH_COUNT, ARG_MISMATCH_DETAIL);

    /**
     * 编排作业状态行不存在（resume/查询未知 jobId）。
     */
    ErrorCode ERR_MIG_JOB_NOT_FOUND = define(PREFIX + "job-not-found",
            "数据搬迁作业不存在:{jobId}", ARG_JOB_ID);

    /**
     * CDC offset 种子化契约违反（种子位点缺失却要求增量阶段启动）。
     */
    ErrorCode ERR_MIG_P0_NOT_PINNED = define(PREFIX + "p0-not-pinned",
            "数据搬迁作业[{jobId}]尚未钉定源位点P0,不能启动增量阶段:{detail}", ARG_JOB_ID, ARG_DETAIL);

    /**
     * FULL 全量复制阶段失败（batch chunk 失败 fail-fast 后的编排层包装）。
     */
    ErrorCode ERR_MIG_FULL_COPY_FAILED = define(PREFIX + "full-copy-failed",
            "数据搬迁全量复制失败:{reason}", ARG_REASON);
}
