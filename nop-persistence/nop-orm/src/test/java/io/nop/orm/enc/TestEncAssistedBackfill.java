/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.enc;

import io.nop.api.core.config.AppConfig;
import io.nop.commons.crypto.impl.AESTextCipher;
import io.nop.core.lang.sql.SQL;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.OrmConfigs;
import io.nop.app.SimsExam;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2318 Phase 5：辅助列存量数据回填（{@link EncAssistedBackfiller}）。
 *
 * <p>存量态模拟：实体经 ORM 保存（主列落 @enc: 密文），随后裸 SQL 清空辅助列/改写主列存储值，
 * 构造"辅助列未回填"的存量行。覆盖：@enc: 前缀 v1 密文、@enc: 前缀 legacy 格式密文
 * （versionedFormat=false）、未带前缀的存量明文（与 EncodedDataParameterBinder 读语义一致）、
 * 主列为 null 跳过、幂等（重复执行结果一致）、分批大小配置（nop.orm.enc-backfill-batch-size）。</p>
 */
public class TestEncAssistedBackfill extends AbstractOrmTestCase {

    private void saveExam(String examId, String examName) {
        SimsExam entity = new SimsExam();
        entity.setExamId(examId);
        entity.setExamName(examName);
        daoProvider().daoFor(SimsExam.class).saveEntity(entity);
    }

    /** 裸 SQL 清空辅助列，模拟存量未回填状态 */
    private void clearHelpers(String examId) {
        jdbc().executeUpdate(SQL.begin()
                .sql("update sims_exam set EXAM_NAME_ASSISTED=null, EXAM_NAME_LIKE=null where EXAM_ID=")
                .param(examId).end());
    }

    /** 裸 SQL 改写主列存储值（@enc: 密文或存量明文） */
    private void rawUpdateExamName(String examId, String stored) {
        jdbc().executeUpdate(SQL.begin()
                .sql("update sims_exam set EXAM_NAME=")
                .param(stored)
                .sql(" where EXAM_ID=")
                .param(examId).end());
    }

    private Map<String, Object> rawRow(String examId) {
        return jdbc().findFirst(new SQL("select * from sims_exam where exam_id='" + examId + "'"));
    }

    @Test
    public void testBackfillLegacyCiphertextThenQueryHits() {
        saveExam("501", "LegacyOne");
        saveExam("502", "LegacyTwo");
        // 存量态：主列为 @enc: 密文、辅助列为空（这里再裸写一次密文以钉住"直接裸 SQL 插入存量密文"语义）
        clearHelpers("501");
        clearHelpers("502");
        rawUpdateExamName("501", "@enc:" + new AESTextCipher().encrypt("LegacyOne"));

        long updated = new EncAssistedBackfiller(sessionFactory).backfillAll();
        assertEquals(2, updated);

        EncAssistedHelper helper = EncAssistedHelper.instance();
        Map<String, Object> row1 = rawRow("501");
        assertEquals(helper.assistedDigest("LegacyOne"), row1.get("EXAM_NAME_ASSISTED"));
        assertEquals(helper.likeDigest("LegacyOne"), row1.get("EXAM_NAME_LIKE"));
        Map<String, Object> row2 = rawRow("502");
        assertEquals(helper.assistedDigest("LegacyTwo"), row2.get("EXAM_NAME_ASSISTED"));
        assertEquals(helper.likeDigest("LegacyTwo"), row2.get("EXAM_NAME_LIKE"));

        // 端到端：回填后 EQL 等值/LIKE 查询命中，实体加载解密回归
        List<SimsExam> eqRows = orm().findAll(SQL.begin()
                .sql("select o from io.nop.app.SimsExam o where o.examName = ?", "LegacyOne").end());
        assertEquals(1, eqRows.size());
        assertEquals("501", eqRows.get(0).getExamId());
        assertEquals("LegacyOne", eqRows.get(0).getExamName(), "entity load must decrypt master column");

        List<SimsExam> likeRows = orm().findAll(SQL.begin()
                .sql("select o from io.nop.app.SimsExam o where o.examName like ?", "%one%").end());
        assertEquals(1, likeRows.size(), "like via like column must hit backfilled row");
        assertEquals("501", likeRows.get(0).getExamId());
    }

