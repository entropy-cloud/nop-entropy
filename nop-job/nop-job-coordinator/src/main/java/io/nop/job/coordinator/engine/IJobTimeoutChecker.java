package io.nop.job.coordinator.engine;

public interface IJobTimeoutChecker {
    void startScanning();

    void stopScanning();
}
