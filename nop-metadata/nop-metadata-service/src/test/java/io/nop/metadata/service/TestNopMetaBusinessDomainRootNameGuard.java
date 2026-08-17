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
import io.nop.metadata.dao.entity.NopMetaBusinessDomain;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2-28 守卫回归测试（plan 2026-08-16-0920-1）：
 *
 * <p>{@code UK_NOP_META_BUSINESS_DOMAIN_PARENT_NAME (parentDomainId,name)} 对根域
 * （parentDomainId NULL）不生效（复合 UK 任一列 NULL 即豁免唯一性），根域重名失守。
 * 应用层守卫（save + update 双入口）按 {@code (parentDomainId IS NULL, name)} 查重，
 * 命中 fail-loud（nop.err.metadata.business-domain-duplicate-root-name），事务回滚零残留。
 *
 * <p>区分性断言（Anti-Hollow）：守卫只拦根域重名——
 * <ul>
 *   <li>不同名根域共存合法；不同父域下同名子域合法（per-parent 语义）；</li>
 *   <li>同父域下同名子域继续由 DB UK 拒绝（既有路径不回归）；</li>
 *   <li>save 带自身 id 的幂等更新/改名不自误伤（自排除）。</li>
 * </ul>
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaBusinessDomainRootNameGuard extends JunitBaseTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    private final Timestamp now = new Timestamp(System.currentTimeMillis());

    @Test
    public void testDuplicateRootDomainNameRejected() {
        GraphQLResponseBean first = saveDomainViaGql(domainData("bd-p228-1", null, "Finance"));
        assertFalse(first.hasError(), "first root domain must save: " + first);

        // 根域重名 → 守卫 fail-loud（修复前该 save 成功、重名根域落库）
        GraphQLResponseBean dup = saveDomainViaGql(domainData("bd-p228-2", null, "Finance"));
        assertTrue(dup.hasError(), "duplicate root domain name must be rejected");
        assertTrue(dup.getErrors().get(0).getMessage().contains("Duplicate root business domain name"),
                "must fail with ERR_BUSINESS_DOMAIN_DUPLICATE_ROOT_NAME description: " + dup.getErrors().get(0));

        assertEquals(1, countRootDomainsNamed("Finance"),
                "exactly one Finance root may land (guard throw rolls back the duplicate)");
    }

    @Test
    public void testDifferentRootNamesAndSameNameUnderDifferentParentsAllowed() {
        assertFalse(saveDomainViaGql(domainData("bd-p228-10", null, "Finance")).hasError(), "root Finance must save");
        assertFalse(saveDomainViaGql(domainData("bd-p228-11", null, "Sales")).hasError(),
                "different root name must save (no false positive)");

        // 同名子域挂不同父域 → 合法（per-parent 语义，守卫不误伤）
        assertFalse(saveDomainViaGql(domainData("bd-p228-12", "bd-p228-10", "Report")).hasError(),
                "child Report under Finance must save");
        assertFalse(saveDomainViaGql(domainData("bd-p228-13", "bd-p228-11", "Report")).hasError(),
                "child Report under Sales must save (same name different parent is legal)");
        assertEquals(2, countDomainsNamed("Report"), "both Report children must land");
    }

    @Test
    public void testSiblingDuplicateUnderSameParentStillDbUkGuarded() {
        assertFalse(saveDomainViaGql(domainData("bd-p228-20", null, "Risk")).hasError(), "root Risk must save");
        assertFalse(saveDomainViaGql(domainData("bd-p228-21", "bd-p228-20", "Market")).hasError(),
                "first child Market must save");

        // 同父域下同名子域 → DB UK 拒绝（既有保护面，守卫不接管、不放松）
        GraphQLResponseBean dup = saveDomainViaGql(domainData("bd-p228-22", "bd-p228-20", "Market"));
        assertTrue(dup.hasError(), "duplicate child under same parent must still be rejected by DB UK");
        assertEquals(1, countChildrenNamed("bd-p228-20", "Market"), "exactly one Market child may land");
    }

    @Test
    public void testDuplicateViaUpdateMutationRejectedAndSelfUpdateAllowed() {
        assertFalse(saveDomainViaGql(domainData("bd-p228-30", null, "Finance")).hasError(), "root Finance must save");
        assertFalse(saveDomainViaGql(domainData("bd-p228-31", null, "Sales")).hasError(), "root Sales must save");

        // update 把 Sales 根改名为 Finance → 撞既有根域名，守卫拒绝（对称写面）
        Map<String, Object> rename = new HashMap<>();
        rename.put("id", "bd-p228-31");
        rename.put("name", "Finance");
        GraphQLResponseBean resp = updateDomainViaGql(rename);
        assertTrue(resp.hasError(), "root rename into existing root name must be rejected");
        assertTrue(resp.getErrors().get(0).getMessage().contains("Duplicate root business domain name"),
                "must fail with guard error: " + resp.getErrors().get(0));
        assertEquals(1, countRootDomainsNamed("Finance"), "still exactly one Finance root (update rolled back)");

        // 幂等自更新（id 指向自身，名不变）→ 不自误伤（自排除）
        Map<String, Object> selfData = domainData("bd-p228-30", null, "Finance");
        selfData.put("id", "bd-p228-30");
        GraphQLResponseBean self = updateDomainViaGql(selfData);
        assertFalse(self.hasError(), "idempotent self-update must not trip the guard: " + self);
    }

    private Map<String, Object> domainData(String businessDomainId, String parentDomainId, String name) {
        Map<String, Object> data = new HashMap<>();
        data.put("businessDomainId", businessDomainId);
        if (parentDomainId != null) {
            data.put("parentDomainId", parentDomainId);
        }
        data.put("name", name);
        data.put("displayName", name);
        data.put("version", 1);
        return data;
    }

    private int countRootDomainsNamed(String name) {
        IEntityDao<NopMetaBusinessDomain> dao = daoProvider.daoFor(NopMetaBusinessDomain.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.isNull(NopMetaBusinessDomain.PROP_NAME_parentDomainId));
        q.addFilter(FilterBeans.eq(NopMetaBusinessDomain.PROP_NAME_name, name));
        return dao.findAllByQuery(q).size();
    }

    private int countDomainsNamed(String name) {
        IEntityDao<NopMetaBusinessDomain> dao = daoProvider.daoFor(NopMetaBusinessDomain.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaBusinessDomain.PROP_NAME_name, name));
        return dao.findAllByQuery(q).size();
    }

    private int countChildrenNamed(String parentDomainId, String name) {
        IEntityDao<NopMetaBusinessDomain> dao = daoProvider.daoFor(NopMetaBusinessDomain.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaBusinessDomain.PROP_NAME_parentDomainId, parentDomainId));
        q.addFilter(FilterBeans.eq(NopMetaBusinessDomain.PROP_NAME_name, name));
        return dao.findAllByQuery(q).size();
    }

    private GraphQLResponseBean saveDomainViaGql(Map<String, Object> data) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("data", data);
        return executeWithVars(
                "mutation($data:Map) { NopMetaBusinessDomain__save(data:$data) { businessDomainId name parentDomainId } }",
                vars);
    }

    private GraphQLResponseBean updateDomainViaGql(Map<String, Object> data) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("data", data);
        return executeWithVars(
                "mutation($data:Map) { NopMetaBusinessDomain__update(data:$data) { businessDomainId name parentDomainId } }",
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
