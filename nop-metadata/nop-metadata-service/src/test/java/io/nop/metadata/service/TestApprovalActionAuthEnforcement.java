package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.auth.api.AuthApiErrors;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.util.CollectionHelper;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.dao.entity.NopMetaDataContract;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * P1-2.6-005/006 全链 enforcement 回归（plan 2378 R1.12 Phase 1）：
 * enable-action-auth=true + DefaultActionAuthChecker（nop-auth-service test 依赖）在位时，
 * xbiz 声明 roles="admin" 的 approval 动作对非 admin 用户必须拒绝（ERR_AUTH_NO_PERMISSION），
 * admin 用户放行。链路：xbiz &lt;auth&gt; → 合并终值 → GraphQLFieldDefinition.auth →
 * GraphQLActionAuthChecker → UserContextImpl 角色。
 * 沿 nop-datav TestNopDatavRbacAuth 先例（@NopTestConfig(enableActionAuth=TRUE)）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.TRUE)
public class TestApprovalActionAuthEnforcement extends JunitBaseTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    @AfterEach
    public void clearUserContext() {
        IUserContext.set(null);
    }

    @Test
    public void testNonAdminUserDeniedApprove() {
        String id = saveSubmittedContract("c-auth-deny");

        setUserContext("regular-user", "user");

        ApiResponse<?> response = executeApprove(id);
        assertNotNull(response);
        assertEquals(AuthApiErrors.ERR_AUTH_NO_PERMISSION.getErrorCode(), response.getCode(),
                "user role must be denied NopMetaDataContract__approve (roles=admin), got: " + response);
    }

    @Test
    public void testAdminUserAllowedApprove() {
        String id = saveSubmittedContract("c-auth-admin");

        setUserContext("admin-user", "admin");

        ApiResponse<?> response = executeApprove(id);
        assertNotNull(response);
        assertEquals(0, response.getStatus(), "admin must be allowed NopMetaDataContract__approve, got: " + response);
    }

    private void setUserContext(String userId, String... roles) {
        UserContextImpl userContext = new UserContextImpl();
        userContext.setUserId(userId);
        userContext.setUserName(userId);
        userContext.setRoles(CollectionHelper.buildImmutableSet(roles));
        IUserContext.set(userContext);
    }

    private ApiResponse<?> executeApprove(String contractId) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", contractId);

        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(data);

        io.nop.graphql.core.IGraphQLExecutionContext context =
                graphQLEngine.newRpcContext(GraphQLOperationType.mutation, "NopMetaDataContract__approve", request);
        return io.nop.api.core.util.FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context));
    }

    private String saveSubmittedContract(String contractId) {
        IEntityDao<NopMetaDataContract> dao = daoProvider.daoFor(NopMetaDataContract.class);
        NopMetaDataContract c = dao.newEntity();
        c.setContractId(contractId);
        c.setContractName(contractId + "-name");
        c.setDisplayName(contractId + "-name");
        c.setStatus("DRAFT");
        c.setApproveStatus("SUBMITTED");
        c.setOwnerUserId("autotest");
        c.setVersion(1L);
        c.setCreatedBy("autotest");
        c.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        c.setCreateTime(now);
        c.setUpdateTime(now);
        dao.saveEntity(c);
        return contractId;
    }
}
