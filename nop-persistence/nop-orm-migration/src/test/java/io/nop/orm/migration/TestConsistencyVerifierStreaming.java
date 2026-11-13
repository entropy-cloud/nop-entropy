/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.sql.SQL;
import io.nop.orm.migration.TargetShardMapping.PhysicalTable;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static io.nop.orm.migration.MigrationErrors.ARG_MISMATCH_COUNT;
import static io.nop.orm.migration.MigrationErrors.ARG_MISMATCH_DETAIL;
import static io.nop.orm.migration.MigrationErrors.ERR_MIG_CHECKSUM_MISMATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.5-MA-07（plan 2554 裁定 A）载体：ConsistencyVerifier 校验的规模包络收敛——
 * 双侧 keyset 分页（{@code WHERE pk > lastKey ORDER BY pk LIMIT N}）拉取 + 归并双游标
 * 增量检出（missing/added/mismatch 跨页边界），替换全量 pk→hash Map + TreeSet 驻堆形态。
 *
 * <p>行为钉：fake jdbc 跨 ≥3 页数据下跨页 added/missing/mismatch 全检出；结构钉：扫描 SQL
 * 含 keyset 谓词（{@code > ?}）与 LIMIT 批参数；源级钉：全量物化扫描方法（scanHashes）移除。</p>
 */
public class TestConsistencyVerifierStreaming extends AbstractMigrationE2ETestCase {

    private static final int BATCH = 3;

    /** 可编程页应答：按 SQL 名（orm-migration-scan:&lt;table&gt;）分派，从 keyset 参数推导页窗。 */
    private final Map<String, TreeMap<Long, Map<String, Object>>> tables = new LinkedHashMap<>();
    private CapturingJdbcTemplate fake;

    private CapturingJdbcTemplate fakeTemplate() {
        fake = new CapturingJdbcTemplate(jdbcTemplate);
        fake.setResponder(this::answerPage);
        return fake;
    }

    private void putRow(String table, long sid, String amount) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("SID", sid);
        row.put("AMOUNT", amount);
        row.put("SHARD_KEY", String.valueOf(sid % 2));
        tables.computeIfAbsent(table, t -> new TreeMap<>()).put(sid, row);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> answerPage(SQL sql) {
        // SQL 名形态：orm-migration-scan:<table>（ConsistencyVerifier 单点装配）
        String name = sql.getName();
        String table = name.substring(name.indexOf(':') + 1);
        TreeMap<Long, Map<String, Object>> rows = tables.get(table);
        if (rows == null)
            throw new IllegalStateException("no fixture rows for scan target: " + table);
        List<Object> params = sql.getParams();
        // keyset 谓词参数在前，LIMIT 批参数在末位（LimitOffsetPaginationHandler 追加 LIMIT ?）
        Long lastKey = params.size() >= 2 ? ((Number) params.get(params.size() - 2)).longValue() : null;
        int limit = ((Number) params.get(params.size() - 1)).intValue();
        Map<Long, Map<String, Object>> window = lastKey == null ? rows : rows.tailMap(lastKey, false);
        List<Map<String, Object>> page = new ArrayList<>();
        for (Map<String, Object> row : window.values()) {
            if (page.size() >= limit)
                break;
            page.add(row);
        }
        return (List<Map<String, Object>>) (List<?>) page;
    }

    private int scanPageCount() {
        int n = 0;
        for (SQL sql : fake.executed()) {
            if (sql.getName() != null && sql.getName().startsWith("orm-migration-scan:"))
                n++;
        }
        return n;
    }

    private MigrationJobConfig fullChecksumConfig() {
        return MigrationJobConfig.newBuilder("rescale", "mig.entity.MigOrder")
                .sourceQuerySpaces(List.of(""))
                .targetStrategy(AbstractMigrationE2ETestCase.Json.map(
                        "strategies", AbstractMigrationE2ETestCase.Json.map("order-mod",
                                AbstractMigrationE2ETestCase.Json.map("type", "mod", "shard-count", 2,
                                        "entities", List.of("mig.entity.MigOrder")))))
                .verify(false, 0, true)
                .build();
    }

