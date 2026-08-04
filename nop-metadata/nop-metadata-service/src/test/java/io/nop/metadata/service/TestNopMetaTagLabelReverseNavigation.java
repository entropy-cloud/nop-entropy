package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.dao.entity.NopMetaClassification;
import io.nop.metadata.dao.entity.NopMetaGlossary;
import io.nop.metadata.dao.entity.NopMetaGlossaryTerm;
import io.nop.metadata.dao.entity.NopMetaTag;
import io.nop.metadata.dao.entity.NopMetaTagLabel;
import io.nop.orm.IOrmSession;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.model.OrmEntityModel;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2-MA2-01 修复回归测试（plan-2026-08-04-1004-3，MA2.1 裁决例外）：
 * NopMetaTag / NopMetaGlossaryTerm 显式 `tagLabels` to-many 声明。
 *
 * <p>区分性断言（codegen 修复前已自动派生 getTagLabels()，因此必须验证"新增语义"而非集合存在性）：
 * <ul>
 *   <li>反向导航：DB-backed 流程（set FK → save → 新会话重载 → 断言反向集合）——Nop ORM 语义下
 *       `internalSetRefEntity` 只写 FK 不推内存反向集合，内存级断言必然失败</li>
 *   <li>cascadeDelete：删除 Tag/GlossaryTerm 后其 TagLabel 行级联删除（修复前无 cascadeDelete，
 *       生成 DDL 亦无 DB 级 FOREIGN KEY，孤儿行会永久残留）</li>
 *   <li>模型声明：ORM 模型中 tagLabels 关系带 cascadeDelete + displayName（修复前派生关系无）</li>
 * </ul>
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaTagLabelReverseNavigation extends JunitBaseTestCase {

    public TestNopMetaTagLabelReverseNavigation() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate orm;

    @Test
    public void testTagLabelsReverseNavigationDbBacked() {
        IEntityDao<NopMetaTag> tagDao = daoProvider.daoFor(NopMetaTag.class);
        IEntityDao<NopMetaTagLabel> labelDao = daoProvider.daoFor(NopMetaTagLabel.class);
        IEntityDao<NopMetaClassification> clsDao = daoProvider.daoFor(NopMetaClassification.class);

        NopMetaClassification cls = newClassification("cls-nav-tag-001", "NavTag");
        clsDao.saveEntity(cls);

        NopMetaTag tag = newTag("tag-nav-001", "cls-nav-tag-001", "NavTag.Marketing");
        tagDao.saveEntity(tag);

        NopMetaTagLabel label = newLabel("label-nav-001");
        label.setTagId("tag-nav-001");
        labelDao.saveEntity(label);
        labelDao.flushSession();

        // DB-backed 重载（新会话）：set to-one → save → flush → reload → 断言反向集合内容
        orm.runInNewSession(session -> {
            NopMetaTag reloaded = tagDao.getEntityById("tag-nav-001");
            assertNotNull(reloaded, "tag must be reloaded");
            assertFalse(reloaded.getTagLabels().isEmpty(),
                    "tag.tagLabels must contain the saved label after DB reload");
            assertEquals(1, reloaded.getTagLabels().size());
            assertEquals("label-nav-001", reloaded.getTagLabels().iterator().next().getTagLabelId());
            return null;
        });
    }

    @Test
    public void testGlossaryTermTagLabelsReverseNavigationDbBacked() {
        IEntityDao<NopMetaGlossary> glossaryDao = daoProvider.daoFor(NopMetaGlossary.class);
        IEntityDao<NopMetaGlossaryTerm> termDao = daoProvider.daoFor(NopMetaGlossaryTerm.class);
        IEntityDao<NopMetaTagLabel> labelDao = daoProvider.daoFor(NopMetaTagLabel.class);

        NopMetaGlossary glossary = glossaryDao.newEntity();
        glossary.setGlossaryId("gl-nav-001");
        glossary.setName("BizGlossary");
        glossary.setVersion(1L);
        glossary.setCreatedBy("autotest");
        glossary.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        glossary.setCreateTime(now);
        glossary.setUpdateTime(now);
        glossaryDao.saveEntity(glossary);

        NopMetaGlossaryTerm term = termDao.newEntity();
        term.setGlossaryTermId("term-nav-001");
        term.setGlossaryId("gl-nav-001");
        term.setName("Customer");
        term.setVersion(1L);
        term.setCreatedBy("autotest");
        term.setUpdatedBy("autotest");
        term.setCreateTime(now);
        term.setUpdateTime(now);
        termDao.saveEntity(term);

        NopMetaTagLabel label = newLabel("label-nav-002");
        label.setGlossaryTermId("term-nav-001");
        labelDao.saveEntity(label);
        labelDao.flushSession();

        orm.runInNewSession(session -> {
            NopMetaGlossaryTerm reloaded = termDao.getEntityById("term-nav-001");
            assertNotNull(reloaded, "term must be reloaded");
            assertFalse(reloaded.getTagLabels().isEmpty(),
                    "glossaryTerm.tagLabels must contain the saved label after DB reload");
            assertEquals(1, reloaded.getTagLabels().size());
            assertEquals("label-nav-002", reloaded.getTagLabels().iterator().next().getTagLabelId());
            return null;
        });
    }

    @Test
    public void testDeleteTagCascadesTagLabels() {
        IEntityDao<NopMetaClassification> clsDao = daoProvider.daoFor(NopMetaClassification.class);
        IEntityDao<NopMetaTag> tagDao = daoProvider.daoFor(NopMetaTag.class);
        IEntityDao<NopMetaTagLabel> labelDao = daoProvider.daoFor(NopMetaTagLabel.class);

        clsDao.saveEntity(newClassification("cls-cas-tag-001", "CasTag"));
        tagDao.saveEntity(newTag("tag-cas-001", "cls-cas-tag-001", "CasTag.Lead"));

        NopMetaTagLabel l1 = newLabel("label-cas-001");
        l1.setTagId("tag-cas-001");
        labelDao.saveEntity(l1);
        NopMetaTagLabel l2 = newLabel("label-cas-002");
        l2.setTagId("tag-cas-001");
        labelDao.saveEntity(l2);
        labelDao.flushSession();

        // 同一会话内 load + delete + flush：ORM 级联删除按 to-many cascadeDelete 关系加载并删除子行
        orm.runInSession(session -> {
            NopMetaTag tag = tagDao.getEntityById("tag-cas-001");
            assertNotNull(tag, "tag must exist before delete");
            tagDao.deleteEntity(tag);
            session.flush();
            return null;
        });

        orm.runInNewSession(session -> {
            assertTrue(labelDao.getEntityById("label-cas-001") == null,
                    "cascadeDelete must remove tag label rows when the tag is deleted");
            assertTrue(labelDao.getEntityById("label-cas-002") == null,
                    "cascadeDelete must remove all tag label rows");
            return null;
        });
    }

    @Test
    public void testDeleteGlossaryTermCascadesTagLabels() {
        IEntityDao<NopMetaGlossary> glossaryDao = daoProvider.daoFor(NopMetaGlossary.class);
        IEntityDao<NopMetaGlossaryTerm> termDao = daoProvider.daoFor(NopMetaGlossaryTerm.class);
        IEntityDao<NopMetaTagLabel> labelDao = daoProvider.daoFor(NopMetaTagLabel.class);

        NopMetaGlossary glossary = glossaryDao.newEntity();
        glossary.setGlossaryId("gl-cas-001");
        glossary.setName("CasGlossary");
        glossary.setVersion(1L);
        glossary.setCreatedBy("autotest");
        glossary.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        glossary.setCreateTime(now);
        glossary.setUpdateTime(now);
        glossaryDao.saveEntity(glossary);

        NopMetaGlossaryTerm term = termDao.newEntity();
        term.setGlossaryTermId("term-cas-001");
        term.setGlossaryId("gl-cas-001");
        term.setName("Order");
        term.setVersion(1L);
        term.setCreatedBy("autotest");
        term.setUpdatedBy("autotest");
        term.setCreateTime(now);
        term.setUpdateTime(now);
        termDao.saveEntity(term);

        NopMetaTagLabel label = newLabel("label-cas-003");
        label.setGlossaryTermId("term-cas-001");
        labelDao.saveEntity(label);
        labelDao.flushSession();

        orm.runInSession(session -> {
            NopMetaGlossaryTerm termToDelete = termDao.getEntityById("term-cas-001");
            assertNotNull(termToDelete, "term must exist before delete");
            termDao.deleteEntity(termToDelete);
            session.flush();
            return null;
        });

        orm.runInNewSession(session -> {
            assertTrue(labelDao.getEntityById("label-cas-003") == null,
                    "cascadeDelete must remove term label rows when the term is deleted");
            return null;
        });
    }

    @Test
    public void testTagLabelsRelationDeclaredWithCascadeAndDisplayName() {
        assertCascadeToMany("io.nop.metadata.dao.entity.NopMetaTag", "tagLabels",
                "标签标注集", "io.nop.metadata.dao.entity.NopMetaTagLabel", "tag");
        assertCascadeToMany("io.nop.metadata.dao.entity.NopMetaGlossaryTerm", "tagLabels",
                "术语标注集", "io.nop.metadata.dao.entity.NopMetaTagLabel", "glossaryTerm");
    }

    private void assertCascadeToMany(String entityName, String relName, String displayName,
                                     String refEntityName, String refPropName) {
        OrmEntityModel model = (OrmEntityModel) orm.getOrmModel().getEntityModel(entityName);
        assertNotNull(model, entityName + " model must be loaded");
        IEntityRelationModel rel = model.getRelation(relName);
        assertNotNull(rel, entityName + " must declare to-many " + relName);
        assertTrue(rel.isToManyRelation(), relName + " must be a to-many relation");
        assertEquals(refEntityName, rel.getRefEntityName(), relName + " must reference the label entity");
        assertEquals(refPropName, rel.getRefPropName(), relName + " refPropName must match the label's to-one");
        assertTrue(rel.isCascadeDelete(), relName + " must declare cascadeDelete (orphan prevention)");
        assertEquals(displayName, rel.getDisplayName(), relName + " must declare displayName (i18n sync)");
    }

    private NopMetaClassification newClassification(String id, String name) {
        NopMetaClassification cls = daoProvider.daoFor(NopMetaClassification.class).newEntity();
        cls.setClassificationId(id);
        cls.setName(name);
        cls.setDisplayName(name);
        cls.setMutuallyExclusive((byte) 0);
        cls.setProvider("system");
        cls.setDisabled((byte) 0);
        cls.setVersion(1L);
        cls.setCreatedBy("autotest");
        cls.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        cls.setCreateTime(now);
        cls.setUpdateTime(now);
        return cls;
    }

    private NopMetaTag newTag(String id, String classificationId, String fqn) {
        NopMetaTag tag = daoProvider.daoFor(NopMetaTag.class).newEntity();
        tag.setTagId(id);
        tag.setClassificationId(classificationId);
        tag.setName(fqn.substring(fqn.lastIndexOf('.') + 1));
        tag.setFullyQualifiedName(fqn);
        tag.setVersion(1L);
        tag.setCreatedBy("autotest");
        tag.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        tag.setCreateTime(now);
        tag.setUpdateTime(now);
        return tag;
    }

    private NopMetaTagLabel newLabel(String id) {
        NopMetaTagLabel label = daoProvider.daoFor(NopMetaTagLabel.class).newEntity();
        label.setTagLabelId(id);
        label.setSource("Manual");
        label.setLabelType("Manual");
        label.setState("ACTIVE");
        label.setEntityType("MetaTable");
        label.setEntityId("tbl-001");
        label.setVersion(1L);
        label.setCreatedBy("autotest");
        label.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        label.setCreateTime(now);
        label.setUpdateTime(now);
        return label;
    }
}
