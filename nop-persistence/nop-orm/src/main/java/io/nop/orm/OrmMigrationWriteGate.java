/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigProvider;
import io.nop.api.core.exceptions.NopException;

import static io.nop.orm.OrmConfigs.CFG_ORM_MIGRATION_WRITE_GATE;
import static io.nop.orm.OrmErrors.ARG_ENTITY_NAME;
import static io.nop.orm.OrmErrors.ARG_OPERATION;
import static io.nop.orm.OrmErrors.ERR_ORM_MIGRATION_WRITE_GATE_CLOSED;

/**
 * WI9 数据搬迁写停止关（设计 §4.3 M3）。
 *
 * <p>平台写停止原语的执行面：配置平面键 {@code nop.orm.migration.write-gate}（{@link OrmConfigs}）
 * 取值 {@code closed} 时，ORM DML 提交入口显式 fail-fast（{@code ERR_ORM_MIGRATION_WRITE_GATE_CLOSED}，
 * 携带实体与操作），读路径不受影响。与 2320 的 fail-fast 哲学同构：门禁不满足时显式失败，绝不静默放行。</p>
 *
 * <p>检查点落位（{@code OrmSessionImpl}）：{@code flush()}（dirty 会话的级联 DML 提交）、
 * {@code flushSave/flushUpdate/flushDelete}（实体级 DML 入队/执行）、{@code flushImmediately}
 * （无状态会话立即持久化）。运行期由搬迁编排器经 {@link IConfigProvider#updateConfigValue}
 * 动态翻转；未配置时取值 open，零行为变更。</p>
 */
public final class OrmMigrationWriteGate {
    public static final String GATE_OPEN = "open";
    public static final String GATE_CLOSED = "closed";

    private OrmMigrationWriteGate() {
    }

    /**
     * 写停止关是否处于关闭态。
     */
    public static boolean isClosed() {
        return GATE_CLOSED.equalsIgnoreCase(CFG_ORM_MIGRATION_WRITE_GATE.get());
    }

    /**
     * DML 提交前的门禁检查：写门关闭时抛 {@code ERR_ORM_MIGRATION_WRITE_GATE_CLOSED}。
     *
     * @param entityName 发生 DML 的实体名（null 时以 "-" 占位，如批量 flush 尚无实体上下文）
     * @param operation  操作标识（save/update/delete/flush）
     */
    public static void checkWriteAllowed(String entityName, String operation) {
        if (isClosed()) {
            throw new NopException(ERR_ORM_MIGRATION_WRITE_GATE_CLOSED)
                    .param(ARG_ENTITY_NAME, entityName == null ? "-" : entityName)
                    .param(ARG_OPERATION, operation == null ? "-" : operation);
        }
    }

    /**
     * 编排器/运维翻转写停止关（单个配置点，原子生效于本 JVM 所有 DML 提交入口）。
     */
    public static void setClosed(boolean closed) {
        AppConfig.getConfigProvider().updateConfigValue(CFG_ORM_MIGRATION_WRITE_GATE,
                closed ? GATE_CLOSED : GATE_OPEN);
    }
}