    @Test
    public void testBackfillLegacyFormatAndPlaintextStoredValues() {
        saveExam("511", "FmtRow");
        clearHelpers("511");
        // legacy 格式密文（无 v1: 前缀的 base64）：cipher.decrypt 的格式识别须同时覆盖两种形态
        AESTextCipher legacyCipher = new AESTextCipher();
        legacyCipher.setVersionedFormat(false);
        rawUpdateExamName("511", "@enc:" + legacyCipher.encrypt("FmtRow"));

        saveExam("512", "PlainRow");
        clearHelpers("512");
        // 未带 @enc: 前缀的存量值按明文处理（与 EncodedDataParameterBinder 读语义一致）
        rawUpdateExamName("512", "PlainRow");

        assertEquals(2, new EncAssistedBackfiller(sessionFactory).backfillAll());

        EncAssistedHelper helper = EncAssistedHelper.instance();
        assertEquals(helper.assistedDigest("FmtRow"), rawRow("511").get("EXAM_NAME_ASSISTED"));
        assertEquals(helper.assistedDigest("PlainRow"), rawRow("512").get("EXAM_NAME_ASSISTED"));

        List<SimsExam> rows = orm().findAll(SQL.begin()
                .sql("select o from io.nop.app.SimsExam o where o.examName in (?,?)", "FmtRow", "PlainRow").end());
        assertEquals(2, rows.size(), "backfilled rows must hit eq query");
    }

    @Test
    public void testBackfillSkipsNullMaster() {
        // 主列为 null 的存量行：辅助列保持原值不动
        saveExam("521", null);
        jdbc().executeUpdate(SQL.begin()
                .sql("update sims_exam set EXAM_NAME_ASSISTED='garbage' where EXAM_ID=").param("521").end());

        // 同批混合一个正常行
        saveExam("522", "NotNullRow");
        clearHelpers("522");

        long updated = new EncAssistedBackfiller(sessionFactory).backfillAll();
        assertEquals(1, updated, "only rows with non-null master are updated");
        assertEquals("garbage", rawRow("521").get("EXAM_NAME_ASSISTED"), "null-master row must be skipped");
        assertEquals(EncAssistedHelper.instance().assistedDigest("NotNullRow"), rawRow("522").get("EXAM_NAME_ASSISTED"));
    }

    @Test
    public void testBackfillIdempotent() {
        saveExam("531", "IdemRow");
        clearHelpers("531");

        EncAssistedBackfiller backfiller = new EncAssistedBackfiller(sessionFactory);
        assertEquals(1, backfiller.backfillAll());
        Object first = rawRow("531").get("EXAM_NAME_ASSISTED");
        Object firstLike = rawRow("531").get("EXAM_NAME_LIKE");

        // 重复执行：重算覆盖，结果一致
        assertEquals(1, backfiller.backfillAll());
        assertEquals(first, rawRow("531").get("EXAM_NAME_ASSISTED"));
        assertEquals(firstLike, rawRow("531").get("EXAM_NAME_LIKE"));
        assertEquals(EncAssistedHelper.instance().assistedDigest("IdemRow"), first);
    }

    @Test
    public void testBackfillBatchSizeConfig() {
        for (int i = 1; i <= 5; i++) {
            saveExam("54" + i, "BatchRow" + i);
            clearHelpers("54" + i);
        }

        AppConfig.getConfigProvider().updateConfigValue(OrmConfigs.CFG_ORM_ENC_BACKFILL_BATCH_SIZE, 2);
        try {
            // 5 行、批大小 2：覆盖 2+2+1 的分页循环
            assertEquals(5, new EncAssistedBackfiller(sessionFactory).backfillAll());
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(OrmConfigs.CFG_ORM_ENC_BACKFILL_BATCH_SIZE, 500);
        }

        EncAssistedHelper helper = EncAssistedHelper.instance();
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> row = rawRow("54" + i);
            assertEquals(helper.assistedDigest("BatchRow" + i), row.get("EXAM_NAME_ASSISTED"),
                    "row 54" + i + " must be backfilled");
        }
    }

    @Test
    public void testBackfillEntityScopedByName() {
        saveExam("551", "ScopedRow");
        clearHelpers("551");
        saveExam("552", "NormalRow");

        // 未声明辅助列的实体返回 0（无绑定即无操作）
        assertEquals(0, new EncAssistedBackfiller(sessionFactory).backfillEntity("io.nop.app.SimsClass"));
        // 按名回填指定实体：幂等重算，551 补齐、552 保持契约摘要
        assertEquals(2, new EncAssistedBackfiller(sessionFactory).backfillEntity("io.nop.app.SimsExam"));

        EncAssistedHelper helper = EncAssistedHelper.instance();
        assertEquals(helper.assistedDigest("ScopedRow"), rawRow("551").get("EXAM_NAME_ASSISTED"));
        assertEquals(helper.assistedDigest("NormalRow"), rawRow("552").get("EXAM_NAME_ASSISTED"));
    }
}
