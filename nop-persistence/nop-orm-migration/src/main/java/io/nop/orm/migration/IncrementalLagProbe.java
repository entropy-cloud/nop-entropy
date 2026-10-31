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

    private final IJdbcTemplate targetJdbcTemplate;
    private final String ledgerQuerySpace;
    private final String ledgerTableName;

    public IncrementalLagProbe(IJdbcTemplate targetJdbcTemplate, String ledgerQuerySpace,
                               String ledgerTableName) {
        this.targetJdbcTemplate = targetJdbcTemplate;
        this.ledgerQuerySpace = ledgerQuerySpace == null ? "" : ledgerQuerySpace;
        this.ledgerTableName = ledgerTableName;
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

    private void sleep(long quiesceSeconds) {
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

    private long countLedger() {
        String sql = "SELECT COUNT(*) FROM " + ledgerTableName;
        Long count = targetJdbcTemplate.findLong(SQL.begin().querySpace(ledgerQuerySpace)
                .name("orm-migration-ledger-count").sql(sql).end(), 0L);
        return count == null ? 0L : count;
    }

    private Long maxEpoch() {
        String sql = "SELECT MAX(epoch_id) FROM " + ledgerTableName;
        return targetJdbcTemplate.findLong(SQL.begin().querySpace(ledgerQuerySpace)
                .name("orm-migration-ledger-max-epoch").sql(sql).end(), 0L);
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
