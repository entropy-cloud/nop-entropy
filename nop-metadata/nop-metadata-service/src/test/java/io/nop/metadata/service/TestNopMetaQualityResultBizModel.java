package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.dao.entity.NopMetaQualityResult;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaQualityResultBizModel extends JunitBaseTestCase {

    public TestNopMetaQualityResultBizModel() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    @Test
    public void testApprove() {
        String id = saveQualityResult("test-approve", "PASS", "needs review");
        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaQualityResult__approve(id: \"" + id + "\") { qualityResultId status } }")));
        assertFalse(resp.hasError(), "approve must not error: " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("qualityResultId=" + id), "response must contain qualityResultId: " + data);
    }

    @Test
    public void testRejectSetsFalsePositive() {
        String id = saveQualityResult("test-reject", "FAIL", "false positive case");
        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaQualityResult__reject(id: \"" + id + "\") { qualityResultId status } }")));
        assertFalse(resp.hasError(), "reject must not error: " + resp);

        NopMetaQualityResult reloaded = daoProvider.daoFor(NopMetaQualityResult.class).getEntityById(id);
        assertNotNull(reloaded, "entity must exist after reject");
        assertEquals((byte) 1, reloaded.getIsFalsePositive().byteValue(),
                "reject must set isFalsePositive=1");
    }

    @Test
    public void testApproveOnNonExistentReturnsError() {
        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaQualityResult__approve(id: \"__nope__\") { qualityResultId } }")));
        assertTrue(resp.hasError(), "approve on non-existent must error: " + resp);
    }

    @Test
    public void testRejectOnNonExistentReturnsError() {
        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaQualityResult__reject(id: \"__nope__\") { qualityResultId } }")));
        assertTrue(resp.hasError(), "reject on non-existent must error: " + resp);
    }

    private String saveQualityResult(String qualityRuleId, String status, String message) {
        IEntityDao<NopMetaQualityResult> dao = daoProvider.daoFor(NopMetaQualityResult.class);
        NopMetaQualityResult r = dao.newEntity();
        r.setQualityRuleId(qualityRuleId);
        r.setExecuteTime(new Timestamp(System.currentTimeMillis()));
        r.setStatus(status);
        r.setMessage(message);
        r.setVersion(1L);
        r.setCreatedBy("autotest");
        r.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        r.setCreateTime(now);
        r.setUpdateTime(now);
        dao.saveEntity(r);
        return r.getQualityResultId();
    }

    private static GraphQLRequestBean req(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        return request;
    }
}
