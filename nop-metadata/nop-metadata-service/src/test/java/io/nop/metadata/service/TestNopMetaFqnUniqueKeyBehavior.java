package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.dao.entity.NopMetaGlossaryTerm;
import io.nop.metadata.dao.entity.NopMetaTag;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * P2-29 行为回归测试（plan 2026-08-16-0920-1，裁定：删冗余 per-scope UK、保留全局 FQN UK）。
 *
 * <p>蕴含关系：非 NULL FQN 行 {@code (fullyQualifiedName)} 全局唯一 ⇒ {@code (scope,fullyQualifiedName)}
 * 唯一——删除 per-scope UK（UK_NOP_META_GLOSSARY_TERM_G_FQN / UK_NOP_META_TAG_CLS_FQN）后
 * 约束集语义不变。本测试在 H2（initDatabaseSchema=TRUE，schema 由 live 模型物化）钉死：
 * <ul>
 *   <li>全局 FQN 重复插入（同 glossary / 跨 glossary、同 classification / 跨 classification）
 *       仍被 DB 唯一约束拒绝——删 per-scope 不是放宽；</li>
 *   <li>GlossaryTerm FQN 可空语义保留：多行 NULL FQN 共存（NULL-distinct，既有行为）。</li>
 * </ul>
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaFqnUniqueKeyBehavior extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    private final Timestamp now = new Timestamp(System.currentTimeMillis());

    @Test
    public void testTagDuplicateFqnRejectedWithinAndAcrossClassifications() {
        IEntityDao<NopMetaTag> dao = daoProvider.daoFor(NopMetaTag.class);
        dao.saveEntity(tag("tag-fqn-a1", "cls-fqn-a", "Sales.PII"));

        // 同 classification 重复 FQN → 全局 UK 拒绝
        assertThrows(Exception.class, () -> dao.saveEntity(tag("tag-fqn-a2", "cls-fqn-a", "Sales.PII")),
                "duplicate tag FQN within same classification must be rejected by global UK");

        // 跨 classification 重复 FQN → 同样拒绝（全局唯一语义是保留项，非 per-scope）
        assertThrows(Exception.class, () -> dao.saveEntity(tag("tag-fqn-b1", "cls-fqn-b", "Sales.PII")),
                "duplicate tag FQN across classifications must be rejected by global UK");
    }

    @Test
    public void testGlossaryTermDuplicateFqnRejectedWithinAndAcrossGlossaries() {
        IEntityDao<NopMetaGlossaryTerm> dao = daoProvider.daoFor(NopMetaGlossaryTerm.class);
        dao.saveEntity(term("gt-fqn-a1", "gl-fqn-a", "BuiltIn.PII"));

        assertThrows(Exception.class, () -> dao.saveEntity(term("gt-fqn-a2", "gl-fqn-a", "BuiltIn.PII")),
                "duplicate term FQN within same glossary must be rejected by global UK");
        assertThrows(Exception.class, () -> dao.saveEntity(term("gt-fqn-b1", "gl-fqn-b", "BuiltIn.PII")),
                "duplicate term FQN across glossaries must be rejected by global UK");
    }

    @Test
    public void testGlossaryTermMultipleNullFqnsStillCoexist() {
        IEntityDao<NopMetaGlossaryTerm> dao = daoProvider.daoFor(NopMetaGlossaryTerm.class);
        assertDoesNotThrow(() -> {
            dao.saveEntity(term("gt-fqn-n1", "gl-fqn-n", null));
            dao.saveEntity(term("gt-fqn-n2", "gl-fqn-n", null));
        }, "NULL FQN rows stay exempt (NULL-distinct) — documented behavior of the kept global UK");
    }

    private NopMetaTag tag(String tagId, String classificationId, String fqn) {
        NopMetaTag t = daoProvider.daoFor(NopMetaTag.class).newEntity();
        t.setTagId(tagId);
        t.setClassificationId(classificationId);
        t.setName(tagId);
        t.setFullyQualifiedName(fqn);
        t.setVersion(1L);
        t.setCreatedBy("autotest");
        t.setUpdatedBy("autotest");
        t.setCreateTime(now);
        t.setUpdateTime(now);
        return t;
    }

    private NopMetaGlossaryTerm term(String termId, String glossaryId, String fqn) {
        NopMetaGlossaryTerm g = daoProvider.daoFor(NopMetaGlossaryTerm.class).newEntity();
        g.setGlossaryTermId(termId);
        g.setGlossaryId(glossaryId);
        g.setName(termId);
        g.setFullyQualifiedName(fqn);
        g.setVersion(1L);
        g.setCreatedBy("autotest");
        g.setUpdatedBy("autotest");
        g.setCreateTime(now);
        g.setUpdateTime(now);
        return g;
    }
}
