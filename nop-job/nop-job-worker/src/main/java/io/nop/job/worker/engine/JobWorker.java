package io.nop.job.worker.engine;

import io.nop.commons.service.LifeCycleSupport;
import jakarta.inject.Inject;

public class JobWorker extends LifeCycleSupport {
    private IJobWorkerScanner workerScanner;

    @Inject
    public void setWorkerScanner(IJobWorkerScanner workerScanner) {
        this.workerScanner = workerScanner;
    }

    @Override
    protected void doStart() {
        if (workerScanner != null) {
            workerScanner.startScanning();
        }
    }

    @Override
    protected void doStop() {
        if (workerScanner != null) {
            workerScanner.stopScanning();
        }
    }
}
