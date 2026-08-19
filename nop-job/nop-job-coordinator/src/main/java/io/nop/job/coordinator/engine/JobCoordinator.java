package io.nop.job.coordinator.engine;

import io.nop.commons.service.LifeCycleSupport;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobCoordinator extends LifeCycleSupport {
    static final Logger LOG = LoggerFactory.getLogger(JobCoordinator.class);

    private IJobPlannerScanner plannerScanner;
    private IJobDispatcherScanner dispatcherScanner;
    private IJobCompletionProcessor completionProcessor;
    private IJobTimeoutChecker timeoutChecker;
    private JobScheduleCounterReconciler counterReconciler;
    private io.nop.job.api.execution.IJobInvoker invoker;
    private io.nop.job.worker.engine.IJobWorkerScanner workerScanner;

    @Inject
    public void setPlannerScanner(IJobPlannerScanner plannerScanner) {
        this.plannerScanner = plannerScanner;
    }

    @Inject
    public void setDispatcherScanner(IJobDispatcherScanner dispatcherScanner) {
        this.dispatcherScanner = dispatcherScanner;
    }

    @Inject
    public void setCompletionProcessor(IJobCompletionProcessor completionProcessor) {
        this.completionProcessor = completionProcessor;
    }

    @Inject
    public void setTimeoutChecker(IJobTimeoutChecker timeoutChecker) {
        this.timeoutChecker = timeoutChecker;
    }

    @Inject
    public void setCounterReconciler(JobScheduleCounterReconciler counterReconciler) {
        this.counterReconciler = counterReconciler;
    }

    /**
     * plan 2254: coordinator 内嵌 worker 执行链（可选注入——未装配 IJobWorkerScanner bean 时
     * coordinator 启动不失败，行为与现状一致）。
     */
    public void setWorkerScanner(io.nop.job.worker.engine.IJobWorkerScanner workerScanner) {
        this.workerScanner = workerScanner;
    }

    @Override
    protected void doStart() {
        if (plannerScanner != null) {
            plannerScanner.startScanning();
        }
        if (dispatcherScanner != null) {
            dispatcherScanner.startScanning();
        }
        if (completionProcessor != null) {
            completionProcessor.startScanning();
        }
        if (timeoutChecker != null) {
            timeoutChecker.startScanning();
        }
        if (counterReconciler != null) {
            counterReconciler.startScanning();
        }
        if (workerScanner != null) {
            workerScanner.startScanning();
        }
    }

    @Override
    protected void doStop() {
        try {
            if (plannerScanner != null)
                plannerScanner.stopScanning();
        } catch (Exception e) {
            LOG.warn("nop.job.coordinator.stop-component-failed:component=plannerScanner", e);
        }
        try {
            if (dispatcherScanner != null)
                dispatcherScanner.stopScanning();
        } catch (Exception e) {
            LOG.warn("nop.job.coordinator.stop-component-failed:component=dispatcherScanner", e);
        }
        try {
            if (completionProcessor != null)
                completionProcessor.stopScanning();
        } catch (Exception e) {
            LOG.warn("nop.job.coordinator.stop-component-failed:component=completionProcessor", e);
        }
        try {
            if (timeoutChecker != null)
                timeoutChecker.stopScanning();
        } catch (Exception e) {
            LOG.warn("nop.job.coordinator.stop-component-failed:component=timeoutChecker", e);
        }
        try {
            if (counterReconciler != null)
                counterReconciler.stopScanning();
        } catch (Exception e) {
            LOG.warn("nop.job.coordinator.stop-component-failed:component=counterReconciler", e);
        }
        try {
            if (workerScanner != null)
                workerScanner.stopScanning();
        } catch (Exception e) {
            LOG.warn("nop.job.coordinator.stop-component-failed:component=workerScanner", e);
        }
    }
}