    @Test
    public void testMergeStreamingDetectsDiffsAcrossPageBoundaries() {
        // 源侧 1..9 单物理表（3 页 @batch=3）；目标侧拆两张物理表（k 路归并 + 跨页边界），
        // 刻意覆盖：missing（4）、added（10）、mismatch（7）三类全部落在页边界附近
        for (long id = 1; id <= 9; id++)
            putRow("MIG_ORDER", id, String.valueOf(id) + ".00");
        // 目标 = {1,2,3,5,6,7,8,9,10}：缺 4（missing）、多 10（added）、7 内容改写（mismatch）
        putRow("MIG_ORDER_0", 2L, "2.00");
        putRow("MIG_ORDER_0", 6L, "6.00");
        putRow("MIG_ORDER_0", 8L, "8.00");
        putRow("MIG_ORDER_1", 1L, "1.00");
        putRow("MIG_ORDER_1", 3L, "3.00");
        putRow("MIG_ORDER_1", 5L, "5.00");
        putRow("MIG_ORDER_1", 7L, "999.99");
        putRow("MIG_ORDER_1", 9L, "9.00");
        putRow("MIG_ORDER_1", 10L, "10.00");

        ConsistencyVerifier verifier = new ConsistencyVerifier(fakeTemplate(),
                TargetShardMapping.of(entityFacade, rescaleConfig()), List.of("SID"));
        verifier.setChecksumBatch(BATCH);

        NopException e = assertThrows(NopException.class,
                () -> verifier.verify(fullChecksumConfig()));
        assertEquals(ERR_MIG_CHECKSUM_MISMATCH.getErrorCode(), e.getErrorCode());
        assertEquals(3L, ((Number) e.getParams().get(ARG_MISMATCH_COUNT)).longValue(),
                "missing(4) + added(10) + mismatch(7) must all be detected incrementally");
        String detail = String.valueOf(e.getParams().get(ARG_MISMATCH_DETAIL));
        assertTrue(detail.contains("pk=n:4") && detail.contains("target=MISSING"),
                "target-missing row must be reported with the existing contract: " + detail);
        assertTrue(detail.contains("pk=n:10") && detail.contains("source=MISSING"),
                "target-added row must be reported with the existing contract: " + detail);
        assertTrue(detail.contains("pk=n:7") && detail.contains("source=") && detail.contains("target="),
                "hash mismatch must carry both-side hashes: " + detail);

        // 行为钉：双侧均跨 ≥3 页拉取（9 行 @batch=3 = 3 页；双侧 + k 路表 ≥ 5 页）
        assertTrue(scanPageCount() >= 6,
                "verification must page through keyset windows, scanned pages=" + scanPageCount());
    }

    @Test
    public void testKeysetSqlShapeAndNoFullMaterialization() throws Exception {
        for (long id = 1; id <= 6; id++)
            putRow("MIG_ORDER", id, String.valueOf(id) + ".00");
        for (long id = 1; id <= 6; id++)
            putRow(id % 2 == 0 ? "MIG_ORDER_0" : "MIG_ORDER_1", id, String.valueOf(id) + ".00");

        ConsistencyVerifier verifier = new ConsistencyVerifier(fakeTemplate(),
                TargetShardMapping.of(entityFacade, rescaleConfig()), List.of("SID"));
        verifier.setChecksumBatch(BATCH);
        ConsistencyVerifier.VerifyReport report = verifier.verify(fullChecksumConfig());
        assertTrue(report.isPassed());

        // 结构钉：扫描 SQL 含 keyset 谓词（pk > ?）与 LIMIT 批参数；首页无谓词、后续页带谓词
        int keysetPages = 0;
        boolean limitOnEveryPage = true;
        for (SQL sql : fake.executed()) {
            if (sql.getName() == null || !sql.getName().startsWith("orm-migration-scan:"))
                continue;
            String text = sql.getText();
            if (text.contains("> ?"))
                keysetPages++;
            if (!text.contains("LIMIT ?"))
                limitOnEveryPage = false;
        }
        assertTrue(keysetPages >= 4,
                "second-and-later pages must use the keyset predicate pk > ?, count=" + keysetPages);
        assertTrue(limitOnEveryPage, "every keyset page must carry the LIMIT batch parameter");

        // 源级钉：全量物化扫描面（双侧 pk→hash Map + scanHashes）已移除
        String source = Files.readString(Path.of(
                "src/main/java/io/nop/orm/migration/ConsistencyVerifier.java"));
        assertFalse(source.contains("scanHashes"),
                "full-materialization scan method must be gone (2.5-MA-07)");
        assertFalse(source.contains("new TreeSet"),
                "full key-set materialization must be gone (2.5-MA-07)");
        assertTrue(source.contains("keyset"), "merge-streaming keyset marker must be present");
    }

    @Test
    public void testTaskKeyCarriesQuerySpaceIdentity() {
        // 2.5-C3-011（plan 2554 裁定 I）相邻钉：taskKey 键派生含 querySpace 维度
        PhysicalTable a = new PhysicalTable("qs-a", "MIG_ORDER");
        PhysicalTable b = new PhysicalTable("qs-b", "MIG_ORDER");
        assertFalse(FullCopyBatchJob.taskKey("job1", a)
                .equals(FullCopyBatchJob.taskKey("job1", b)),
                "same-name tables in different query spaces must not collide on taskKey");
        assertEquals("job1:qs-a:MIG_ORDER", FullCopyBatchJob.taskKey("job1", a));
    }
}
