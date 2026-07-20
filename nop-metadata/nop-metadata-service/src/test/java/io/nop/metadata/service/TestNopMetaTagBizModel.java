package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.dao.entity.NopMetaClassification;
import io.nop.metadata.dao.entity.NopMetaTag;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 NopMetaTagBizModel.save 的 fullyQualifiedName 自动生成逻辑。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaTagBizModel extends JunitBaseTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    @Test
    public void testRootTagAutoFqn() {
        IEntityDao<NopMetaClassification> clsDao = daoProvider.daoFor(NopMetaClassification.class);

        // Create Classification with name "PII"
        NopMetaClassification cls = clsDao.newEntity();
        cls.setClassificationId("cls-fqn-root-001");
        cls.setName("PII");
        cls.setDisplayName("敏感数据");
        cls.setMutuallyExclusive((byte) 0);
        cls.setProvider("system");
        cls.setDisabled((byte) 0);
        cls.setVersion(1L);
        cls.setCreatedBy("autotest");
        cls.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        cls.setCreateTime(now);
        cls.setUpdateTime(now);
        clsDao.saveEntity(cls);

        // Save Tag without fullyQualifiedName — should auto-generate to "PII.Sensitive"
        GraphQLResponseBean response = execute(
                "mutation { NopMetaTag__save(data: { tagId: \"tag-fqn-root-001\", classificationId: \"cls-fqn-root-001\", name: \"Sensitive\", displayName: \"敏感\" }) { tagId fullyQualifiedName } }");
        assertFalse(response.hasError(), "save should not error: " + response);

        // Read back via GraphQL to verify FQN
        response = execute(
                "query { NopMetaTag__get(id: \"tag-fqn-root-001\") { tagId fullyQualifiedName } }");
        assertFalse(response.hasError(), "get should not error: " + response);
        String data = response.getData().toString();
        assertTrue(data.contains("PII.Sensitive"), "root tag FQN should be \"PII.Sensitive\": " + data);
    }

    @Test
    public void testChildTagAutoFqn() {
        IEntityDao<NopMetaClassification> clsDao = daoProvider.daoFor(NopMetaClassification.class);
        IEntityDao<NopMetaTag> tagDao = daoProvider.daoFor(NopMetaTag.class);

        // Create Classification
        NopMetaClassification cls = clsDao.newEntity();
        cls.setClassificationId("cls-fqn-child-001");
        cls.setName("PII");
        cls.setDisplayName("敏感数据");
        cls.setMutuallyExclusive((byte) 0);
        cls.setProvider("system");
        cls.setDisabled((byte) 0);
        cls.setVersion(1L);
        cls.setCreatedBy("autotest");
        cls.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        cls.setCreateTime(now);
        cls.setUpdateTime(now);
        clsDao.saveEntity(cls);

        // Create parent tag with FQN "PII.Sensitive"
        NopMetaTag parentTag = tagDao.newEntity();
        parentTag.setTagId("tag-fqn-parent-001");
        parentTag.setClassificationId("cls-fqn-child-001");
        parentTag.setName("Sensitive");
        parentTag.setFullyQualifiedName("PII.Sensitive");
        parentTag.setDisplayName("敏感");
        parentTag.setVersion(1L);
        parentTag.setCreatedBy("autotest");
        parentTag.setUpdatedBy("autotest");
        parentTag.setCreateTime(now);
        parentTag.setUpdateTime(now);
        tagDao.saveEntity(parentTag);

        // Save child tag without FQN — should auto-generate to "PII.Sensitive.Phone"
        GraphQLResponseBean response = execute(
                "mutation { NopMetaTag__save(data: { tagId: \"tag-fqn-child-002\", classificationId: \"cls-fqn-child-001\", parentTagId: \"tag-fqn-parent-001\", name: \"Phone\", displayName: \"电话\" }) { tagId fullyQualifiedName } }");
        assertFalse(response.hasError(), "save should not error: " + response);

        response = execute(
                "query { NopMetaTag__get(id: \"tag-fqn-child-002\") { tagId fullyQualifiedName } }");
        assertFalse(response.hasError(), "get should not error: " + response);
        String data = response.getData().toString();
        assertTrue(data.contains("PII.Sensitive.Phone"), "child tag FQN should be \"PII.Sensitive.Phone\": " + data);
    }

    @Test
    public void testGrandchildTagAutoFqn() {
        IEntityDao<NopMetaClassification> clsDao = daoProvider.daoFor(NopMetaClassification.class);
        IEntityDao<NopMetaTag> tagDao = daoProvider.daoFor(NopMetaTag.class);

        // Create Classification "DataClass"
        NopMetaClassification cls = clsDao.newEntity();
        cls.setClassificationId("cls-fqn-grand-001");
        cls.setName("DataClass");
        cls.setDisplayName("数据分类");
        cls.setMutuallyExclusive((byte) 0);
        cls.setProvider("system");
        cls.setDisabled((byte) 0);
        cls.setVersion(1L);
        cls.setCreatedBy("autotest");
        cls.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        cls.setCreateTime(now);
        cls.setUpdateTime(now);
        clsDao.saveEntity(cls);

        // Level1
        NopMetaTag level1 = tagDao.newEntity();
        level1.setTagId("tag-fqn-grand-parent");
        level1.setClassificationId("cls-fqn-grand-001");
        level1.setName("Level1");
        level1.setFullyQualifiedName("DataClass.Level1");
        level1.setVersion(1L);
        level1.setCreatedBy("autotest");
        level1.setUpdatedBy("autotest");
        level1.setCreateTime(now);
        level1.setUpdateTime(now);
        tagDao.saveEntity(level1);

        // Level2 (child of Level1)
        NopMetaTag level2 = tagDao.newEntity();
        level2.setTagId("tag-fqn-grand-child");
        level2.setClassificationId("cls-fqn-grand-001");
        level2.setParentTagId("tag-fqn-grand-parent");
        level2.setName("Level2");
        level2.setFullyQualifiedName("DataClass.Level1.Level2");
        level2.setVersion(1L);
        level2.setCreatedBy("autotest");
        level2.setUpdatedBy("autotest");
        level2.setCreateTime(now);
        level2.setUpdateTime(now);
        tagDao.saveEntity(level2);

        // Save Level3 (grandchild) without FQN — should auto-generate to "DataClass.Level1.Level2.Level3"
        GraphQLResponseBean response = execute(
                "mutation { NopMetaTag__save(data: { tagId: \"tag-fqn-grand-grandchild\", classificationId: \"cls-fqn-grand-001\", parentTagId: \"tag-fqn-grand-child\", name: \"Level3\", displayName: \"层级3\" }) { tagId fullyQualifiedName } }");
        assertFalse(response.hasError(), "save should not error: " + response);

        response = execute(
                "query { NopMetaTag__get(id: \"tag-fqn-grand-grandchild\") { tagId fullyQualifiedName } }");
        assertFalse(response.hasError(), "get should not error: " + response);
        String data = response.getData().toString();
        assertTrue(data.contains("DataClass.Level1.Level2.Level3"), "grandchild FQN should be \"DataClass.Level1.Level2.Level3\": " + data);
    }

    @Test
    public void testManualFqnOverride() {
        IEntityDao<NopMetaClassification> clsDao = daoProvider.daoFor(NopMetaClassification.class);

        // Create Classification "Region"
        NopMetaClassification cls = clsDao.newEntity();
        cls.setClassificationId("cls-fqn-manual-001");
        cls.setName("Region");
        cls.setDisplayName("区域");
        cls.setMutuallyExclusive((byte) 0);
        cls.setProvider("system");
        cls.setDisabled((byte) 0);
        cls.setVersion(1L);
        cls.setCreatedBy("autotest");
        cls.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        cls.setCreateTime(now);
        cls.setUpdateTime(now);
        clsDao.saveEntity(cls);

        // Save Tag with explicit fullyQualifiedName — should NOT be overridden
        GraphQLResponseBean response = execute(
                "mutation { NopMetaTag__save(data: { tagId: \"tag-fqn-manual-001\", classificationId: \"cls-fqn-manual-001\", name: \"EMEA\", fullyQualifiedName: \"Custom.EMEA\", displayName: \"欧洲\" }) { tagId fullyQualifiedName } }");
        assertFalse(response.hasError(), "save should not error: " + response);

        response = execute(
                "query { NopMetaTag__get(id: \"tag-fqn-manual-001\") { tagId fullyQualifiedName } }");
        assertFalse(response.hasError(), "get should not error: " + response);
        String data = response.getData().toString();
        assertTrue(data.contains("Custom.EMEA"), "manual FQN should be preserved as \"Custom.EMEA\": " + data);
    }

    private GraphQLResponseBean execute(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }
}
