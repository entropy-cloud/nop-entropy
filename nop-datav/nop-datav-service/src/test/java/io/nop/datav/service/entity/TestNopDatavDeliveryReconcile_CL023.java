package io.nop.datav.service.entity;

import io.nop.dao.api.IDaoProvider;
import io.nop.datav.dao.entity.NopDatavReportDelivery;
import io.nop.datav.service.report.NopDatavReportDeliveryRecovery;
import io.nop.datav.service.report.NopDatavReportDeliveryStatus;
import io.nop.datav.service.report.NopDatavReportTriggerSource;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CL-023 交付两段式 SMTP 半程窗口对账红⑭钉（plan 2526 裁定⑭，最简对账案）。
 *
 * <p>「SUCCEEDED 且 deliveredChannels 为空」= Part A 提交后 Part B 前进程死亡的事后唯一线索；
 * 旧实现 recovery 只处理非终态行、SUCCEEDED 明确跳过——半程行静默不可见（当前即红的缺陷态）。
 * 修复后启动对账输出结构化 WARN 清单 + 可断言命中计数。</p>
 */
public class TestNopDatavDeliveryReconcile_CL023 extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    NopDatavReportDeliveryRecovery deliveryRecovery;

    /**
     * 对账谓词精确性：SUCCEEDED+空渠道命中；SUCCEEDED+已记录渠道不命中；FAILED 不命中。
     */
    @Test
    public void testReconcileHitsOnlySucceededWithoutChannels() {
        long now = System.currentTimeMillis();
        seedDelivery("delivery-recon-halfway", NopDatavReportDeliveryStatus.SUCCEEDED, null, now);
        seedDelivery("delivery-recon-ok", NopDatavReportDeliveryStatus.SUCCEEDED, "email", now);
        seedDelivery("delivery-recon-failed", NopDatavReportDeliveryStatus.FAILED, null, now);

        int hits = deliveryRecovery.reconcileSucceededWithoutChannels();

        assertEquals(1, hits,
                "exactly the SUCCEEDED-without-channels row is listed (half-way window candidate)");
    }

    /**
     * 对账触发点接线：recoverInterruptedDeliveries（启动恢复入口）必须级联触发对账
     * （触发点=启动 recovery——崩溃窗口的物理成因是进程死亡，重启必经）。
     */
    @Test
    public void testRecoveryEntryTriggersReconciliation() {
        long now = System.currentTimeMillis();
        seedDelivery("delivery-recon-entry", NopDatavReportDeliveryStatus.SUCCEEDED, null, now);

        deliveryRecovery.recoverInterruptedDeliveries();

        int hits = deliveryRecovery.reconcileSucceededWithoutChannels();
        assertEquals(1, hits, "half-way row still listed after recovery entry (reconciliation wired)");
        assertEquals(NopDatavReportDeliveryStatus.SUCCEEDED,
                deliveryRow("delivery-recon-entry").getStatus(),
                "reconciliation must not auto-rewrite terminal rows (WARN-only)");
    }

    private void seedDelivery(String deliveryId, int status, String deliveredChannels, long startTimeMillis) {
        NopDatavReportDelivery d = new NopDatavReportDelivery();
        d.setDeliveryId(deliveryId);
        d.setReportTaskId("task-recon");
        d.setStatus(status);
        d.setDeliveredChannels(deliveredChannels);
        d.setTriggeredBy(NopDatavReportTriggerSource.SCHEDULE);
        d.setStartTime(new Timestamp(startTimeMillis));
        d.setDelFlag((byte) 0);
        d.setVersion(0L);
        d.setCreatedBy("alice");
        d.setCreateTime(new Timestamp(startTimeMillis));
        d.setUpdatedBy("alice");
        d.setUpdateTime(new Timestamp(startTimeMillis));
        daoProvider.daoFor(NopDatavReportDelivery.class).saveEntityDirectly(d);
    }

    private NopDatavReportDelivery deliveryRow(String deliveryId) {
        return daoProvider.daoFor(NopDatavReportDelivery.class).getEntityById(deliveryId);
    }
}
