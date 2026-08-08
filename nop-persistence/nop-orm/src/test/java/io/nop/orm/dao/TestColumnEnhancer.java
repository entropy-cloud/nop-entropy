/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.dao;

import io.nop.app.SimsExam;
import io.nop.commons.CommonConstants;
import io.nop.commons.crypto.impl.AESTextCipher;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.AbstractOrmTestCase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestColumnEnhancer extends AbstractOrmTestCase {
    @Test
    public void testEncryptedColumn() {
        IEntityDao<SimsExam> dao = daoProvider().daoFor(SimsExam.class);
        SimsExam entity = new SimsExam();
        entity.setExamId("101");
        entity.setExamName("testExam");
        dao.saveEntity(entity);

        entity = dao.getEntityById("101");
        assertEquals("testExam", entity.getExamName());

        // 注意：引入 per-message IV（语义安全）后，等值明文每次加密为不同密文，
        // 因此无法再通过对明文重新加密来做密文列等值检索。这是安全加固的预期副作用，
        // 不应回退。findAllByExample(example.examName) 在 v1 下返回 0 属正确行为。

        Map<String, Object> row = jdbc().findFirst(new SQL("select * from sims_exam where exam_id='101'"));
        System.out.println(row);
        String examName = (String) row.get("EXAM_NAME");
        assertTrue(examName.startsWith(CommonConstants.ENC_VALUE_PREFIX));
        // 接线验证 + 版本标记：ORM binder 写入的密文必须使用 v1 自描述格式（per-message IV）
        String cipherText = examName.substring(CommonConstants.ENC_VALUE_PREFIX.length());
        assertTrue(cipherText.startsWith(AESTextCipher.V1_MARKER),
                "ORM @enc column must persist v1 versioned ciphertext, but got: " + cipherText);

        // 两次保存同一明文，密文必须不同（per-message IV 的端到端证据）
        SimsExam entity2 = new SimsExam();
        entity2.setExamId("102");
        entity2.setExamName("testExam");
        dao.saveEntity(entity2);
        Map<String, Object> row2 = jdbc().findFirst(new SQL("select * from sims_exam where exam_id='102'"));
        String examName2 = (String) row2.get("EXAM_NAME");
        assertTrue(examName2.substring(CommonConstants.ENC_VALUE_PREFIX.length()).startsWith(AESTextCipher.V1_MARKER));
        assertNotEquals(examName, examName2, "per-message IV must yield distinct stored ciphertexts");
    }

    /**
     * Legacy-ORM-column 读取兼容：以 legacy 格式（静态 IV + MD5 KDF，无版本标记）写入的密文，
     * 在引入 v1 格式后仍能通过 ORM binder 正确读取（read-only 兼容，无强制再加密）。
     */
    @Test
    public void testLegacyEncryptedColumnReadable() {
        // 用 legacy 模式、与 binder 相同的默认 key 产生一条 legacy 密文
        AESTextCipher legacyCipher = new AESTextCipher().versionedFormat(false);
        String legacyCipherText = legacyCipher.encrypt("legacyExam");
        assertTrue(!legacyCipherText.startsWith(AESTextCipher.V1_MARKER),
                "legacy ciphertext must not carry v1 marker");

        // 直接以 legacy 格式密文写入数据库（模拟历史存量数据）
        String stored = CommonConstants.ENC_VALUE_PREFIX + legacyCipherText;
        jdbc().executeUpdate(new SQL("delete from sims_exam where exam_id='legacy-1'"));
        jdbc().executeUpdate(new SQL(
                "insert into sims_exam(exam_id, exam_name, tenant_id) values('legacy-1', '" + stored
                        + "', '123')"));

        // 通过 ORM binder 读取，必须返回原始明文
        SimsExam entity = daoProvider().daoFor(SimsExam.class).getEntityById("legacy-1");
        assertEquals("legacyExam", entity.getExamName(),
                "legacy-format @enc column must remain readable after v1 hardening");
    }
}
