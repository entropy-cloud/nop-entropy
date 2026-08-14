package io.nop.datav.service.recovery;

import io.nop.datav.service.export.NopDatavExportTaskRecovery;
import io.nop.datav.service.report.NopDatavReportDeliveryRecovery;
import io.nop.job.api.IJobScheduler;
import io.nop.job.api.spec.JobSpec;
import io.nop.job.api.spec.TriggerSpec;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_STUCK_SCAN_ENABLED;
import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_STUCK_SCAN_INTERVAL_MINUTES;
import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_STUCK_SCAN_TIMEOUT_MINUTES;

/**
 * stuck-task 周期恢复扫描器。
 *
 * <p>把「仅进程重启（{@code @PostConstruct}）时清理中断记录」的恢复机制扩展为「周期扫描 + 时间阈值」：
 * 进程存活但 worker 线程因 JVM Error 死亡（throwable-sweep 后 Error 传播出
 * {@code GlobalExecutors.globalWorker()} 线程）导致的 stuck 交付/导出记录，在一个有界时间窗口内
 * （默认 60 min 阈值 + 10 min 扫描间隔）被标记为 FAILED，无需等待下一次进程重启。
 * 时间阈值确保正在执行的记录不被误杀（Dim14-01 per-request 误杀裁定的周期版安全前提）。</p>
 *
 * <p><b>调度集成</b>：镜像 {@code NopDatavReportScheduler} 范式——{@code @Inject @Nullable IJobScheduler}、
 * {@code @PostConstruct} 注册固定间隔 job（{@link TriggerSpec#setRepeatInterval}）、执行方法经
 * {@code beanMethod} invoker 调用。宿主未注册调度器时 INFO 日志跳过（不抛崩），重启
 * {@code @PostConstruct} 全量恢复路径不受影响（两类 recovery bean 的 {@code init()} 保持原状）。</p>
 *
 * <p><b>FAILED-brick 规避</b>：{@link #scanStuck()} 吞业务错误返回正常结果对象
 * （catch Exception → WARN 日志 → 继续下一类扫描），仅 JVM {@code Error} 向外传播——否则
 * invoker 转 {@code JobFireResult.ERROR} 后 LocalJobScheduler 将 job 永久置 FAILED
 * （schedule-report-design.md §3/§9 约定）。</p>
 */
public class NopDatavStuckTaskScanner {

    private static final Logger LOG = LoggerFactory.getLogger(NopDatavStuckTaskScanner.class);

    /** 周期扫描 job 名（固定单例 job，非 per-task） */
    public static final String JOB_NAME = "nop-datav-stuck-task-scan";

    /** 本 bean 在 IoC 容器中的注册名（与 app-service.beans.xml 一致） */
    public static final String BEAN_NAME = "nopDatavStuckTaskScanner";

    /** beanMethod invoker 调用的方法名 */
    public static final String SCHEDULED_METHOD_NAME = "scanStuck";

    /** beanMethod invoker 约定：jobParams 中的 bean 名键 */
    static final String PARAM_BEAN_NAME = "beanName";
    /** beanMethod invoker 约定：jobParams 中的方法名键 */
    static final String PARAM_METHOD_NAME = "methodName";

    private IJobScheduler scheduler;
    private NopDatavReportDeliveryRecovery deliveryRecovery;
    private NopDatavExportTaskRecovery exportTaskRecovery;

    /** scanStuck 执行计数（测试接线断言用：证明 cron 派发真正到达本方法） */
    private final AtomicLong scanCount = new AtomicLong();

