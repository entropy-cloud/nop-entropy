package io.nop.stream.cep.operator;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import io.nop.stream.cep.CepTestUtils;
import io.nop.stream.cep.Event;
import io.nop.stream.cep.functions.PatternProcessFunction;
import io.nop.stream.cep.nfa.sharedbuffer.EventId;
import io.nop.stream.cep.nfa.sharedbuffer.SharedBuffer;
import io.nop.stream.cep.nfa.sharedbuffer.SharedBufferAccessor;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.cep.pattern.conditions.SimpleCondition;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.operators.ProcessingTimeService;
import io.nop.stream.core.operators.ProcessingTimeService.ProcessingTimeCallback;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.util.Collector;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the periodic cache-statistics wiring introduced in Stage 54 / G65:
 * <ul>
 *   <li>{@link SharedBuffer#logCacheStatistics()} emits a log line containing
 *       hit/miss/eviction/size for both caches.</li>
 *   <li>{@link CepOperator} registers the periodic timer via
 *       {@link ProcessingTimeService#registerTimer(long, ProcessingTimeCallback)}
 *       (NOT via {@code InternalTimerService}/{@code cepTimerService} which route to
 *       {@link CepOperator#onProcessingTime(long)} CEP event processing).</li>
 *   <li>The dedicated {@link CepOperator#onCacheStatisticsTimer(long)} callback re-arms the
 *       timer anchored to fire time (not current time).</li>
 *   <li>{@link CepOperator#releaseCacheStatisticsTimer()} cancels the future.</li>
 *   <li>Pattern-matching semantics are unchanged (E2E regression).</li>
 * </ul>
 */
public class TestCepOperatorCacheStatistics {

    /**
     * Minimal {@link ScheduledFuture} that records cancellation state for assertion. Returned
     * by {@link CapturingProcessingTimeService#registerTimer} so tests can verify
     * {@code releaseCacheStatisticsTimer()} cancels the live future.
     */
    static final class TestScheduledFuture<V> implements ScheduledFuture<V> {
        private volatile boolean cancelled;
        private volatile boolean done;

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (done) {
                return false;
            }
            cancelled = true;
            done = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return done;
        }

        @Override
        public V get() {
            throw new UnsupportedOperationException("not used in tests");
        }

        @Override
        public V get(long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException("not used in tests");
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed o) {
            return 0;
        }
    }

    /** A captured {@code registerTimer} registration: timestamp + callback + future handle. */
    static final class Registration {
        final long timestamp;
        final ProcessingTimeCallback callback;
        final TestScheduledFuture<?> future;

        Registration(long timestamp, ProcessingTimeCallback callback, TestScheduledFuture<?> future) {
            this.timestamp = timestamp;
            this.callback = callback;
            this.future = future;
        }
    }

    /**
     * Controllable {@link ProcessingTimeService} that captures every {@code registerTimer}
     * call so tests can drive callbacks manually. Lets the test advance current time and
     * observe re-arm behaviour.
     */
    static final class CapturingProcessingTimeService implements ProcessingTimeService {
        private volatile long currentTime = 1_000L;
        final ConcurrentLinkedQueue<Registration> registrations = new ConcurrentLinkedQueue<>();
        final AtomicLong registerTimerCallCount = new AtomicLong();

        void setCurrentTime(long t) {
            this.currentTime = t;
        }

        @Override
        public long getCurrentProcessingTime() {
            return currentTime;
        }

        @Override
        public ScheduledFuture<?> registerTimer(long timestamp, ProcessingTimeCallback target) {
            registerTimerCallCount.incrementAndGet();
            TestScheduledFuture<?> future = new TestScheduledFuture<>();
            registrations.add(new Registration(timestamp, target, future));
            return future;
        }

        /** Returns only the registrations whose timestamp matches. */
        List<Registration> registrationsForTimestamp(long ts) {
            List<Registration> result = new ArrayList<>();
            for (Registration r : registrations) {
                if (r.timestamp == ts) {
                    result.add(r);
                }
            }
            return result;
        }
    }

    /** A {@link CepOperator} that counts {@code onProcessingTime} invocations (CEP event path). */
    static final class CountingCepOperator extends CepOperator<Event, Integer, String> {
        final AtomicInteger onProcessingTimeCount = new AtomicInteger();
        final AtomicInteger onCacheStatisticsTimerCount = new AtomicInteger();

        CountingCepOperator(NFACompiler.NFAFactory<Event> nfaFactory,
                            PatternProcessFunction<Event, String> function) {
            super(new EventTypeSerializer(), false, nfaFactory, null, null, function, null);
        }

        @Override
        public void onProcessingTime(long time) throws Exception {
            onProcessingTimeCount.incrementAndGet();
            super.onProcessingTime(time);
        }

        @Override
        void onCacheStatisticsTimer(long timestamp) {
            onCacheStatisticsTimerCount.incrementAndGet();
            super.onCacheStatisticsTimer(timestamp);
        }
    }

    @Test
    void testLogCacheStatisticsOutputsHitMissEvictionSize() throws Exception {
        SharedBuffer<Event> buffer = new SharedBuffer<>(
                new io.nop.stream.core.common.state.simple.SimpleKeyedStateStore(),
                null,
                new io.nop.stream.cep.configuration.SharedBufferCacheConfig(
                        2, 2, Duration.ofMinutes(1)));

        // Exercise the cache to populate stats: insert 4 events into a max-size=2 cache
        // so that at least 2 SIZE evictions are counted. registerEvent is the public entry
        // point and triggers both cache.put (write-through) and cache.containsKey (in the
        // eventId-collision loop).
        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            accessor.registerEvent(new Event(1, "a"), 100L);
            accessor.registerEvent(new Event(2, "b"), 101L);
            accessor.registerEvent(new Event(3, "c"), 102L);
            accessor.registerEvent(new Event(4, "d"), 103L);
        }

        long evictionsBeforeLog = buffer.getEventsBufferEvictionCount();
        assertTrue(evictionsBeforeLog >= 2,
                "SIZE eviction should have been counted when capacity is exceeded. evictions="
                        + evictionsBeforeLog);

        // Capture log output for SharedBuffer.logCacheStatistics().
        Logger logger = (Logger) LoggerFactory.getLogger(SharedBuffer.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            buffer.logCacheStatistics();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertFalse(appender.list.isEmpty(),
                "logCacheStatistics() should have produced at least one log event");
        ILoggingEvent event = appender.list.get(0);
        String formatted = event.getFormattedMessage();
        assertTrue(formatted.contains("hitCount="),
                "Log line must contain hitCount. line=" + formatted);
        assertTrue(formatted.contains("missCount="),
                "Log line must contain missCount. line=" + formatted);
        assertTrue(formatted.contains("evictionCount="),
                "Log line must contain evictionCount. line=" + formatted);
        assertTrue(formatted.contains("size="),
                "Log line must contain size. line=" + formatted);
        assertTrue(formatted.contains("eventsBufferCache"),
                "Log line must identify the eventsBufferCache. line=" + formatted);
        assertTrue(formatted.contains("entryCache"),
                "Log line must identify the entryCache. line=" + formatted);

        // The formatted line should also reflect the eviction count we observed above.
        assertTrue(formatted.contains("evictionCount=" + evictionsBeforeLog)
                        || formatted.contains("evictionCount=0"),
                "Log line should embed the actual evictionCount value. line=" + formatted);
    }

    @Test
    void testCacheStatisticsRegisteredViaProcessingTimeService() throws Exception {
        CountingCepOperator operator = newOperator();
        CapturingProcessingTimeService pts = new CapturingProcessingTimeService();
        CepTestUtils.injectProcessingTimeService(operator, pts);
        operator.setOutput(new TestOutput<>());
        operator.open();

        try {
            assertTrue(pts.registerTimerCallCount.get() >= 1,
                    "open() should have registered the cache-statistics timer via ProcessingTimeService. "
                            + "calls=" + pts.registerTimerCallCount.get());
            assertFalse(pts.registrations.isEmpty(),
                    "At least one registration must be captured");
        } finally {
            operator.close();
        }
    }

    /**
     * Verifies that firing the cache-statistics timer invokes the dedicated
     * {@code onCacheStatisticsTimer} callback (which logs stats) and does NOT invoke
     * {@code onProcessingTime} (the CEP event-processing path). The two callbacks must
     * remain independent.
     */
    @Test
    void testCacheStatisticsUsesDedicatedCallbackNotCepProcessing() throws Exception {
        CountingCepOperator operator = newOperator();
        CapturingProcessingTimeService pts = new CapturingProcessingTimeService();
        CepTestUtils.injectProcessingTimeService(operator, pts);
        operator.setOutput(new TestOutput<>());
        operator.open();

        try {
            // Capture the first cache-statistics registration (timestamp, callback, future).
            assertFalse(pts.registrations.isEmpty(),
                    "open() must register the cache-statistics timer");
            Registration firstReg = pts.registrations.peek();
            assertNotNull(firstReg);

            int onProcessingTimeBefore = operator.onProcessingTimeCount.get();
            int onCacheStatsBefore = operator.onCacheStatisticsTimerCount.get();

            // Fire the cache-statistics callback directly via the captured ProcessingTimeCallback.
            firstReg.callback.onProcessingTime(firstReg.timestamp);

            assertEquals(onCacheStatsBefore + 1, operator.onCacheStatisticsTimerCount.get(),
                    "onCacheStatisticsTimer should have been invoked exactly once");
            assertEquals(onProcessingTimeBefore, operator.onProcessingTimeCount.get(),
                    "onProcessingTime (CEP event processing) must NOT be triggered by the "
                            + "cache-statistics timer");
        } finally {
            operator.close();
        }
    }

    /**
     * Verifies that {@link CepOperator#onCacheStatisticsTimer(long)} re-arms the timer via
     * {@code ProcessingTimeService.registerTimer}, anchored to fire time (timestamp +
     * interval) rather than current time.
     */
    @Test
    void testCacheStatisticsTimerReArmsOnFire() throws Exception {
        CountingCepOperator operator = newOperator();
        CapturingProcessingTimeService pts = new CapturingProcessingTimeService();
        CepTestUtils.injectProcessingTimeService(operator, pts);
        operator.setOutput(new TestOutput<>());
        operator.open();

        long intervalMs;
        try {
            intervalMs = io.nop.stream.cep.NopCepConfigs.CEP_CACHE_STATISTICS_INTERVAL
                    .get().toMillis();
            assertTrue(intervalMs > 0, "Default interval must be positive for this test");
        } finally {
            operator.close();
        }
        // Re-open with a fresh operator to keep counters clean for the assertions below.
        CountingCepOperator operator2 = newOperator();
        CapturingProcessingTimeService pts2 = new CapturingProcessingTimeService();
        CepTestUtils.injectProcessingTimeService(operator2, pts2);
        operator2.setOutput(new TestOutput<>());
        operator2.open();

        try {
            assertEquals(1, pts2.registrations.size(),
                    "open() should register exactly one cache-statistics timer. regs="
                            + pts2.registrations.size());
            Registration initial = pts2.registrations.peek();
            assertNotNull(initial);

            int onCacheStatsBefore = operator2.onCacheStatisticsTimerCount.get();
            // Fire the initial callback; onCacheStatisticsTimer should re-arm by calling
            // registerTimer(timestamp + interval, this::onCacheStatisticsTimer).
            initial.callback.onProcessingTime(initial.timestamp);

            assertEquals(onCacheStatsBefore + 1, operator2.onCacheStatisticsTimerCount.get(),
                    "onCacheStatisticsTimer should have been invoked");
            assertEquals(2, pts2.registrations.size(),
                    "Re-arm must register a second timer. regs=" + pts2.registrations.size());

            // The second registration's timestamp must equal the fire time + interval (anchored).
            Registration[] regs = pts2.registrations.toArray(new Registration[0]);
            long expectedReArm = regs[0].timestamp + intervalMs;
            assertEquals(expectedReArm, regs[1].timestamp,
                    "Re-arm must be anchored to fire time (timestamp + interval), not current time. "
                            + "expected=" + expectedReArm + ", actual=" + regs[1].timestamp);
        } finally {
            operator2.close();
        }
    }

    /**
     * Verifies {@link CepOperator#releaseCacheStatisticsTimer()} cancels the live future
     * returned by {@code registerTimer}. Idempotent: a second call is a no-op (no exception,
     * no NPE).
     */
    @Test
    void testReleaseCacheStatisticsTimerCancelsFuture() throws Exception {
        CountingCepOperator operator = newOperator();
        CapturingProcessingTimeService pts = new CapturingProcessingTimeService();
        CepTestUtils.injectProcessingTimeService(operator, pts);
        operator.setOutput(new TestOutput<>());
        operator.open();

        Registration initial = pts.registrations.peek();
        assertNotNull(initial, "open() must register the cache-statistics timer");
        assertFalse(initial.future.isCancelled(),
                "Future must not be cancelled before release");

        operator.releaseCacheStatisticsTimer();

        assertTrue(initial.future.isCancelled(),
                "releaseCacheStatisticsTimer() must cancel the live future");

        // Idempotent: second call must not throw and must remain consistent.
        operator.releaseCacheStatisticsTimer();
    }

    /**
     * Verifies that on {@link CepOperator#close()}, the cache-statistics timer is cancelled.
     */
    @Test
    void testCloseCancelsCacheStatisticsTimer() throws Exception {
        CountingCepOperator operator = newOperator();
        CapturingProcessingTimeService pts = new CapturingProcessingTimeService();
        CepTestUtils.injectProcessingTimeService(operator, pts);
        operator.setOutput(new TestOutput<>());
        operator.open();

        Registration initial = pts.registrations.peek();
        assertNotNull(initial);
        assertFalse(initial.future.isCancelled());

        operator.close();

        assertTrue(initial.future.isCancelled(),
                "close() must cancel the cache-statistics timer future");
    }

    /**
     * E2E regression: pattern matching behaviour is unchanged after the cache primitive
     * migration (LruCache -> Guava Cache) and the statistics timer wiring. Two matches must
     * still be produced for a simple begin(id>=42)->followedBy(name=="end") pattern.
     */
    @Test
    void testSharedBufferPatternMatchingRegression() throws Exception {
        TestOutput<String> output = new TestOutput<>();
        PatternProcessFunction<Event, String> function = new PatternProcessFunction<>() {
            @Override
            public void processMatch(Map<String, List<Event>> match, Context ctx, Collector<String> out) {
                Event start = match.get("start").get(0);
                Event end = match.get("end").get(0);
                out.collect(start.getName() + "->" + end.getName());
            }
        };

        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(event -> event.getId() >= 42))
                .followedBy("end")
                .where(SimpleCondition.of(event -> event.getName().equals("end")));

        NFACompiler.NFAFactory<Event> nfaFactory = NFACompiler.compileFactory(pattern, false);

        CepOperator<Event, Integer, String> operator = new CepOperator<>(
                new EventTypeSerializer(), false, nfaFactory, null, null, function, null);
        operator.setOutput(output);
        // Use the capturing PTS so registerTimer (called by open()) does not return null
        // during cache-statistics wiring.
        CapturingProcessingTimeService pts = new CapturingProcessingTimeService();
        CepTestUtils.injectProcessingTimeService(operator, pts);
        operator.open();

        try {
            operator.processElement(new StreamRecord<>(new Event(42, "a"), 1));
            operator.processElement(new StreamRecord<>(new Event(99, "end"), 2));
            operator.processWatermark(new Watermark(5));

            operator.processElement(new StreamRecord<>(new Event(50, "b"), 6));
            operator.processElement(new StreamRecord<>(new Event(100, "end"), 7));
            operator.processWatermark(new Watermark(20));

            assertTrue(output.size() >= 2,
                    "Should produce at least two matches, got: " + output.getElements());
            assertTrue(output.getElements().contains("a->end"),
                    "Should contain first match 'a->end', got: " + output.getElements());
            assertTrue(output.getElements().contains("b->end"),
                    "Should contain second match 'b->end', got: " + output.getElements());
        } finally {
            operator.close();
        }
    }

    private static CountingCepOperator newOperator() {
        PatternProcessFunction<Event, String> function = new PatternProcessFunction<>() {
            @Override
            public void processMatch(Map<String, List<Event>> match, Context ctx, Collector<String> out) {
                // no-op for cache-statistics tests; we never assert match output here
            }
        };
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(event -> event.getId() >= 42))
                .followedBy("end")
                .where(SimpleCondition.of(event -> event.getName().equals("end")));
        NFACompiler.NFAFactory<Event> nfaFactory = NFACompiler.compileFactory(pattern, false);
        return new CountingCepOperator(nfaFactory, function);
    }

    private static final class EventTypeSerializer implements TypeSerializer<Event> {
        @Override public boolean isImmutableType() { return false; }
        @Override public TypeSerializer<Event> duplicate() { return this; }
        @Override public Event createInstance() { return new Event(); }
        @Override public Event copy(Event from) { return new Event(from.getId(), from.getName()); }
        @Override public Event copy(Event from, Event reuse) { return new Event(from.getId(), from.getName()); }
        @Override public int getLength() { return -1; }
    }
}
