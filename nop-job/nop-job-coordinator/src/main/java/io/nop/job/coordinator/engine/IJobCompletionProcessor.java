package io.nop.job.coordinator.engine;

public interface IJobCompletionProcessor {
    void startScanning();

    void stopScanning();
}
