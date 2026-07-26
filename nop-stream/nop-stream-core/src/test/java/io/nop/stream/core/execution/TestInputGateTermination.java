package io.nop.stream.core.execution;

import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class TestInputGateTermination {

    @Test
    void testSlowProducerDoesNotCausePrematureTermination() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));
        InputGate gate = new InputGate(channels);

        p0.close();

        AtomicReference<Optional<StreamElement>> result = new AtomicReference<>();
        AtomicBoolean finished = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        Thread reader = new Thread(() -> {
            try {
                result.set(gate.read());
            } finally {
                finished.set(true);
                latch.countDown();
            }
        });
        reader.start();

        Thread.sleep(500);
        assertFalse(finished.get(), "Gate should not terminate just because of slow producer");

        p1.write(new StreamRecord<>("b"));
        p1.close();

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Reader should complete once data arrives");

        Optional<StreamElement> val = result.get();
        assertTrue(val.isPresent(), "Should read an element");
    }

    @Test
    void testSingleChannelInterruptReturnsEmpty() throws Exception {
        // P1-8: single-channel interrupt now aligns with multi-input handling:
        // set interrupt flag + return Optional.empty() (instead of throwing StreamException).
        // The SubtaskTask state machine uses the cancel flag to determine CANCELED state.
        ResultPartition partition = new ResultPartition();
        InputChannel channel = new InputChannel(partition);
        InputGate gate = new InputGate(channel);

        AtomicReference<Optional<StreamElement>> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicBoolean interruptFlagSet = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        Thread reader = new Thread(() -> {
            try {
                result.set(gate.read());
            } catch (Exception e) {
                error.set(e);
            } finally {
                // Capture the reader thread's interrupt status BEFORE it exits,
                // because Thread.isInterrupted() is cleared when a thread dies.
                interruptFlagSet.set(Thread.currentThread().isInterrupted());
                latch.countDown();
            }
        });
        reader.start();

        Thread.sleep(100);
        reader.interrupt();

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Reader thread should complete after interrupt");
        assertNull(error.get(), "Interrupt should NOT cause an exception — it returns empty");
        assertNotNull(result.get(), "read() should have returned a result");
        assertFalse(result.get().isPresent(), "Interrupt should cause read() to return Optional.empty()");
        assertTrue(interruptFlagSet.get(), "Interrupt flag should be set on the reader thread");
    }

    @Test
    void testMultiChannelAllFinishedReturnsEmpty() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));
        InputGate gate = new InputGate(channels);

        p0.close();
        p1.close();

        Optional<StreamElement> result = gate.read();
        assertFalse(result.isPresent(), "Should return empty when all channels finished");
    }
}
