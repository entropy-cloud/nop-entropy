package io.nop.datav.service.entity;

import io.nop.api.core.context.TenantProxyContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.biz.INopDatavDashboardBiz;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.service.filter.DashboardFilterUrlCodec;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_PARAM_TYPE_MISMATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全局筛选应用 API + URL 同步 测试（D2-1 Phase 2）。
 *
 * <p>通过注入 {@code INopDatavDashboardBiz} 代理调用 {@code resolveFilterValues} / {@code parseFilterFromUrl}，
 * 验证 Dashboard BizModel → 参数定义解析 → 校验/归一化 调用链在运行时连通（rule #23 接线验证，非 mock-only）。</p>
 */
public class TestNopDatavDashboardFilterApi extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    INopDatavDashboardBiz dashboardBiz;

    // ==================== resolveFilterValues (wiring + behavior) ====================

    /**
     * 接线验证：注入的 BizModel 代理调用 resolveFilterValues 返回经校验和默认值填充的结果，
     * 证明 Dashboard BizModel → DashboardParamParser → DashboardFilterResolver 调用链在运行时连通。
     */
    @Test
    public void testResolveFilterValuesFillsDefaultsViaBizProxy() {
        String paramConfig = JsonTool.stringify(List.of(
                Map.of("name", "region", "type", "string", "defaultValue", "all")
        ));
        NopDatavDashboard dashboard = saveDashboardWithParamConfig("dash-resolve", paramConfig);
        IServiceContext context = newContext("tester");

        Map<String, Object> out = dashboardBiz.resolveFilterValues(
                dashboard.getDashboardId(), Map.of(), context);

        assertEquals("all", out.get("region"), "default value should be filled");
    }

    @Test
    public void testResolveFilterValuesCompositeDateRangeProducesFlatKeys() {
        String paramConfig = JsonTool.stringify(List.of(
                Map.of("name", "dateRange", "type", "date-range",
                        "defaultValue", Map.of("start", "2024-01-01", "end", "2024-12-31"))
        ));
        NopDatavDashboard dashboard = saveDashboardWithParamConfig("dash-resolve-dr", paramConfig);
        IServiceContext context = newContext("tester");

        Map<String, Object> out = dashboardBiz.resolveFilterValues(dashboard.getDashboardId(),
                Map.of("dateRange.start", "2024-02-01", "dateRange.end", "2024-06-30"), context);

        assertEquals("2024-02-01", out.get("dateRange.start"));
        assertEquals("2024-06-30", out.get("dateRange.end"));
        assertFalse(out.containsKey("dateRange"));
    }

    @Test
    public void testResolveFilterValuesFiltersUnknownParams() {
        String paramConfig = JsonTool.stringify(List.of(
                Map.of("name", "region", "type", "string", "defaultValue", "all")
        ));
        NopDatavDashboard dashboard = saveDashboardWithParamConfig("dash-resolve-unknown", paramConfig);
        IServiceContext context = newContext("tester");

        Map<String, Object> out = dashboardBiz.resolveFilterValues(dashboard.getDashboardId(),
                Map.of("region", "East", "rogueKey", "shouldDrop"), context);

        assertTrue(out.containsKey("region"));
        assertFalse(out.containsKey("rogueKey"), "unknown params must be explicitly filtered");
    }

    @Test
    public void testResolveFilterValuesTypeMismatchThrows() {
        String paramConfig = JsonTool.stringify(List.of(
                Map.of("name", "limit", "type", "number")
        ));
        NopDatavDashboard dashboard = saveDashboardWithParamConfig("dash-resolve-typemismatch", paramConfig);
        IServiceContext context = newContext("tester");

        NopException ex = assertThrows(NopException.class,
                () -> dashboardBiz.resolveFilterValues(dashboard.getDashboardId(),
                        Map.of("limit", "not-a-number"), context));
        assertEquals(ERR_DATAV_PARAM_TYPE_MISMATCH.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testResolveFilterValuesEmptyParamConfigReturnsEmpty() {
        NopDatavDashboard dashboard = saveDashboardWithParamConfig("dash-resolve-empty", null);
        IServiceContext context = newContext("tester");

        Map<String, Object> out = dashboardBiz.resolveFilterValues(
                dashboard.getDashboardId(), Map.of("region", "East"), context);
        assertTrue(out.isEmpty(), "no param definitions → no resolved values");
    }

    // ==================== parseFilterFromUrl (wiring + behavior) ====================

    @Test
    public void testParseFilterFromUrlRoundTrip() {
        String paramConfig = JsonTool.stringify(List.of(
                Map.of("name", "region", "type", "string", "defaultValue", "all"),
                Map.of("name", "dateRange", "type", "date-range",
                        "defaultValue", Map.of("start", "2024-01-01", "end", "2024-12-31"))
        ));
        NopDatavDashboard dashboard = saveDashboardWithParamConfig("dash-url", paramConfig);
        IServiceContext context = newContext("tester");

        Map<String, Object> resolved = Map.of(
                "region", "East",
                "dateRange.start", "2024-03-01",
                "dateRange.end", "2024-06-30");
        String queryString = DashboardFilterUrlCodec.toQueryString(resolved);
        String url = "https://example.com/dashboard/" + dashboard.getDashboardId() + "?" + queryString;

        Map<String, Object> fromUrl = dashboardBiz.parseFilterFromUrl(
                dashboard.getDashboardId(), url, context);

        assertEquals("East", fromUrl.get("region"));
        assertEquals("2024-03-01", fromUrl.get("dateRange.start"));
        assertEquals("2024-06-30", fromUrl.get("dateRange.end"));
    }

    @Test
    public void testParseFilterFromUrlAppliesDefaultsForMissing() {
        String paramConfig = JsonTool.stringify(List.of(
                Map.of("name", "region", "type", "string", "defaultValue", "all"),
                Map.of("name", "dateRange", "type", "date-range",
                        "defaultValue", Map.of("start", "2024-01-01", "end", "2024-12-31"))
        ));
        NopDatavDashboard dashboard = saveDashboardWithParamConfig("dash-url-defaults", paramConfig);
        IServiceContext context = newContext("tester");

        // only region in URL; dateRange missing → defaults applied
        Map<String, Object> fromUrl = dashboardBiz.parseFilterFromUrl(
                dashboard.getDashboardId(), "region=East", context);

        assertEquals("East", fromUrl.get("region"));
        assertEquals("2024-01-01", fromUrl.get("dateRange.start"));
        assertEquals("2024-12-31", fromUrl.get("dateRange.end"));
    }

    // ==================== URL codec round-trip (utility level) ====================

    @Test
    public void testUrlCodecRoundTripPreservesValues() {
        Map<String, Object> original = new java.util.LinkedHashMap<>();
        original.put("region", "East & West");
        original.put("dateRange.start", "2024-01-01");
        original.put("dateRange.end", "2024-12-31");

        String queryString = DashboardFilterUrlCodec.toQueryString(original);
        Map<String, Object> roundTripped = DashboardFilterUrlCodec.parseQueryString(queryString);

        assertEquals("East & West", roundTripped.get("region"));
        assertEquals("2024-01-01", roundTripped.get("dateRange.start"));
        assertEquals("2024-12-31", roundTripped.get("dateRange.end"));
    }

    @Test
    public void testUrlCodecRoundTripWithFullUrl() {
        Map<String, Object> original = Map.of("region", "North", "limit", "100");
        String url = "https://host/dash/x?" + DashboardFilterUrlCodec.toQueryString(original);

        Map<String, Object> parsed = DashboardFilterUrlCodec.parseQueryString(url);
        assertEquals("North", parsed.get("region"));
        assertEquals("100", parsed.get("limit"));
    }

    // ==================== Helpers ====================

    private IServiceContext newContext(String userName) {
        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new TenantProxyContext(context.getContext()));
        context.getContext().setUserName(userName);
        return context;
    }

    private NopDatavDashboard saveDashboardWithParamConfig(String id, String paramConfig) {
        long now = System.currentTimeMillis();
        NopDatavDashboard d = new NopDatavDashboard();
        d.setDashboardId(id);
        d.setDashboardName(id);
        d.setDisplayName(id);
        d.setPublishStatus(0);
        d.setParamConfig(paramConfig);
        d.setVersion(0L);
        d.setCreatedBy("test");
        d.setCreateTime(new Timestamp(now));
        d.setUpdatedBy("test");
        d.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavDashboard.class).saveEntityDirectly(d);
        return d;
    }
}