    /**
     * 注入 {@link IJobScheduler}（{@code @Nullable}——宿主未注册调度器时不注入，周期扫描跳过，
     * 重启恢复路径不受影响）。生产 runtime 由宿主 app 经 {@code app-local-scheduler.beans.xml} 提供。
     */
    @Inject
    public void setScheduler(@Nullable IJobScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Inject
    public void setDeliveryRecovery(NopDatavReportDeliveryRecovery deliveryRecovery) {
        this.deliveryRecovery = deliveryRecovery;
    }

    @Inject
    public void setExportTaskRecovery(NopDatavExportTaskRecovery exportTaskRecovery) {
        this.exportTaskRecovery = exportTaskRecovery;
    }

    /**
     * 注册周期扫描 job：间隔 = {@code nop.datav.stuck-scan.interval-minutes}（默认 10 min），
     * 经 {@link TriggerSpec#setRepeatInterval} 固定间隔触发。enabled=false 或 scheduler==null 时
     * INFO 日志跳过（显式记录，非静默吞）。注册失败 try/catch 隔离，不抛崩启动。
     */
    @PostConstruct
    public void init() {
        if (scheduler == null) {
            LOG.info("nop.datav.stuck-scanner.no-scheduler: periodic stuck scan disabled (host app did not register IJobScheduler)");
            return;
        }
        if (!CFG_DATAV_STUCK_SCAN_ENABLED.get()) {
            LOG.info("nop.datav.stuck-scanner.disabled: nop.datav.stuck-scan.enabled=false, periodic stuck scan not registered");
            return;
        }
        try {
            // 防御性 activate：activate() 幂等，先激活以保证 addJob 可用（镜像 NopDatavReportScheduler.init）
            scheduler.activate();
            scheduler.addJob(buildJobSpec(), true);
            LOG.info("nop.datav.stuck-scanner.registered: jobName={} intervalMinutes={} timeoutMinutes={}",
                    JOB_NAME, CFG_DATAV_STUCK_SCAN_INTERVAL_MINUTES.get(), CFG_DATAV_STUCK_SCAN_TIMEOUT_MINUTES.get());
        } catch (Exception e) {
            LOG.error("nop.datav.stuck-scanner.register-failed: jobName={}", JOB_NAME, e);
        }
    }

    private JobSpec buildJobSpec() {
        JobSpec spec = new JobSpec();
        spec.setJobName(JOB_NAME);
        spec.setDisplayName("nop-datav stuck task scanner");
        spec.setJobGroup("nop-datav");
        spec.setJobInvoker("beanMethod");

        Map<String, Object> jobParams = new HashMap<>();
        jobParams.put(PARAM_BEAN_NAME, BEAN_NAME);
        jobParams.put(PARAM_METHOD_NAME, SCHEDULED_METHOD_NAME);
        spec.setJobParams(jobParams);

        TriggerSpec trigger = new TriggerSpec();
        trigger.setRepeatInterval(CFG_DATAV_STUCK_SCAN_INTERVAL_MINUTES.get() * 60_000L);
        spec.setTriggerSpec(trigger);
        return spec;
    }

    /**
     * beanMethod 调用入口（周期 job 触发）：读 timeout-minutes 配置并委托两类 recovery 的
     * {@code scanStuck(timeoutMinutes)}——交付按 startTime、导出按 createTime 判定（实体时间字段
     * 差异见两类 recovery 的 javadoc 与 schedule-report-design.md §25）。
     *
     * <p><b>吞业务错误返回正常结果对象（FAILED-brick 规避）</b>：单类扫描异常 catch 后 WARN 日志、
     * 继续另一类扫描，不向外抛——否则 LocalJobScheduler 将 job 永久置 FAILED。仅 {@code Error}
     * （OOM/StackOverflow）才传播（Dim14-04 catch(Exception) 非 catch(Throwable) 约定）。</p>
     *
     * @return 执行结果 Map（markedDeliveries/markedTasks）
     */
    public Map<String, Object> scanStuck() {
        scanCount.incrementAndGet();
        int timeoutMinutes = CFG_DATAV_STUCK_SCAN_TIMEOUT_MINUTES.get();
        int markedDeliveries = 0;
        try {
            markedDeliveries = deliveryRecovery.scanStuck(timeoutMinutes);
        } catch (Exception e) {
            LOG.warn("nop.datav.stuck-scanner.delivery-scan-failed: timeoutMinutes={}", timeoutMinutes, e);
        }
        int markedTasks = 0;
        try {
            markedTasks = exportTaskRecovery.scanStuck(timeoutMinutes);
        } catch (Exception e) {
            LOG.warn("nop.datav.stuck-scanner.export-scan-failed: timeoutMinutes={}", timeoutMinutes, e);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("markedDeliveries", markedDeliveries);
        result.put("markedTasks", markedTasks);
        return result;
    }

    /** 测试辅助：暴露调度器实例（测试经 {@link IJobScheduler#fireNow} 同步触发周期 job） */
    @Nullable
    public IJobScheduler getScheduler() {
        return scheduler;
    }

    /** 测试辅助：scanStuck 执行计数（接线断言：fireNow 后递增证明 cron 派发到达 {@link #scanStuck()}） */
    public long getScanCountForTest() {
        return scanCount.get();
    }

    /** 测试辅助：手动触发 {@link #scanStuck()}（不经 scheduler，受控调用 ①–⑦/⑨ 用） */
    public Map<String, Object> scanStuckForTest() {
        return scanStuck();
    }
}
