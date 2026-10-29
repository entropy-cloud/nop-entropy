package io.nop.stream.core.source.coordinator;

import io.nop.stream.core.source.Boundedness;
import io.nop.stream.core.source.SimpleVersionedSerializer;
import io.nop.stream.core.source.Source;
import io.nop.stream.core.source.SourceReader;
import io.nop.stream.core.source.SourceReaderContext;
import io.nop.stream.core.source.SplitEnumerator;
import io.nop.stream.core.source.SplitEnumeratorContext;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C1-001 / C10-001 (plan 2510 R2-07, adjudication 1): the enumerator entry points
 * {@code addReader} (via {@code registerReader}) and {@code handleSplitRequest} (via
 * {@code ReaderChannel.requestSplits}) must run under the same {@code enumeratorLock}
 * as {@code snapshotState}/{@code close} — no concurrent entry into any enumerator
 * method from reader task threads vs the coordinator thread (snapshot atomicity,
 * zero double assignment / zero torn snapshot).
 *
 * <p>Red driver (overlapping interleaving, design 3.11 seam 1): {@code addReader}
 * parks for a window while concurrent {@code snapshotState} and
 * {@code registerReader} calls are issued. On the unfixed code those enter the
 * enumerator concurrently with {@code addReader} (the mutual-exclusion flag is
 * tripped = red); on the fixed code all entries are serialized by
 * {@code enumeratorLock} = green.
 */
class TestEnumeratorLockConvergenceC1_001 {

    /** Instrumented enumerator: trips a flag whenever two of its methods overlap. */
    static final class GuardedEnumerator implements SplitEnumerator<SimpleSplit, String> {
        private static final long serialVersionUID = 1L;

        final AtomicInteger concurrentEntries = new AtomicInteger();
        final AtomicBoolean mutualExclusionViolated = new AtomicBoolean();
        final List<Integer> addedReaders = Collections.synchronizedList(new ArrayList<>());
        final List<Integer> splitRequests = Collections.synchronizedList(new ArrayList<>());
        /** Parks the first {@code addReader} so a concurrent entry can race it. */
        final CountDownLatch addReaderEntered = new CountDownLatch(1);
        volatile boolean parkAddReader;

        private void enter() {
            if (concurrentEntries.incrementAndGet() > 1) {
                mutualExclusionViolated.set(true);
            }
        }

        private void exit() {
            concurrentEntries.decrementAndGet();
        }

        @Override
        public void start(SplitEnumeratorContext<SimpleSplit> context) {
        }

        @Override
        public void addReader(int subtaskIndex) {
            enter();
            try {
                addedReaders.add(subtaskIndex);
                if (parkAddReader) {
                    addReaderEntered.countDown();
                    // Simulate enumerator work; a concurrent entry here is the defect.
                    Thread.sleep(300);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exit();
            }
        }

        @Override
        public void handleSplitRequest(int subtaskIndex, Optional<Throwable> reason) {
            enter();
            try {
                splitRequests.add(subtaskIndex);
            } finally {
                exit();
            }
        }

        @Override
        public String snapshotState(long checkpointId) {
            enter();
            try {
                return "snapshot@" + checkpointId;
            } finally {
                exit();
            }
        }

        @Override
        public void restoreState(String state) {
        }

        @Override
        public void close() {
        }
    }

    static final class SimpleSplit implements io.nop.stream.core.source.SourceSplit {
        private static final long serialVersionUID = 1L;
        private final String id;

        SimpleSplit(String id) {
            this.id = id;
        }

        @Override
        public String splitId() {
            return id;
        }
    }

    static final class ToStringSerializer<T> implements SimpleVersionedSerializer<T> {
        private static final long serialVersionUID = 1L;

        @Override
        public byte[] serialize(T obj) {
            return String.valueOf(obj).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public T deserialize(int version, byte[] bytes) {
            throw new UnsupportedOperationException("not needed in this test");
        }

        @Override
        public int getVersion() {
            return 1;
        }
    }

    static final class InstrumentedSource implements Source<String, SimpleSplit, String> {
        private static final long serialVersionUID = 1L;
        final GuardedEnumerator enumerator = new GuardedEnumerator();

        @Override
        public SplitEnumerator<SimpleSplit, String> createEnumerator() {
            return enumerator;
        }

        @Override
        public SplitEnumerator<SimpleSplit, String> restoreEnumerator(String checkpointState) {
            return enumerator;
        }

