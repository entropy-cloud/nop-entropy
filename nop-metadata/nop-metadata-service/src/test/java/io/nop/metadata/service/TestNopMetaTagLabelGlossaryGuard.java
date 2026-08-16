package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.dao.entity.NopMetaClassification;
import io.nop.metadata.dao.entity.NopMetaGlossary;
import io.nop.metadata.dao.entity.NopMetaGlossaryTerm;
import io.nop.metadata.dao.entity.NopMetaTag;
import io.nop.metadata.dao.entity.NopMetaTagLabel;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2-01 守卫回归测试（plan 2026-08-16-0920-1，裁决选项 ii）：
 *
 * <p>{@code UK_NOP_META_TAG_LABEL (entityType,entityId,tagId,source)} 对 GLOSSARY 来源、
 * tagId=NULL 的行不生效（复合 UK 任一列 NULL 即豁免唯一性），显式客户端 save 可累积重复行。
 * 应用层查重守卫（save + update 双入口）按
 * {@code (entityType, entityId, source=Glossary, glossaryTermId, tagId IS NULL)} 查重，
 * 命中 fail-loud（nop.err.metadata.tag-label-duplicate-glossary-term），事务回滚零残留。
 *
 * <p>区分性断言（Anti-Hollow）：守卫只拦 GLOSSARY+NULL-tagId 面——
 * <ul>
 *   <li>不同 glossaryTermId 的 GLOSSARY 行共（entityType,entityId）合法（守卫不误伤）；</li>
 *   <li>tagId 承载行（含 GLOSSARY source）继续由 DB UK 拒绝（既有路径不回归）；</li>
 *   <li>save 带自身 id 的幂等更新不自误伤（自排除）。</li>
 * </ul>
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaTagLabelGlossaryGuard extends JunitBaseTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    private final Timestamp now = new Timestamp(System.currentTimeMillis());

    @Test
    public void testDuplicateGlossaryTermLabelSaveRejected() {
        createGlossary("gl-p201", "GlossaryP201");
        createTerm("gt-p201-a", "gl-p201");

        Map<String, Object> first = glossaryLabelData("tl-p201-1", "gt-p201-a", "NopMetaEntityField", "field-p201-1");
        GraphQLResponseBean resp1 = saveTagLabelViaGql(first);
        assertFalse(resp1.hasError(), "first GLOSSARY null-tag label must save: " + resp1);

        // 显式客户端 save 重复行 → 守卫 fail-loud（修复前该 save 成功、重复行落库）
        Map<String, Object> dup = glossaryLabelData("tl-p201-2", "gt-p201-a", "NopMetaEntityField", "field-p201-1");
        GraphQLResponseBean resp2 = saveTagLabelViaGql(dup);
        assertTrue(resp2.hasError(), "duplicate GLOSSARY null-tag save must be rejected");
        assertTrue(resp2.getErrors().get(0).getMessage().contains("Duplicate glossary term tag label"),
                "must fail with ERR_TAG_LABEL_DUPLICATE_GLOSSARY_TERM description: " + resp2.getErrors().get(0));

        assertEquals(1, countGlossaryNullTagLabels("NopMetaEntityField", "field-p201-1", "gt-p201-a"),
                "exactly one row may land (guard throw rolls back the duplicate)");
    }

    @Test
    public void testDifferentTermsOnSameEntityAllowed() {
        createGlossary("gl-p202", "GlossaryP202");
        createTerm("gt-p202-a", "gl-p202");
        createTerm("gt-p202-b", "gl-p202");

        Map<String, Object> a = glossaryLabelData("tl-p202-1", "gt-p202-a", "NopMetaTable", "tbl-p202-1");
        Map<String, Object> b = glossaryLabelData("tl-p202-2", "gt-p202-b", "NopMetaTable", "tbl-p202-1");
        assertFalse(saveTagLabelViaGql(a).hasError(), "term A label must save");
        assertFalse(saveTagLabelViaGql(b).hasError(), "term B label on same entity must save (no false positive)");

        Map<String, Object> otherEntity = glossaryLabelData("tl-p202-3", "gt-p202-a", "NopMetaTable", "tbl-p202-2");
        assertFalse(saveTagLabelViaGql(otherEntity).hasError(),
                "same term on a different entity must save (guard keyed on entityType+entityId too)");
    }

    @Test
    public void testTagBearingRowsStillGuardedByDbUniqueKey() {
        createGlossary("gl-p203", "GlossaryP203");
        createTerm("gt-p203-a", "gl-p203");
        createClassification("cls-p203", "ClsP203");
        createTag("tag-p203-x", "cls-p203");

        Map<String, Object> withTag = glossaryLabelData("tl-p203-1", "gt-p203-a", "NopMetaEntityField", "field-p203-1");
        withTag.put("tagId", "tag-p203-x");
        assertFalse(saveTagLabelViaGql(withTag).hasError(), "GLOSSARY row with tagId must save");

        // tagId 承载行的重复 → DB UK 拒绝（既有保护面，守卫不接管、不放松）
        Map<String, Object> dupWithTag = glossaryLabelData("tl-p203-2", "gt-p203-a", "NopMetaEntityField", "field-p203-1");
        dupWithTag.put("tagId", "tag-p203-x");
        GraphQLResponseBean dupResp = saveTagLabelViaGql(dupWithTag);
        assertTrue(dupResp.hasError(), "duplicate tagId-bearing row must still be rejected by DB UK");
        assertEquals(1, countTagBearingLabels("NopMetaEntityField", "field-p203-1", "tag-p203-x"),
                "no duplicate tagId-bearing row may land");
    }

    @Test
    public void testDuplicateViaUpdateMutationRejected() {
        createGlossary("gl-p204", "GlossaryP204");
        createTerm("gt-p204-a", "gl-p204");
        createTerm("gt-p204-b", "gl-p204");

        Map<String, Object> a = glossaryLabelData("tl-p204-1", "gt-p204-a", "NopMetaEntityField", "field-p204-1");
        Map<String, Object> b = glossaryLabelData("tl-p204-2", "gt-p204-b", "NopMetaEntityField", "field-p204-2");
        assertFalse(saveTagLabelViaGql(a).hasError(), "label A must save");
        assertFalse(saveTagLabelViaGql(b).hasError(), "label B must save");

        // update 变更 B 的 glossaryTermId/entityId 撞向 A 的形状 → 守卫拒绝（对称写面）
        Map<String, Object> collide = new HashMap<>();
        collide.put("id", "tl-p204-2");
        collide.put("glossaryTermId", "gt-p204-a");
        collide.put("entityId", "field-p204-1");
        GraphQLResponseBean resp = updateTagLabelViaGql(collide);
        assertTrue(resp.hasError(), "update colliding into existing GLOSSARY null-tag row must be rejected");
        assertTrue(resp.getErrors().get(0).getMessage().contains("Duplicate glossary term tag label"),
                "must fail with guard error: " + resp.getErrors().get(0));
        assertEquals(1, countGlossaryNullTagLabels("NopMetaEntityField", "field-p204-1", "gt-p204-a"),
                "row A stays exactly one (update rolled back)");
    }

    @Test
    public void testSelfUpdateNotFalsePositive() {
        createGlossary("gl-p205", "GlossaryP205");
        createTerm("gt-p205-a", "gl-p205");

        Map<String, Object> a = glossaryLabelData("tl-p205-1", "gt-p205-a", "NopMetaEntityField", "field-p205-1");
        assertFalse(saveTagLabelViaGql(a).hasError(), "label must save");

        // 幂等自更新（id 指向自身，形状不变）→ 不自误伤（自排除）
        Map<String, Object> self = glossaryLabelData("tl-p205-1", "gt-p205-a", "NopMetaEntityField", "field-p205-1");
        self.put("id", "tl-p205-1");
        GraphQLResponseBean resp = updateTagLabelViaGql(self);
        assertFalse(resp.hasError(), "idempotent self-update must not trip the guard: " + resp);
        assertEquals(1, countGlossaryNullTagLabels("NopMetaEntityField", "field-p205-1", "gt-p205-a"),
                "still exactly one row");
    }

    private Map<String, Object> glossaryLabelData(String tagLabelId, String glossaryTermId,
                                                  String entityType, String entityId) {
        Map<String, Object> data = new HashMap<>();
        data.put("tagLabelId", tagLabelId);
        data.put("source", "Glossary");
        data.put("glossaryTermId", glossaryTermId);
        data.put("labelType", "Manual");
        data.put("state", "Confirmed");
        data.put("entityType", entityType);
        data.put("entityId", entityId);
        data.put("version", 1);
        return data;
    }

    private int countGlossaryNullTagLabels(String entityType, String entityId, String glossaryTermId) {
        IEntityDao<NopMetaTagLabel> dao = daoProvider.daoFor(NopMetaTagLabel.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityType, entityType));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityId, entityId));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_source, "Glossary"));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_glossaryTermId, glossaryTermId));
        q.addFilter(FilterBeans.isNull(NopMetaTagLabel.PROP_NAME_tagId));
        List<NopMetaTagLabel> rows = dao.findAllByQuery(q);
        return rows.size();
    }

    private int countTagBearingLabels(String entityType, String entityId, String tagId) {
        IEntityDao<NopMetaTagLabel> dao = daoProvider.daoFor(NopMetaTagLabel.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityType, entityType));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityId, entityId));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_tagId, tagId));
        return dao.findAllByQuery(q).size();
    }

    private void createClassification(String classificationId, String name) {
        IEntityDao<NopMetaClassification> dao = daoProvider.daoFor(NopMetaClassification.class);
        NopMetaClassification c = dao.newEntity();
        c.setClassificationId(classificationId);
        c.setName(name);
        c.setDisplayName(name);
        c.setMutuallyExclusive((byte) 0);
        c.setProvider("system");
        c.setDisabled((byte) 0);
        c.setVersion(1L);
        c.setCreatedBy("autotest");
        c.setUpdatedBy("autotest");
        c.setCreateTime(now);
        c.setUpdateTime(now);
        dao.saveEntity(c);
    }

    private void createTag(String tagId, String classificationId) {
        IEntityDao<NopMetaTag> dao = daoProvider.daoFor(NopMetaTag.class);
        NopMetaTag t = dao.newEntity();
        t.setTagId(tagId);
        t.setClassificationId(classificationId);
        t.setName(tagId);
        t.setFullyQualifiedName(tagId);
        t.setVersion(1L);
        t.setCreatedBy("autotest");
        t.setUpdatedBy("autotest");
        t.setCreateTime(now);
        t.setUpdateTime(now);
        dao.saveEntity(t);
    }

    private void createTerm(String termId, String glossaryId) {
        IEntityDao<NopMetaGlossaryTerm> dao = daoProvider.daoFor(NopMetaGlossaryTerm.class);
        NopMetaGlossaryTerm t = dao.newEntity();
        t.setGlossaryTermId(termId);
        t.setGlossaryId(glossaryId);
        t.setName(termId);
        t.setVersion(1L);
        t.setCreatedBy("autotest");
        t.setUpdatedBy("autotest");
        t.setCreateTime(now);
        t.setUpdateTime(now);
        dao.saveEntity(t);
    }

    private void createGlossary(String glossaryId, String name) {
        IEntityDao<NopMetaGlossary> dao = daoProvider.daoFor(NopMetaGlossary.class);
        NopMetaGlossary g = dao.newEntity();
        g.setGlossaryId(glossaryId);
        g.setName(name);
        g.setDisplayName(name);
        g.setVersion(1L);
        g.setCreatedBy("autotest");
        g.setUpdatedBy("autotest");
        g.setCreateTime(now);
        g.setUpdateTime(now);
        dao.saveEntity(g);
    }

    private GraphQLResponseBean saveTagLabelViaGql(Map<String, Object> data) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("data", data);
        return executeWithVars(
                "mutation($data:Map) { NopMetaTagLabel__save(data:$data) { tagLabelId source glossaryTermId tagId } }",
                vars);
    }

    private GraphQLResponseBean updateTagLabelViaGql(Map<String, Object> data) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("data", data);
        return executeWithVars(
                "mutation($data:Map) { NopMetaTagLabel__update(data:$data) { tagLabelId source glossaryTermId tagId } }",
                vars);
    }

    private GraphQLResponseBean executeWithVars(String query, Map<String, Object> vars) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        request.setVariables(vars);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }
}
