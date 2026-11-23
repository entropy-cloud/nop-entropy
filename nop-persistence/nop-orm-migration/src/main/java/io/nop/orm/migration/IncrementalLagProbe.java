/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.api.core.time.CoreMetrics;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.message.debezium.engine.NopStreamOffsetBackingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * lag=0 判定（设计 §4.3 m6）：观测窗口 = {@code incremental.quiesce-seconds} 静默期，两个观测量
 * 同时满足才算追平：
 * <ol>
 * <li>CDC offset 在静默期内无前进（复用 cdc-design §3.7 的 offset lag 指标族——对
 *     {@link NopStreamOffsetBackingStore} 的共享 registry 快照做前后比对）；</li>
 * <li>目标端 epoch ledger（stream_epoch_ledger_v2）在静默期内无新增提交行（行数 + 最大 epoch
 *     前后比对）。</li>
 * </ol>
 *
 * <p>写停止关（write-gate=closed）是本判定的前置契约——写不停则位点静止无意义（设计 §4.3）。
 * 白名单外数据库的停写全量形态无 CDC 面，跳过 offset 观测（无增量即无 lag）。</p>
 */
public class IncrementalLagProbe {
    static final Logger LOG = LoggerFactory.getLogger(IncrementalLagProbe.class);

    private final IJdbcTemplate targetJdbcTemplate;
    private final String ledgerQuerySpace;
    private final String ledgerTableName;
    /**
     * 2.5-C7-008（plan 2554 裁定 L）：观测 namespace 过滤前缀（{@code jobId|%}）——链作业的
     * sink_namespace 恒带 jobId 前缀（{@code JdbcTwoPhaseCommitSink} 写 {@code jobId|vertexId}），
     * 前缀匹配排除其他作业/非链提交的跨作业污染。null = 无 jobId 上下文（回落无过滤观测 + WARN）。
     */
    private final String namespacePrefix;
    private boolean namespaceFallbackWarned;

    public IncrementalLagProbe(IJdbcTemplate targetJdbcTemplate, String ledgerQuerySpace,
                               String ledgerTableName) {
        this(targetJdbcTemplate, ledgerQuerySpace, ledgerTableName, null);
    }

    public IncrementalLagProbe(IJdbcTemplate targetJdbcTemplate, String ledgerQuerySpace,
                               String ledgerTableName, String jobId) {
        this.targetJdbcTemplate = targetJdbcTemplate;
        this.ledgerQuerySpace = ledgerQuerySpace == null ? "" : ledgerQuerySpace;
        this.ledgerTableName = ledgerTableName;
        this.namespacePrefix = jobId == null || jobId.isEmpty() ? null : jobId + "|%";
    }

    /**
     * 静默期观测：窗口前后两快照一致 → lag=0。
     *
     * @param connectorName  CDC connector 名（null = 无增量阶段，仅观测 ledger）
     * @param quiesceSeconds 静默期秒数（&gt; 0）
     */
    public boolean awaitLagZero(String connectorName, long quiesceSeconds) {
        Snapshot before = snapshot(connectorName);
        sleep(quiesceSeconds);
        Snapshot after = snapshot(connectorName);
        return before.equals(after);
    }

    /**
     * 逐链聚合静默期观测（plan 2518 裁定⑨新增，加法式入口——既有
     * {@link #awaitLagZero(String, long)} 签名与语义保留为委托，RP-2 双钉零改写）：
     * M&gt;1 全链 offsets 指纹 + ledger 计数在<b>单静默窗</b>内聚合判定——任一链指纹前进或
     * ledger 前进即 lag≠0。
     *
     * <p>单静默窗 residual（m1）：各链「窗前快照」时刻随遍历序有微小错位（非严格同刻），
     * 窗内跨越快照时刻的链事件存在理论漏检窗；错位量级 = 逐链快照取值毫秒级 vs 静默窗秒级，
     * 占比可忽略，设计文档 §3.9 显式登记。</p>
     *
     * @param connectorNames 各链实际注册的 connector 名清单（裁定⑨派生函数产出，逐链消费）
     * @param quiesceSeconds 静默期秒数（&gt; 0）
     */
    public boolean awaitLagZeroChains(List<String> connectorNames, long quiesceSeconds) {
        List<Snapshot> before = new ArrayList<>(connectorNames.size());
        for (String connectorName : connectorNames) {
            before.add(snapshot(connectorName));
        }
        sleep(quiesceSeconds);
        for (int i = 0; i < connectorNames.size(); i++) {
            if (!before.get(i).equals(snapshot(connectorNames.get(i))))
                return false;
        }
        return true;
    }

