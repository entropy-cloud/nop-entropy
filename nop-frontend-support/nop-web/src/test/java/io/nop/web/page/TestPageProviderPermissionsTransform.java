package io.nop.web.page;

import io.nop.api.core.auth.IRolePermissionMapping;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.json.utils.JsonTransformHelper;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.web.WebConstants;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-4.5-B1 (plan 2404): the permissions → roles conversion must be copy-on-write
 * (never mutating the cached PageModel data) and must also convert nested matches
 * under a matched map (JsonTransformHelper short-circuits recursion at a match).
 */
public class TestPageProviderPermissionsTransform extends JunitBaseTestCase {

    private static final String PAGE_PATH = "/nop/test/pages/test-permissions.page.yaml";
    private static final String LOCALE = "zh-CN";

    private PageProvider providerWithRoles(Set<String> roles) {
        PageProvider provider = new PageProvider();
        provider.setRolePermissionMapping(permissions -> roles);
        return provider;
    }

    private Map<String, Object> node(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    @Test
    void transformIsCopyOnWriteAndConvertsNestedMatches() {
        PageProvider provider = providerWithRoles(Set.of("roleA"));

        Map<String, Object> grand = node(WebConstants.ATTR_XUI_PERMISSIONS, "p1");
        Map<String, Object> matchedChild = node(WebConstants.ATTR_XUI_PERMISSIONS, "p1",
                "children", List.of(grand));
        Map<String, Object> plainSibling = node("component", "span");
        Map<String, Object> root = node(WebConstants.ATTR_XUI_PERMISSIONS, "p1",
                "children", List.of(matchedChild, plainSibling));

        Map<String, Object> result = (Map<String, Object>) JsonTransformHelper.transform(
                root, provider::transformPermissions, provider::hasXuiAuth);

        // converted: no permissions, roles resolved, at every matched level
        assertNull(result.get(WebConstants.ATTR_XUI_PERMISSIONS));
        assertEquals("roleA", result.get(WebConstants.ATTR_XUI_ROLES));

        Map<String, Object> resultChild = (Map<String, Object>) ((List<?>) result.get("children")).get(0);
        assertNull(resultChild.get(WebConstants.ATTR_XUI_PERMISSIONS));
        assertEquals("roleA", resultChild.get(WebConstants.ATTR_XUI_ROLES));
        Map<String, Object> resultGrand = (Map<String, Object>) ((List<?>) resultChild.get("children")).get(0);
        assertNull(resultGrand.get(WebConstants.ATTR_XUI_PERMISSIONS),
                "nested match under a matched map must be converted too");
        assertEquals("roleA", resultGrand.get(WebConstants.ATTR_XUI_ROLES));

        // source tree (and therefore any cached model) is untouched
        assertEquals("p1", root.get(WebConstants.ATTR_XUI_PERMISSIONS),
                "the incoming map must not be mutated (copy-on-write)");
        assertFalse(root.containsKey(WebConstants.ATTR_XUI_ROLES));
        assertEquals("p1", matchedChild.get(WebConstants.ATTR_XUI_PERMISSIONS));
        assertFalse(matchedChild.containsKey(WebConstants.ATTR_XUI_ROLES));
        assertEquals("p1", grand.get(WebConstants.ATTR_XUI_PERMISSIONS));
        assertFalse(grand.containsKey(WebConstants.ATTR_XUI_ROLES));
    }

    @Test
    void noMatchingRoleFallsBackToNone() {
        PageProvider provider = providerWithRoles(Set.of());
        Map<String, Object> map = node(WebConstants.ATTR_XUI_PERMISSIONS, "p1");

        Map<String, Object> result = (Map<String, Object>) provider.transformPermissions(map);

        assertEquals("none", result.get(WebConstants.ATTR_XUI_ROLES));
        assertEquals("p1", map.get(WebConstants.ATTR_XUI_PERMISSIONS));
    }

    @Test
    void existingRolesAreMergedWithMappedRoles() {
        PageProvider provider = providerWithRoles(Set.of("roleA"));
        Map<String, Object> map = node(WebConstants.ATTR_XUI_PERMISSIONS, "p1",
                WebConstants.ATTR_XUI_ROLES, "existingRole");

        Map<String, Object> result = (Map<String, Object>) provider.transformPermissions(map);

        String roles = (String) result.get(WebConstants.ATTR_XUI_ROLES);
        assertTrue(roles.contains("roleA") && roles.contains("existingRole"),
                "mapped roles must merge with existing roles, got: " + roles);
        assertEquals("existingRole", map.get(WebConstants.ATTR_XUI_ROLES));
    }

    @Test
    void getPageDoesNotMutateCachedPageModel() {
        PageProvider provider = providerWithRoles(Set.of("roleA"));

        Map<String, Object> first = provider.getPage(PAGE_PATH, LOCALE);
        assertNull(first.get(WebConstants.ATTR_XUI_PERMISSIONS));
        assertTrue(((String) first.get(WebConstants.ATTR_XUI_ROLES)).contains("roleA"));

        // cached PageModel must still carry xui:permissions (pre-fix it was removed in place)
        PageModel cached = (PageModel) ResourceComponentManager.instance()
                .loadComponentModel(LOCALE + "|" + PAGE_PATH);
        assertNotNull(cached);
        assertNotNull(cached.getData().get(WebConstants.ATTR_XUI_PERMISSIONS),
                "cached PageModel data must not be mutated by getPage");
        assertFalse(cached.getData().containsKey(WebConstants.ATTR_XUI_ROLES));

        Map<String, Object> second = provider.getPage(PAGE_PATH, LOCALE);
        assertEquals("roleA", ((String) second.get(WebConstants.ATTR_XUI_ROLES)).split(",")[0]);
    }

    @Test
    void concurrentGetPageNeverMutatesCachedPageModel() throws Exception {
        PageProvider provider = providerWithRoles(Set.of("roleA"));
        // warm the cache once so all threads exercise the transform on the same cached instance
        provider.getPage(PAGE_PATH, LOCALE);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Map<String, Object>>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return provider.getPage(PAGE_PATH, LOCALE);
                }));
            }
            start.countDown();

            for (Future<Map<String, Object>> future : futures) {
                Map<String, Object> result = future.get();
                assertNull(result.get(WebConstants.ATTR_XUI_PERMISSIONS));
                // best-effort: a thread observing the pre-fix intermediate state may miss roles,
                // so the deterministic assertion below is the cache-isolation one.
                assertNotNull(result.get(WebConstants.ATTR_XUI_ROLES));
            }
        } finally {
            pool.shutdownNow();
        }

        PageModel cached = (PageModel) ResourceComponentManager.instance()
                .loadComponentModel(LOCALE + "|" + PAGE_PATH);
        assertNotNull(cached.getData().get(WebConstants.ATTR_XUI_PERMISSIONS),
                "concurrent getPage must not mutate the cached PageModel");
        assertFalse(cached.getData().containsKey(WebConstants.ATTR_XUI_ROLES));
    }
}
