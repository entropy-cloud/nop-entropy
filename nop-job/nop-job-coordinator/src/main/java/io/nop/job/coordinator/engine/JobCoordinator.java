package io.nop.job.coordinator.engine;

import io.nop.commons.service.LifeCycleSupport;
import jakarta.inject.Inject;

public class JobCoordinator extends LifeCycleSupport {
    private IJobPlannerScanner plannerScanner;
    private IJobDispatcherScanner dispatcherScanner;
    private IJobCompletionProcessor completionProcessor;
    private IJobTimeoutChecker timeoutChecker;

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
    }

    @Override
    protected void doStop() {
        if (timeoutChecker != null) {
            timeoutChecker.stopScanning();
        }
        if (completionProcessor != null) {
            completionProcessor.stopScanning();
        }
        if (dispatcherScanner != null) {
            dispatcherScanner.stopScanning();
        }
        if (plannerScanner != null) {
            plannerScanner.stopScanning();
        }
    }
}
