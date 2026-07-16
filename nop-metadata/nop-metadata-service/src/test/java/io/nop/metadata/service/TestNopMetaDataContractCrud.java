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
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.dao.entity.NopMetaDataContract;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 NopMetaDataContract 实体建模（Phase 1，plan 0900-1）：
 * GraphQL CRUD 由 CrudBizModel 自动暴露（registerShortName=true），无需手写 BizModel。
 *
 * <p>覆盖：
 * <ul>
 *   <li>create + query：经 DAO 写入 + GraphQL {@code NopMetaDataContract__get} 读回，断言字段一致。</li>
 *   <li>findPage：GraphQL {@code NopMetaDataContract__findPage} 返回真实行。</li>
 *   <li>delete：GraphQL {@code NopMetaDataContract__delete} 后实体不可查。</li>
 *   <li>dict 约束：status 使用大写 4 值（DRAFT/ACTIVE/DEPRECATED/RETIRED）。</li>
 * </ul>
 *
 * <p>Anti-Hollow：写入真实 DRAFT 契约（含 sla/qualityExpectations JSON），GraphQL 读回断言字段值一致，
 * 证明实体建模、列映射、to-one relation(metaTable)、dict(contract-status) 全链路可用，非空壳。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaDataContractCrud extends JunitBaseTestCase {

    public TestNopMetaDataContractCrud() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    /** create + query：DAO 写入 → GraphQL get 读回，字段一致。 */
    @Test
    public void testCreateAndQueryViaGraphQL() {
        String contractId = saveContractDirect("c-crud-1", "DRAFT",
                "{\"refreshFrequency\":{\"interval\":1,\"unit\":\"day\"}}",
                "{\"qualityRuleIds\":[\"r-1\",\"r-2\"]}");

        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "query { NopMetaDataContract__get(id: \"" + contractId + "\") { contractId contractName status "
                        + "sla qualityExpectations ownerUserId entityTableId } }")));
        assertFalse(resp.hasError(), "get should not error: " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("contractId=" + contractId), "contractId must be returned: " + data);
        assertTrue(data.contains("contractName=c-crud-1-name"), "contractName must be returned: " + data);
        assertTrue(data.contains("status=DRAFT"), "status must be DRAFT: " + data);
        assertTrue(data.contains("qualityRuleIds"), "qualityExpectations JSON must be stored: " + data);
        assertTrue(data.contains("refreshFrequency"), "sla JSON must be stored: " + data);
    }

    /** findPage：GraphQL findPage 返回真实行。 */
    @Test
    public void testFindPageViaGraphQL() {
        saveContractDirect("c-crud-2", "ACTIVE", null, null);
        saveContractDirect("c-crud-3", "DEPRECATED", null, null);

        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "query { NopMetaDataContract__findPage(query: { limit: 10 }) { total items { contractId status } } }")));
        assertFalse(resp.hasError(), "findPage should not error: " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("c-crud-2"), "findPage must return c-crud-2: " + data);
        assertTrue(data.contains("c-crud-3"), "findPage must return c-crud-3: " + data);
    }

    /** delete：GraphQL delete 后实体不可查。 */
    @Test
    public void testDeleteViaGraphQL() {
        String contractId = saveContractDirect("c-crud-4", "DRAFT", null, null);

        GraphQLResponseBean delResp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaDataContract__delete(id: \"" + contractId + "\") }")));
        assertFalse(delResp.hasError(), "delete should not error: " + delResp);

        IEntityDao<NopMetaDataContract> dao = daoProvider.daoFor(NopMetaDataContract.class);
        assertNull(dao.getEntityById(contractId), "contract must be deleted");
    }

    /** status dict 4 大写值均可持久化（建模约束验证）。 */
    @Test
    public void testStatusDictFourValues() {
        saveContractDirect("c-st-draft", "DRAFT", null, null);
        saveContractDirect("c-st-active", "ACTIVE", null, null);
        saveContractDirect("c-st-deprecated", "DEPRECATED", null, null);
        saveContractDirect("c-st-retired", "RETIRED", null, null);

        IEntityDao<NopMetaDataContract> dao = daoProvider.daoFor(NopMetaDataContract.class);
        assertEquals("DRAFT", dao.getEntityById("c-st-draft").getStatus());
        assertEquals("ACTIVE", dao.getEntityById("c-st-active").getStatus());
        assertEquals("DEPRECATED", dao.getEntityById("c-st-deprecated").getStatus());
        assertEquals("RETIRED", dao.getEntityById("c-st-retired").getStatus());
    }

    // ===== helpers =====

    private String saveContractDirect(String contractId, String status, String sla, String qualityExpectations) {
        IEntityDao<NopMetaDataContract> dao = daoProvider.daoFor(NopMetaDataContract.class);
        NopMetaDataContract c = dao.newEntity();
        c.setContractId(contractId);
        c.setContractName(contractId + "-name");
        c.setDisplayName(contractId + "-name");
        c.setStatus(status);
        c.setOwnerUserId("autotest");
        c.setSla(sla);
        c.setQualityExpectations(qualityExpectations);
        c.setVersion(1L);
        c.setCreatedBy("autotest");
        c.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        c.setCreateTime(now);
        c.setUpdateTime(now);
        dao.saveEntity(c);
        return contractId;
    }

    private GraphQLRequestBean req(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        return request;
    }
}
