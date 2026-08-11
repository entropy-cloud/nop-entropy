package io.nop.datav.service.entity;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.context.TenantProxyContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.dao.api.IDaoProvider;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.datav.biz.INopDatavDashboardShareBiz;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDashboardShare;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * passwordHash 字段不可见化 + createShare 脱敏验证（plan {1} Phase 1）。
 *
 * <p>验证两点：</p>
 * <ol>
 *   <li>{@code createShare} 返回实体 passwordHash == null（mask-on-return contract，
 *       与 {@code listShares}/{@code doToggleShare} 对齐）。</li>
 *   <li>GraphQL 继承 CRUD（{@code findPage}/{@code get}）经 xmeta
 *       {@code published="false"} 屏蔽后，passwordHash 从 GraphQL 出口移除：
 *       显式选择该字段会触发 {@code nop.err.graphql.undefined-field}，
 *       即字段不存在于 GraphQL schema（最严格的不可读保护）。
 *       机制：{@code ObjMetaToGraphQLDefinition} 读取 {@code propMeta.isPublished()}
 *       决定 GraphQL 出口。</li>
 * </ol>
 *
 * <p>本测试不启用 action-auth（{@code enableActionAuth} 默认 false），因为 Phase 1
 * 不依赖角色绑定，仅验证字段可见性机制。</p>
 */
public class TestNopDatavSharePasswordHashMasking extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    INopDatavDashboardShareBiz shareBiz;

    /**
     * createShare 返回实体 passwordHash == null；DB 行持久化了 BCrypt 哈希（防降级回归）。
     */
    @Test
    public void testCreateShareReturnsNullPasswordHash() {
        IServiceContext ownerCtx = newOwnerContext("alice");
        NopDatavDashboard dash = saveDashboardOwnedBy("dash-mask-1", "mask-create", "alice");

        NopDatavDashboardShare share = shareBiz.createShare(
                dash.getDashboardId(), "secretPwd", null, ownerCtx);

        assertNotNull(share.getShareId(), "share persisted");
        assertNull(share.getPasswordHash(),
                "createShare return value must never expose passwordHash (mask-on-return)");

        NopDatavDashboardShare persisted = daoProvider.daoFor(NopDatavDashboardShare.class)
                .getEntityById(share.getShareId());
        assertNotNull(persisted.getPasswordHash(), "DB row must persist a password hash");
        assertTrue(persisted.getPasswordHash().startsWith("$2a"),
                "persisted hash is BCrypt composite output");
    }

    /**
     * findPage（继承 CRUD）经 GraphQL 出口时，passwordHash 字段不可选——
     * published=false 直接从 schema 移除该字段，显式选择触发 undefined-field。
     *
     * <p>双保险：先断言「不可选」（GraphQL 层 schema 移除），
     * 再断言「默认 findPage 输出无 passwordHash」（不显式选也读不到）。</p>
     */
    @Test
    public void testFindPageRejectsPasswordHashSelectionAndDefaultHasNoLeak() {
        IServiceContext ownerCtx = newOwnerContext("alice");
        NopDatavDashboard dash = saveDashboardOwnedBy("dash-mask-2", "mask-findpage", "alice");
        shareBiz.createShare(dash.getDashboardId(), "pwd1", null, ownerCtx);
        shareBiz.createShare(dash.getDashboardId(), null, null, ownerCtx);

        // 1) 显式请求 passwordHash → schema 层直接拒绝（undefined-field）。
        FieldSelectionBean explicitSelection = new FieldSelectionBean();
        FieldSelectionBean itemsField = new FieldSelectionBean();
        itemsField.addField("shareId");
        itemsField.addField("passwordHash");
        explicitSelection.addField("items", itemsField);
        explicitSelection.addField("total");

        ApiRequest<Object> explicitRequest = new ApiRequest<>();
        explicitRequest.setSelection(explicitSelection);

        NopException ex = assertThrows(NopException.class, () -> {
            IGraphQLExecutionContext context = graphQLEngine.newRpcContext(
                    GraphQLOperationType.query,
                    "NopDatavDashboardShare__findPage",
                    explicitRequest);
            FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context));
        });
        assertEquals("nop.err.graphql.undefined-field", ex.getErrorCode(),
                "selecting passwordHash must be rejected: published=false removes it from schema");

        // 2) 默认 findPage（不显式选敏感字段）输出中无 passwordHash key。
        ApiRequest<Object> defaultRequest = new ApiRequest<>();
        IGraphQLExecutionContext defaultContext = graphQLEngine.newRpcContext(
                GraphQLOperationType.query,
                "NopDatavDashboardShare__findPage",
                defaultRequest);
        ApiResponse<?> response = FutureHelper.syncGet(graphQLEngine.executeRpcAsync(defaultContext));

        assertEquals(0, response.getStatus(),
                "findPage should succeed (action-auth disabled), got: " + response);
        assertNotNull(response.getData(), "findPage should return data");

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getData();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
        assertNotNull(items, "findPage should return items");
        assertFalse(items.isEmpty(), "test fixture should have created shares");

        for (Map<String, Object> item : items) {
            assertFalse(item.containsKey("passwordHash"),
                    "passwordHash must not appear in default findPage output: " + item.keySet());
        }
    }

    /**
     * get（继承 CRUD）经 GraphQL 出口时，passwordHash 不可选——
     * 与 findPage 同理，显式选择触发 undefined-field。
     */
    @Test
    public void testGetRejectsPasswordHashSelection() {
        IServiceContext ownerCtx = newOwnerContext("alice");
        NopDatavDashboard dash = saveDashboardOwnedBy("dash-mask-3", "mask-get", "alice");
        NopDatavDashboardShare share = shareBiz.createShare(
                dash.getDashboardId(), "pwdGet", null, ownerCtx);

        FieldSelectionBean selection = new FieldSelectionBean();
        selection.addField("shareId");
        selection.addField("passwordHash");

        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        Map<String, Object> data = new HashMap<>();
        data.put("id", share.getShareId());
        request.setData(data);
        request.setSelection(selection);

        NopException ex = assertThrows(NopException.class, () -> {
            IGraphQLExecutionContext context = graphQLEngine.newRpcContext(
                    GraphQLOperationType.query,
                    "NopDatavDashboardShare__get",
                    request);
            FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context));
        });
        assertEquals("nop.err.graphql.undefined-field", ex.getErrorCode(),
                "selecting passwordHash must be rejected on get: published=false removes it from schema");
    }

    // ==================== Helpers ====================

    private IServiceContext newOwnerContext(String userName) {
        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new TenantProxyContext(context.getContext()));
        if (userName != null) {
            context.getContext().setUserName(userName);
        }
        return context;
    }

    private NopDatavDashboard saveDashboardOwnedBy(String id, String name, String owner) {
        long now = System.currentTimeMillis();
        NopDatavDashboard d = new NopDatavDashboard();
        d.setDashboardId(id);
        d.setDashboardName(name);
        d.setDisplayName(name);
        d.setPublishStatus(0);
        d.setVersion(0L);
        d.setCreatedBy(owner);
        d.setCreateTime(new Timestamp(now));
        d.setUpdatedBy(owner);
        d.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavDashboard.class).saveEntityDirectly(d);
        return d;
    }
}
