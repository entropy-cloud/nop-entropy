/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.core.lang.sql.SQL;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.5-C7-008（plan 2554 裁定 L）载体：ledger 观测 namespace 过滤——COUNT/MAX SQL 携带
 * {@code sink_namespace LIKE ?} 参数化前缀谓词（参数 {@code jobId|%}），只观测本作业链提交；
 * 无 jobId 上下文（fallback 档）回落无过滤观测。
 *
 * <p>RP-2 零改写面：{@code awaitLagZeroChains} 及其 javadoc 不在本载体触达范围。</p>
 */
public class TestLagProbeNamespaceFilter extends AbstractMigrationE2ETestCase {

    @Test
    public void testLedgerObservationFilteredByJobIdPrefix() {
        CapturingJdbcTemplate fake = new CapturingJdbcTemplate(jdbcTemplate);
        IncrementalLagProbe probe = new IncrementalLagProbe(fake, "",
                "stream_epoch_ledger_v2", "ns-filter-job");
        probe.snapshot(null);

        assertEquals(2, fake.executed().size());
        for (SQL sql : fake.executed()) {
            assertTrue(sql.getText().contains(" WHERE sink_namespace LIKE ?"),
                    "COUNT/MAX ledger observation must carry the namespace prefix predicate, got: "
                            + sql.getText());
            assertEquals(1, sql.getParams().size());
            assertEquals("ns-filter-job|%", sql.getParams().get(0),
                    "namespace filter must be the parameterized jobId prefix (jobId|%)");
        }
    }

    @Test
    public void testNullNamespaceFallsBackToUnfilteredObservation() {
        CapturingJdbcTemplate fake = new CapturingJdbcTemplate(jdbcTemplate);
        IncrementalLagProbe probe = new IncrementalLagProbe(fake, "",
                "stream_epoch_ledger_v2");
        probe.snapshot(null);

        assertEquals(2, fake.executed().size());
        for (SQL sql : fake.executed()) {
            assertFalse(sql.getText().contains("sink_namespace"),
                    "fallback (no jobId context) must keep the unfiltered observation, got: "
                            + sql.getText());
            assertTrue(sql.getParams().isEmpty());
        }
    }

    @Test
    public void testJobScopedObservationExcludesForeignSubmissions() {
        // 行为钉：本作业链提交（jobId|vertexId namespace）在静默窗内前进可观测；外部作业提交
        // （异 namespace）不污染本作业的 lag 判定
        insertLedgerRow("ns-filter-job|v0", 1L, 0);
        insertLedgerRow("other-job|v0", 100L, 0);
        IncrementalLagProbe probe = new IncrementalLagProbe(jdbcTemplate, "",
                "stream_epoch_ledger_v2", "ns-filter-job");
        IncrementalLagProbe.Snapshot before = probe.snapshot(null);
        insertLedgerRow("other-job|v1", 101L, 0);
        IncrementalLagProbe.Snapshot after = probe.snapshot(null);
        assertEquals(before.ledgerRows, after.ledgerRows,
                "foreign-job ledger submissions must be excluded by the namespace filter");
        assertEquals(before.maxEpoch, after.maxEpoch,
                "foreign-job epoch advance must not pollute this job's observation");
    }
}
