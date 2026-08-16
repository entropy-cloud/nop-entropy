package io.nop.datav.service.entity;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.biz.INopDatavDashboardBiz;
import io.nop.datav.dao.entity.NopDatavDashboard;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestNopDatavSmoke extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    INopDatavDashboardBiz dashboardBiz;

    @Test
    public void testIoCAndFindPage() {
        NopDatavDashboard dashboard = newDashboard("dash-smoke", "smoke-dashboard");
        daoProvider.daoFor(NopDatavDashboard.class).saveEntityDirectly(dashboard);

        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("dashboardId", "dash-smoke"));

        long count = daoProvider.daoFor(NopDatavDashboard.class).countByQuery(query);
        assertEquals(1, count, "Saved dashboard should be found via daoProvider");

        assertNotNull(dashboardBiz, "INopDatavDashboardBiz proxy should be injected (IoC pipeline connected)");
    }

    private NopDatavDashboard newDashboard(String id, String name) {
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
        return d;
    }
}
