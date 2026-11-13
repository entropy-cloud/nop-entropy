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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.5-C3-010（plan 2554 裁定 H）载体：lag 观测 querySpace 对齐——probe 的 ledger 观测 SQL
 * 必须落在 target querySpace（与 sink 提交面 {@code target.getQuerySpace()} 同源），不得落在
 * stateQuerySpace（其仅保留状态行 DDL 面）。
 */
public class TestLagProbeQuerySpaceAlignment extends AbstractMigrationE2ETestCase {

    private String uniqueTargetQuerySpace(TargetShardMapping mapping) {
        // 与 OrmMigrationOrchestrator.targetLedgerQuerySpace 同构的唯一 qs 派生
        Set<String> spaces = new java.util.LinkedHashSet<>();
        for (TargetShardMapping.PhysicalTable target : mapping.getTargetTables()) {
            spaces.add(target.getQuerySpace());
        }
        return spaces.size() == 1 ? spaces.iterator().next() : "";
    }

    private MigrationJobConfig relocateConfig() {
        Map<String, String> qsMapping = new LinkedHashMap<>();
        qsMapping.put("qs-src", "qs-target");
        return MigrationJobConfig.newBuilder("relocate", "mig.entity.MigOrder")
                .sourceQuerySpaces(List.of("qs-src"))
                .targetQuerySpaceMapping(qsMapping)
                .verify(false, 0, false)
                .quiesceSeconds(1)
                .build();
    }

    @Test
    public void testLedgerObservationRunsOnTargetQuerySpace() {
        // relocate 形态（裁定 H 的 loud 错位场景）：sink 提交 qs = qs-target（target 表映射派生）
        TargetShardMapping mapping = TargetShardMapping.of(entityFacade, relocateConfig());
        String sinkQuerySpace = mapping.getTargetTables().get(0).getQuerySpace();
        assertEquals("qs-target", sinkQuerySpace);

        CapturingJdbcTemplate fake = new CapturingJdbcTemplate(jdbcTemplate);
        IncrementalLagProbe probe = new IncrementalLagProbe(fake, uniqueTargetQuerySpace(mapping),
                "stream_epoch_ledger_v2", "qs-align-job");
        probe.snapshot(null);

        assertEquals(2, fake.executed().size(), "COUNT + MAX two observations per snapshot");
        for (SQL sql : fake.executed()) {
            assertEquals(sinkQuerySpace, sql.getQuerySpace(),
                    "ledger observation must run on the target querySpace (same as sink submission)");
            assertNotEquals("qs-src", sql.getQuerySpace(),
                    "observation must not run on the source/state querySpace");
            assertTrue(sql.getText().contains("stream_epoch_ledger_v2"));
        }
    }
}