        @Override
        public SourceReader<String, SimpleSplit> createReader(SourceReaderContext readerContext) {
            throw new UnsupportedOperationException("not needed in this test");
        }

        @Override
        public SimpleVersionedSerializer<String> getEnumeratorStateSerializer() {
            return new ToStringSerializer<>();
        }

        @Override
        public SimpleVersionedSerializer<SimpleSplit> getSplitSerializer() {
            return new ToStringSerializer<>();
        }

        @Override
        public Boundedness getBoundedness() {
            return Boundedness.BOUNDED;
        }
    }

    @Test
    void c1_001_enumeratorEntriesAreMutuallyExclusiveAcrossReaderAndSnapshotThreads() throws Exception {
        InstrumentedSource source = new InstrumentedSource();
        LocalSourceCoordinator<SimpleSplit, String> coordinator =
                new LocalSourceCoordinator<>("1", source, 2);
        source.enumerator.parkAddReader = true;

        // Thread A (reader task thread): registerReader -> addReader (parks).
        Thread registerA = new Thread(() -> coordinator.registerReader(0), "c1-001-reader-0");
        registerA.start();
        assertTrue(source.enumerator.addReaderEntered.await(5, TimeUnit.SECONDS),
                "addReader must have been entered");

        // Coordinator thread: snapshotState while addReader is still parked.
        AtomicReference<SourceEnumeratorSerializedState> snapshot = new AtomicReference<>();
        Thread snapshotThread = new Thread(() -> snapshot.set(coordinator.snapshotState(1L)),
                "c1-001-snapshot");
        snapshotThread.start();

        // Thread B: a second reader registering concurrently (second addReader).
        Thread registerB = new Thread(() -> coordinator.registerReader(1), "c1-001-reader-1");
        registerB.start();

        registerA.join(10_000);
        registerB.join(10_000);
        snapshotThread.join(10_000);
        assertFalse(registerA.isAlive(), "registerReader(0) must terminate");
        assertFalse(registerB.isAlive(), "registerReader(1) must terminate");
        assertFalse(snapshotThread.isAlive(), "snapshotState must terminate (zero deadlock)");

        assertNotNull(snapshot.get(), "snapshotState must return a state (enumerator started)");
        assertEquals("snapshot@1", new String(snapshot.get().getStateBytes(), StandardCharsets.UTF_8),
                "snapshot content must be intact (zero torn snapshot)");

        // The core C1-001 assertion: zero concurrent entry into any enumerator method.
        assertFalse(source.enumerator.mutualExclusionViolated.get(),
                "C1-001 red driver: enumerator methods must be mutually exclusive "
                        + "(addReader/handleSplitRequest/snapshotState converge on enumeratorLock); "
                        + "concurrent entry observed = unfixed lock discipline");

        List<Integer> readers = new ArrayList<>(source.enumerator.addedReaders);
        Collections.sort(readers);
        assertEquals(Arrays.asList(0, 1), readers,
                "both readers must have been added to the enumerator (zero lost registration)");
    }

    @Test
    void c1_001_requestSplitsConvergesOnEnumeratorLock() throws Exception {
        InstrumentedSource source = new InstrumentedSource();
        LocalSourceCoordinator<SimpleSplit, String> coordinator =
                new LocalSourceCoordinator<>("1", source, 1);
        LocalSourceCoordinator.ReaderChannel<SimpleSplit> channel = coordinator.registerReader(0);
        assertNotNull(channel);

        // Reader channel thread: requestSplits -> handleSplitRequest, racing a
        // concurrent snapshotState on the coordinator thread.
        AtomicReference<SourceEnumeratorSerializedState> snapshot = new AtomicReference<>();
        Thread snapshotThread = new Thread(() -> snapshot.set(coordinator.snapshotState(7L)),
                "c1-001-snapshot-2");
        Thread requester = new Thread(() -> channel.requestSplits(0, Optional.empty()),
                "c1-001-requestSplits");
        snapshotThread.start();
        requester.start();
        requester.join(5_000);
        snapshotThread.join(5_000);
        assertFalse(requester.isAlive(), "requestSplits must terminate");
        assertFalse(snapshotThread.isAlive(), "snapshotState must terminate");

        assertNotNull(snapshot.get());
        assertEquals(Collections.singletonList(0),
                new ArrayList<>(source.enumerator.splitRequests),
                "handleSplitRequest must have been routed to the enumerator");
        assertFalse(source.enumerator.mutualExclusionViolated.get(),
                "C1-001: requestSplits must hold enumeratorLock like snapshotState/close");
    }
}
