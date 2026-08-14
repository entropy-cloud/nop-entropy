package io.nop.datav.service.report;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.orm.IOrmSession;
import io.nop.orm.IOrmTemplate;
import io.nop.datav.dao.entity.NopDatavReportDelivery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;

/**
 * 进程重启后清理中断的报告交付记录（D5-1）。
 *
 * <p>JVM 重启后，所有 status ∈ {pending, running} 的交付记录的执行体（{@code CompletableFuture}）已丢失。
 * 本类在 IoC 启动后（{@link #recoverInterruptedDeliveries()}）将这些记录标记为 failed，
 * 附 reason="interrupted by process restart"，不静默挂起。</p>
 *
 * <p>启动时（{@link PostConstruct}）查询前先经 {@link IJdbcTemplate#existsTable} 判定表是否已建，
 * 避免在测试环境 schema 尚未建表阶段（容器 init 早于建表）误报。方法幂等。</p>
 *
 * <p><b>职责分离</b>：本 bean 负责 stale running 交付记录的两类清理——重启恢复
 * （{@link #recoverInterruptedDeliveries()}，无阈值全量）与周期 stuck 扫描
 * （{@link #scanStuck(int)}，带时间阈值，由 {@code NopDatavStuckTaskScanner} 周期触发）；
 * cron job 重注册由独立 {@link NopDatavReportScheduler} 负责（镜像
 * {@code NopDatavExportTaskRecovery} 独立于 BizModel 的模式）。</p>
 */
public class NopDatavReportDeliveryRecovery {

    private static final Logger LOG = LoggerFactory.getLogger(NopDatavReportDeliveryRecovery.class);

    public static final String RESTART_REASON = "interrupted by process restart";

    /** stuck 扫描标记 reason 前缀（完整 reason 为 {@code stuck beyond timeout threshold (Xm)}） */
    public static final String STUCK_REASON_PREFIX = "stuck beyond timeout threshold";

    private final IDaoProvider daoProvider;
    private final IOrmTemplate ormTemplate;
    private final IJdbcTemplate jdbcTemplate;

    @Inject
    public NopDatavReportDeliveryRecovery(IDaoProvider daoProvider, IOrmTemplate ormTemplate,
                                          IJdbcTemplate jdbcTemplate) {
        this.daoProvider = daoProvider;
        this.ormTemplate = ormTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        try {
            recoverInterruptedDeliveries();
        } catch (Exception e) {
            LOG.warn("nop.datav.report.recovery-init-fail", e);
        }
    }

    /**
     * 扫描所有 status ∈ {pending, running} 的交付记录，标记为 failed（reason=interrupted by process restart）。
     * 幂等：仅影响非终态记录。
     */
    public void recoverInterruptedDeliveries() {
        if (!deliveryTableExists()) {
            return;
        }
        ormTemplate.runInNewSession(this::doRecover);
    }

    private boolean deliveryTableExists() {
        try {
            return jdbcTemplate.existsTable(null, "nop_datav_report_delivery");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 周期 stuck 扫描（与重启恢复正交）：仅标记 status ∈ {pending, running} 且
     * {@code startTime < now − timeoutMinutes} 的交付记录为 failed
     * （reason="stuck beyond timeout threshold (Xm)"）。阈值内正常在途记录不动。
     *
     * <p>与 {@link #recoverInterruptedDeliveries()} 的区别：后者仅在进程重启时全量清理
     * （无时间阈值），本方法由 {@code NopDatavStuckTaskScanner} 周期调用且带时间阈值，
     * 不会误杀阈值内的在途记录（Dim14-01 per-request 误杀裁定的周期版安全前提）。
     * 幂等：仅影响非终态记录。表不存在时安全跳过（返回 0）。</p>
     *
     * @param timeoutMinutes stuck 判定阈值（分钟）
     * @return 本次被标记为 failed 的记录数
     */
    public int scanStuck(int timeoutMinutes) {
        if (!deliveryTableExists()) {
            LOG.info("nop.datav.report.stuck-scan.table-not-exists: skip scan (table NOP_DATAV_REPORT_DELIVERY not yet created)");
            return 0;
        }
        Timestamp cutoff = new Timestamp(System.currentTimeMillis() - timeoutMinutes * 60_000L);
        return ormTemplate.runInNewSession(session -> doScanStuck(session, cutoff, timeoutMinutes));
    }

    private int doScanStuck(IOrmSession session, Timestamp cutoff, int timeoutMinutes) {
        IEntityDao<NopDatavReportDelivery> dao = daoProvider.daoFor(NopDatavReportDelivery.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.in("status", Arrays.asList(
                NopDatavReportDeliveryStatus.PENDING, NopDatavReportDeliveryStatus.RUNNING)));
        query.addFilter(FilterBeans.lt("startTime", cutoff));
        @SuppressWarnings("unchecked")
        List<NopDatavReportDelivery> stale = (List<NopDatavReportDelivery>) dao.findAllByQuery(query);
        if (stale.isEmpty()) {
            return 0;
        }
        Timestamp now = new Timestamp(System.currentTimeMillis());
        String reason = STUCK_REASON_PREFIX + " (" + timeoutMinutes + "m)";
        for (NopDatavReportDelivery delivery : stale) {
            delivery.setStatus(NopDatavReportDeliveryStatus.FAILED);
            delivery.setErrorMsg(reason);
            delivery.setEndTime(now);
            delivery.setUpdatedBy("system");
            delivery.setUpdateTime(now);
            dao.updateEntityDirectly(delivery);
        }
        LOG.info("nop.datav.report.stuck-scan-marked:count={} timeoutMinutes={}", stale.size(), timeoutMinutes);
        return stale.size();
    }

    private Void doRecover(IOrmSession session) {
        IEntityDao<NopDatavReportDelivery> dao = daoProvider.daoFor(NopDatavReportDelivery.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.in("status", Arrays.asList(
                NopDatavReportDeliveryStatus.PENDING, NopDatavReportDeliveryStatus.RUNNING)));
        @SuppressWarnings("unchecked")
        List<NopDatavReportDelivery> stale = (List<NopDatavReportDelivery>) dao.findAllByQuery(query);
        if (stale.isEmpty()) {
            return null;
        }
        Timestamp now = new Timestamp(System.currentTimeMillis());
        for (NopDatavReportDelivery delivery : stale) {
            delivery.setStatus(NopDatavReportDeliveryStatus.FAILED);
            delivery.setErrorMsg(RESTART_REASON);
            delivery.setEndTime(now);
            delivery.setUpdatedBy("system");
            delivery.setUpdateTime(now);
            dao.updateEntityDirectly(delivery);
        }
        LOG.info("nop.datav.report.recovered-deliveries:count={}", stale.size());
        return null;
    }
}
