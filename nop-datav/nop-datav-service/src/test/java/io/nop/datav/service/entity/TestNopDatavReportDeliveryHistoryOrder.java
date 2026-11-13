package io.nop.datav.service.entity;

import io.nop.api.core.context.TenantProxyContext;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavReportDelivery;
import io.nop.datav.dao.entity.NopDatavReportTask;
import io.nop.datav.service.report.NopDatavReportDeliveryStatus;
import io.nop.datav.service.report.NopDatavReportTaskStatus;
import io.nop.datav.service.report.NopDatavReportTriggerSource;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code getReportDeliveryHistory} 排序回归测试：契约（{@code INopDatavReportTaskBiz}）
 * 要求按 startTime 倒序（最近一次交付在前）。
 */
public class TestNopDatavReportDeliveryHistoryOrder extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Test
    public void testHistoryOrdersByStartTimeDesc() {
        String dashboardId = seedDashboard("dash-history", "alice");
        seedTask("task-history", dashboardId, "alice");
        seedDelivery("delivery-older", "task-history", 1_000_000_000_000L);
        seedDelivery("delivery-newer", "task-history", 2_000_000_000_000L);

        NopDatavReportTaskBizModel bizModel = new NopDatavReportTaskBizModel();
        bizModel.setDaoProvider(daoProvider);

        List<NopDatavReportDelivery> history =
                bizModel.getReportDeliveryHistory("task-history", context("alice"));

        assertEquals(2, history.size());
        assertEquals("delivery-newer", history.get(0).getDeliveryId(),
                "startTime 倒序：最近一次交付在前");
        assertEquals("delivery-older", history.get(1).getDeliveryId());
    }

    private String seedDashboard(String name, String owner) {
        long now = System.currentTimeMillis();
        NopDatavDashboard dashboard = new NopDatavDashboard();
        dashboard.setDashboardName(name);
        dashboard.setDisplayName(name);
        dashboard.setPublishStatus(0);
        dashboard.setVersion(0L);
        dashboard.setCreatedBy(owner);
        dashboard.setCreateTime(new Timestamp(now));
        dashboard.setUpdatedBy(owner);
        dashboard.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavDashboard.class).saveEntityDirectly(dashboard);
        return dashboard.getDashboardId();
    }

    private void seedTask(String taskId, String dashboardId, String owner) {
        long now = System.currentTimeMillis();
        NopDatavReportTask task = new NopDatavReportTask();
        task.setReportTaskId(taskId);
        task.setTaskName(taskId + "-name");
        task.setDisplayName(taskId + "-display");
        task.setDashboardId(dashboardId);
        task.setCronExpr("0 0 0 * * ?");
        task.setFormat("xlsx");
        task.setRecipients("[\"a@example.com\"]");
        task.setNotifyChannels("[\"email\"]");
        task.setStatus(NopDatavReportTaskStatus.DISABLED);
        task.setGraceMinutes(60);
        task.setDelFlag((byte) 0);
        task.setVersion(0L);
        task.setCreatedBy(owner);
        task.setCreateTime(new Timestamp(now));
        task.setUpdatedBy(owner);
        task.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavReportTask.class).saveEntityDirectly(task);
    }

    private void seedDelivery(String deliveryId, String reportTaskId, long startTimeMillis) {
        long now = System.currentTimeMillis();
        NopDatavReportDelivery delivery = new NopDatavReportDelivery();
        delivery.setDeliveryId(deliveryId);
        delivery.setReportTaskId(reportTaskId);
        delivery.setStatus(NopDatavReportDeliveryStatus.SUCCEEDED);
        delivery.setTriggeredBy(NopDatavReportTriggerSource.SCHEDULE);
        delivery.setStartTime(new Timestamp(startTimeMillis));
        delivery.setDelFlag((byte) 0);
        delivery.setVersion(0L);
        delivery.setCreatedBy("alice");
        delivery.setCreateTime(new Timestamp(now));
        delivery.setUpdatedBy("alice");
        delivery.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavReportDelivery.class).saveEntityDirectly(delivery);
    }

    private IServiceContext context(String userName) {
        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new TenantProxyContext(context.getContext()));
        context.getContext().setUserName(userName);
        return context;
    }
}
