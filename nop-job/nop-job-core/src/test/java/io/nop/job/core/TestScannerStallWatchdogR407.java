/* CRA 8.47 R4-07 (plan 2540) W-07 / 1.4-C7-007 — driver 红⑦: latch hang + threshold injection. */
package io.nop.job.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestScannerStallWatchdogR407 {
    private CountDownLatch inFlight;
    private CountDownLatch release;

    @AfterEach
    void tearDown() { if (release != null) release.countDown(); }

    @Test
    public void stallDetectedWhenInFlightExceedsThreshold() throws Exception {
        inFlight = new CountDownLatch(1);
        release = new CountDownLatch(1);
        AbstractBatchScanner scanner = new AbstractBatchScanner() {
            { applyStallWatchdogMs(100); }
            @Override protected void onCycleStart() { inFlight.countDown(); }
            @Override protected boolean scanBatch() {
                try { release.await(20, TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return false;
            }
        };
        // start scanning to activate running flag + stall watchdog task
        scanner.startScanning();
        Thread scanThread = new Thread(scanner::scanOnce);
        scanThread.start();
        assertTrue(inFlight.await(10, TimeUnit.SECONDS), "cycle must start");
        Thread.sleep(500); // wait for watchdog to fire
        assertTrue(scanner.getStallDetectedCount() >= 1,
                "stall must be detected (W-07 observe-only face)");
        release.countDown();
        scanThread.join(10000);
        assertTrue(!scanThread.isAlive(), "cycle must finish");
        scanner.stopScanning();
    }

    @Test
    public void stallWatchdogDisabledByDefault() {
        AbstractBatchScanner scanner = new AbstractBatchScanner() {
            @Override protected void onCycleStart() { }
            @Override protected boolean scanBatch() { return false; }
        };
        assertEquals(0, scanner.getStallDetectedCount(),
                "default disabled: zero stall detections");
    }
}
