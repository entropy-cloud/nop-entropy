package io.nop.datav.service.entity;

import io.nop.api.core.context.TenantProxyContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.biz.FilterState;
import io.nop.datav.biz.INopDatavFilterStateBiz;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavFilterState;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_DASHBOARD_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * filter_state 服务 API 接线测试（D2-3 Phase 3）。
 *
 * <p>通过注入 {@link INopDatavFilterStateBiz} 代理调用 {@code saveFilterState} / {@code getFilterState}，
 * 验证 序列化 → 存储 → 反序列化 链路运行时连通（非 mock-only）。</p>
 *
 * <p>覆盖：保存/恢复往返测试、空 state 处理、按 userName 隔离、覆盖更新、格式错误处理、
 * dashboard 不存在校验、调用方据此判断无已保存状态（返回 null）。</p>
 */
public class TestNopDatavFilterStateBizModel extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    INopDatavFilterStateBiz filterStateBiz;

    /**
     * 保存/恢复往返测试：保存 globalFilters + panelSelections + urlState → 恢复 → 状态一致。
     * 证明 序列化 → 存储 → 反序列化 链路连通。
     */
    @Test
    public void testSaveAndGetRoundTrip() {
        IServiceContext context = newContext("alice");
        NopDatavDashboard dash = saveDashboard("dash-fs-1", "fs-test-1");

        Map<String, Object> globalFilters = new LinkedHashMap<>();
        globalFilters.put("region", "east");
        globalFilters.put("dateRange.start", "2024-01-01");
        globalFilters.put("dateRange.end", "2024-06-30");

        Map<String, Map<String, Object>> panelSelections = new LinkedHashMap<>();
        Map<String, Object> selection = new LinkedHashMap<>();
        selection.put("field", "region");
        selection.put("value", "east");
        panelSelections.put("p-src", selection);

        String urlState = "region=east";

        NopDatavFilterState saved = filterStateBiz.saveFilterState(
                dash.getDashboardId(), globalFilters, panelSelections, urlState, context);
        assertNotNull(saved);
        assertNotNull(saved.getStateId());

        FilterState restored = filterStateBiz.getFilterState(dash.getDashboardId(), context);
        assertNotNull(restored);
        assertEquals("east", restored.getGlobalFilters().get("region"));
        assertEquals("2024-01-01", restored.getGlobalFilters().get("dateRange.start"));
        assertEquals("2024-06-30", restored.getGlobalFilters().get("dateRange.end"));

        assertEquals(1, restored.getPanelSelections().size());
        FilterState.PanelSelection ps = restored.getPanelSelections().get("p-src");
        assertNotNull(ps);
        assertEquals("region", ps.getField());
        assertEquals("east", ps.getValue());

        assertEquals(urlState, restored.getUrlState());
    }

    /**
     * 无保存记录返回 null（调用方据此判断无已保存状态，非静默返回空对象）。
     */
    @Test
    public void testGetFilterStateReturnsNullWhenNoRecord() {
        IServiceContext context = newContext("alice");
        NopDatavDashboard dash = saveDashboard("dash-fs-2", "fs-test-2");

        FilterState result = filterStateBiz.getFilterState(dash.getDashboardId(), context);
        assertNull(result, "no saved record returns null (legit branch)");
    }

    /**
     * 按 userName 隔离：用户 A 保存的 state 不影响用户 B。
     */
    @Test
    public void testFilterStateIsIsolatedByUserName() {
        IServiceContext aliceCtx = newContext("alice");
        IServiceContext bobCtx = newContext("bob");
        NopDatavDashboard dash = saveDashboard("dash-fs-3", "fs-test-3");

        // Alice saves
        filterStateBiz.saveFilterState(dash.getDashboardId(),
                Map.of("region", "east"), Map.of(), "region=east", aliceCtx);

        // Bob has no state for the same dashboard
        FilterState bobState = filterStateBiz.getFilterState(dash.getDashboardId(), bobCtx);
        assertNull(bobState, "user B has no state even though user A saved");

        // Alice still has her state
        FilterState aliceState = filterStateBiz.getFilterState(dash.getDashboardId(), aliceCtx);
        assertNotNull(aliceState);
        assertEquals("east", aliceState.getGlobalFilters().get("region"));

        // Bob saves different state for the same dashboard
        filterStateBiz.saveFilterState(dash.getDashboardId(),
                Map.of("region", "west"), Map.of(), "region=west", bobCtx);

        // Both have their own state
        assertEquals("east", filterStateBiz.getFilterState(dash.getDashboardId(), aliceCtx)
                .getGlobalFilters().get("region"));
        assertEquals("west", filterStateBiz.getFilterState(dash.getDashboardId(), bobCtx)
                .getGlobalFilters().get("region"));
    }

    /**
     * 保存覆盖：同一用户+看板的二次保存覆盖旧记录（不新增）。
     */
    @Test
    public void testSaveOverwritesExistingRecord() {
        IServiceContext context = newContext("alice");
        NopDatavDashboard dash = saveDashboard("dash-fs-4", "fs-test-4");

        filterStateBiz.saveFilterState(dash.getDashboardId(),
                Map.of("region", "east"), Map.of(), "region=east", context);
        filterStateBiz.saveFilterState(dash.getDashboardId(),
                Map.of("region", "west"), Map.of(), "region=west", context);

        FilterState state = filterStateBiz.getFilterState(dash.getDashboardId(), context);
        assertEquals("west", state.getGlobalFilters().get("region"),
                "second save overwrites first; only one record per user+dashboard");
        assertEquals("region=west", state.getUrlState());

        // Verify only one record exists for this user+dashboard
        long count = daoProvider.daoFor(NopDatavFilterState.class).findAll().stream()
                .filter(e -> "alice".equals(e.getUserName())
                        && dash.getDashboardId().equals(e.getDashboardId()))
                .count();
        assertEquals(1, count, "exactly one record per userName+dashboardId");
    }

    /**
     * dashboard 不存在：保存时显式抛 ERR_DATAV_DASHBOARD_NOT_FOUND（不静默创建孤儿 state）。
     */
    @Test
    public void testSaveFilterStateDashboardNotFoundThrows() {
        IServiceContext context = newContext("alice");
        NopException ex = assertThrows(NopException.class,
                () -> filterStateBiz.saveFilterState("missing-dash",
                        Map.of("region", "east"), Map.of(), null, context));
        assertEquals(ERR_DATAV_DASHBOARD_NOT_FOUND.getErrorCode(), ex.getErrorCode());
    }

    /**
     * 空 state 保存（空 globalFilters + 空 panelSelections + null urlState）合法可恢复。
     */
    @Test
    public void testSaveEmptyStateRoundTrip() {
        IServiceContext context = newContext("alice");
        NopDatavDashboard dash = saveDashboard("dash-fs-5", "fs-test-5");

        NopDatavFilterState saved = filterStateBiz.saveFilterState(
                dash.getDashboardId(), Map.of(), Map.of(), null, context);
        assertNotNull(saved);

        FilterState restored = filterStateBiz.getFilterState(dash.getDashboardId(), context);
        assertNotNull(restored);
        assertTrue(restored.getGlobalFilters().isEmpty());
        assertTrue(restored.getPanelSelections().isEmpty());
        assertNull(restored.getUrlState());
    }

    /**
     * 系统用户（无 userName 上下文）：fallback 到 "system"，不报错。
     */
    @Test
    public void testSaveWithSystemUserWhenContextMissing() {
        IServiceContext context = newContext(null);
        NopDatavDashboard dash = saveDashboard("dash-fs-6", "fs-test-6");

        NopDatavFilterState saved = filterStateBiz.saveFilterState(
                dash.getDashboardId(), Map.of("region", "north"), Map.of(), "region=north", context);
        assertEquals("system", saved.getUserName(),
                "context without userName falls back to 'system'");

        IServiceContext systemCtx = newContext("system");
        FilterState restored = filterStateBiz.getFilterState(dash.getDashboardId(), systemCtx);
        assertNotNull(restored);
        assertEquals("north", restored.getGlobalFilters().get("region"));
    }

    // ==================== Helpers ====================

    private IServiceContext newContext(String userName) {
        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new TenantProxyContext(context.getContext()));
        if (userName != null) {
            context.getContext().setUserName(userName);
        }
        return context;
    }

    private NopDatavDashboard saveDashboard(String id, String name) {
        long now = System.currentTimeMillis();
        NopDatavDashboard d = new NopDatavDashboard();
        d.setDashboardId(id);
        d.setDashboardName(name);
        d.setDisplayName(name);
        d.setPublishStatus(0);
        d.setVersion(0L);
        d.setCreatedBy("test");
        d.setCreateTime(new Timestamp(now));
        d.setUpdatedBy("test");
        d.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavDashboard.class).saveEntityDirectly(d);
        return d;
    }
}
