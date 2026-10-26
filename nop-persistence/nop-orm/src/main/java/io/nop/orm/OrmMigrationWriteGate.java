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
import io.nop.api.core.time.CoreMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.LongSupplier;

import static io.nop.orm.OrmConfigs.CFG_ORM_MIGRATION_WRITE_GATE;
import static io.nop.orm.OrmConfigs.CFG_ORM_MIGRATION_WRITE_GATE_LEASE_SECONDS;
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
 *
 * <p><b>租约 fail-open（plan 2507 裁定⑤，2.5-C7-004）</b>：{@code setClosed} 的开门翻转仅本 JVM
 * 生效，集群其余实例的 closed 态来自配置源刷新且不可自愈（滚动重启反向加固冻结）。为关门带时间盒：
 * 本 JVM 观察到门进入 closed 态即起租（首见时间戳，惰性锚定于 {@link #isClosed()} 读面，不论经本地
 * setClosed 还是经配置源刷新观察到 closed），经过
 * {@code nop.orm.migration.write-gate-lease-seconds}（默认 1800s，须覆盖迁移 VERIFY+SWITCH 全程）
 * 自动 fail-open（{@code isClosed()} 返回 false）并 WARN 留痕一次。键值 ≤0 = 关闭租约档
 * （现行永久关门语义，单机部署/确定性测试缝）。配置翻回 open 时清零锚点与留痕标志，再次 closed
 * 重新起租。到期 WARN 为运维干预信号；状态行留痕由编排器复核点（runVerify/confirmSwitch）承担——
 * 本类为 nop-orm 静态工具，无 MigrationJobState/store 访问权。</p>
 */
public final class OrmMigrationWriteGate {
    public static final String GATE_OPEN = "open";
    public static final String GATE_CLOSED = "closed";

    static final Logger LOG = LoggerFactory.getLogger(OrmMigrationWriteGate.class);

    /** 租约键单位为秒，锚点时间戳为毫秒。 */
    private static final long MILLIS_PER_SECOND = 1000L;

    /**
     * 租约时钟注入缝（plan 2507 裁定⑤：租约时钟经注入缝可测）：默认
     * {@link CoreMetrics#currentTimeMillis()}；包私有 setter 仅供测试注入确定性时钟。
     */
    private static volatile LongSupplier leaseClock = CoreMetrics::currentTimeMillis;

    /**
     * 本 JVM 首次观察到门进入 closed 态的时间戳（0 = 未锚定）。锚定不区分本地 setClosed
     * 与配置源刷新观察（draft review B1：第二实例的 closed 态只来自配置源刷新，若仅在
     * setClosed 时锚定则第二实例永无租约、冻结不可自愈）。并发首见可能双写相近时刻，
     * 后写覆盖前写——锚点最多偏移一次检查间隔，方向保守（租约只会更长），可接受。
     */
    private static volatile long firstSeenClosedAt = 0L;

    /** 租约到期 WARN 已留痕标志（一次性不刷屏；门翻回 open 时复位）。 */
    private static volatile boolean leaseExpiredLogged = false;

    private OrmMigrationWriteGate() {
    }

    /**
     * 写停止关是否处于关闭态（含租约 fail-open 语义）。
     *
     * <p>closed 态下惰性锚定「首见 closed 时间戳」；租约到期后返回 false（fail-open）
     * 并 WARN 留痕一次；配置翻回 open 清零锚点。键值 ≤0 关闭租约档。</p>
     */
    public static boolean isClosed() {
        if (!GATE_CLOSED.equalsIgnoreCase(CFG_ORM_MIGRATION_WRITE_GATE.get())) {
            // 配置 true→false：清零首见锚点与留痕标志，再次 closed 重新起租
            firstSeenClosedAt = 0L;
            leaseExpiredLogged = false;
            return false;
        }
        long leaseSeconds = CFG_ORM_MIGRATION_WRITE_GATE_LEASE_SECONDS.get();
        if (leaseSeconds <= 0) {
            // ≤0 档：关闭租约 = 现行永久关门语义（单机部署/确定性测试缝）
            return true;
        }
        long now = leaseClock.getAsLong();
        long firstSeen = firstSeenClosedAt;
        if (firstSeen == 0L) {
            // 本 JVM 观察到门进入 closed 态即起租（含配置源刷新观察到的 closed）
            firstSeen = now;
            firstSeenClosedAt = firstSeen;
        }
        if (now - firstSeen >= leaseSeconds * MILLIS_PER_SECOND) {
            if (!leaseExpiredLogged) {
                // 到期开门是显式事件而非静默：WARN 留痕一次，重复观察不刷屏
                leaseExpiredLogged = true;
                LOG.warn("nop.orm.migration.write-gate-lease-expired:lease-seconds={},closedFirstSeenAt={},now={}"
                        + ";write-gate fails open to avoid cluster-wide write freeze,"
                        + " close the gate again or intervene if migration is still in progress",
                        leaseSeconds, firstSeen, now);
            }
            return false;
        }
        return true;
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
     * 翻回 open 时同步清零租约锚点与留痕标志（覆盖 setClosed(false)→setClosed(true)
     * 连续翻转而无中间 isClosed() 观察的窗口——惰性清零只在读面生效，会残留旧锚点）；
     * 配置源传播的开门（第二实例）由 {@link #isClosed()} 读面惰性清零承载。
     */
    public static void setClosed(boolean closed) {
        AppConfig.getConfigProvider().updateConfigValue(CFG_ORM_MIGRATION_WRITE_GATE,
                closed ? GATE_CLOSED : GATE_OPEN);
        if (!closed) {
            firstSeenClosedAt = 0L;
            leaseExpiredLogged = false;
        }
    }

    /**
     * 租约时钟注入缝（包私有，仅供测试）：传 null 恢复默认 {@link CoreMetrics} 时钟。
     */
    static void setLeaseClock(LongSupplier clock) {
        leaseClock = clock == null ? CoreMetrics::currentTimeMillis : clock;
    }
}