    /**
     * Quiesce-window wait. Protected as a test seam: tests drive the window
     * deterministically (advance state between the two snapshots) instead of
     * racing a frozen clock read count against JDBC's own CoreMetrics reads.
     */
    protected void sleep(long quiesceSeconds) {
        long deadline = CoreMetrics.currentTimeMillis() + quiesceSeconds * 1000L;
        while (CoreMetrics.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(Math.min(200L, deadline - CoreMetrics.currentTimeMillis()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("lag probe interrupted");
            }
        }
    }

    Snapshot snapshot(String connectorName) {
        String offsets = connectorName == null ? "no-incremental" : offsetsFingerprint(connectorName);
        long ledgerRows = countLedger();
        Long maxEpoch = maxEpoch();
        return new Snapshot(offsets, ledgerRows, maxEpoch);
    }

    /**
     * offset map 指纹：序列化键值对（TreeMap 稳定排序）的聚合串（无前进 = 指纹不变）。
     */
    private String offsetsFingerprint(String connectorName) {
        Map<ByteBuffer, ByteBuffer> offsets =
                NopStreamOffsetBackingStore.forConnector(connectorName).getOffsets();
        Map<String, String> serialized = NopStreamOffsetBackingStore.toSerializable(offsets);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : serialized.entrySet()) {
            sb.append(entry.getKey()).append('=').append(entry.getValue()).append(';');
        }
        return sb.toString();
    }

    /**
     * 2.5-C7-008（plan 2554 裁定 L）：ledger 观测 SQL 单点装配——COUNT/MAX 只统计本作业链
     * 提交（{@code sink_namespace LIKE 'jobId|%'} 参数化前缀匹配）；无 jobId 上下文（部署侧
     * fallback 表名 namespace 模式）回落无过滤观测并 WARN 留痕（不可过滤档显式声明）。
     */
    private SQL ledgerObservationSql(String sqlName, String aggregate) {
        SQL.SqlBuilder sb = SQL.begin().querySpace(ledgerQuerySpace).name(sqlName)
                .sql(aggregate + " FROM " + ledgerTableName);
        if (namespacePrefix != null) {
            sb.sql(" WHERE sink_namespace LIKE ").param(namespacePrefix);
        } else if (!namespaceFallbackWarned) {
            namespaceFallbackWarned = true;
            LOG.warn("nop.warn.orm.lag-probe-namespace-fallback:table={},filter=none",
                    ledgerTableName);
        }
        return sb.end();
    }

    private long countLedger() {
        Long count = targetJdbcTemplate.findLong(
                ledgerObservationSql("orm-migration-ledger-count", "SELECT COUNT(*)"), 0L);
        return count == null ? 0L : count;
    }

    private Long maxEpoch() {
        return targetJdbcTemplate.findLong(
                ledgerObservationSql("orm-migration-ledger-max-epoch", "SELECT MAX(epoch_id)"), 0L);
    }

    /**
     * 观测快照（equals 即"静默期无任何前进"）。
     */
    static final class Snapshot {
        final String offsetsFingerprint;
        final long ledgerRows;
        final Long maxEpoch;

        Snapshot(String offsetsFingerprint, long ledgerRows, Long maxEpoch) {
            this.offsetsFingerprint = offsetsFingerprint;
            this.ledgerRows = ledgerRows;
            this.maxEpoch = maxEpoch;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof Snapshot))
                return false;
            Snapshot snapshot = (Snapshot) o;
            return ledgerRows == snapshot.ledgerRows
                    && Objects.equals(maxEpoch, snapshot.maxEpoch)
                    && Objects.equals(offsetsFingerprint, snapshot.offsetsFingerprint);
        }

        @Override
        public int hashCode() {
            return Objects.hash(offsetsFingerprint, ledgerRows, maxEpoch);
        }
    }
}
