/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.sql.eval;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.nop.stream.flow.spi.IScalarFunctionResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import io.nop.stream.core.exceptions.StreamException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WI30: the UDF SPI — eval channel (compileScalar dispatch) + static-call xpl form.
 */
public class TestStreamScalarFunctionResolver {

    private StreamScalarFunctionResolver resolver;

    @BeforeEach
    public void setUp() {
        resolver = new StreamScalarFunctionResolver();
        resolver.register("reverse", args -> args[0] == null ? null
                : new StringBuilder(String.valueOf(args[0])).reverse().toString());
        resolver.register("len", args -> args[0] == null ? 0
                : String.valueOf(args[0]).length());
        resolver.registerAsDefault();
    }

    @AfterEach
    public void tearDown() {
        StreamScalarFunctionResolver.setDefault(null);
    }

    @Test
    public void registeredFunctionEvaluates() {
        assertEquals("cba", eval("reverse(a)", rec("a", "abc")));
        assertEquals(3, ((Number) eval("len(a)", rec("a", "abc"))).intValue());
    }

    @Test
    public void nullArgumentPropagates() {
        assertNull(eval("reverse(a)", rec("a", null)));
        assertEquals(0, ((Number) eval("len(a)", rec("a", null))).intValue());
    }

    @Test
    public void unknownFunctionFailsFastListingRegistered() {
        StreamException e = assertThrows(StreamException.class,
                () -> eval("upper(a)", rec("a", 1)));
        assertEquals("nop.err.stream.invalid-arg", e.getErrorCode());
        assertTrue(e.getMessage().contains("upper") || e.getMessage().contains("outside"),
                () -> e.getMessage());
    }

    @Test
    public void containsAndRegisteredNames() {
        assertTrue(resolver.contains("reverse"));
        assertTrue(!resolver.contains("no_such_fn"));
        List<String> names = resolver.namesList();
        assertTrue(names.contains("reverse") && names.contains("len"));
    }

    @Test
    public void udfInCompoundExpression() {
        assertEquals(4, ((Number) eval("len(reverse(a)) + 1", rec("a", "abc"))).intValue(),
                "UDF composed with scalar ops");
    }

    @Test
    public void staticCallEntryWorks() {
        assertEquals("cba", StreamScalarFunctionResolver.call("reverse", new Object[]{"abc"}));
    }

    private static Object eval(String expr, Map<String, Object> record) {
        return StreamSqlExprCompiler.compileScalar(
                io.nop.stream.sql.parse.EqlExprSupport.parseExpr(expr)).eval(record);
    }

    private static Map<String, Object> rec(Object... kv) {
        Map<String, Object> m = new java.util.HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    public void udfProjectionBodyContainsStaticCall() {
        // xpl channel: import hoisting + static call form
        io.nop.stream.sql.compile.SqlScalarXplPrinter printer =
                new io.nop.stream.sql.compile.SqlScalarXplPrinter(col -> "event['a']", null);
        StringBuilder sb = new StringBuilder();
        printer.printTo(io.nop.stream.sql.parse.EqlExprSupport.parseExpr("reverse(a)"), sb);
        assertTrue(sb.toString().contains("StreamScalarFunctionResolver.call('reverse'"),
                () -> "xpl body: " + sb);
        assertTrue(printer.getRequiredImports()
                .contains("io.nop.stream.sql.eval.StreamScalarFunctionResolver"),
                "the resolver class must be imported");
    }

    @Test
    public void closeIsIdempotent() {
        resolver.close();
        resolver.close(); // no throw
    }

    @Test
    public void spiInterfaceExposed() {
        IScalarFunctionResolver spi = resolver;
        assertTrue(spi.contains("reverse"));
    }

    // ------------------------------------------------------------------
    // C10-001 (plan 2512 C1-001 / adjudication ①): concurrency acceptance
    // gate. In the pre-fix state (LinkedHashMap) these trip-wires were red;
    // with the ConcurrentHashMap registry they must stay green.
    // ------------------------------------------------------------------

    @Test
    public void c10_001_concurrentRegisterAndCallNoTearOrLoss() throws Exception {
        int writers = 4;
        int perWriter = 2000;
        int totalNames = writers * perWriter;
        ExecutorService pool = Executors.newFixedThreadPool(6);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int w = 0; w < writers; w++) {
            final int base = w * perWriter;
            tasks.add(() -> {
                start.await();
                for (int i = 0; i < perWriter; i++) {
                    resolver.register("fn_" + (base + i), args -> (long) args.length);
                }
                return null;
            });
        }

        // readers hammer the per-record hot path (static call) while the
        // writers force table resize — pre-fix this could tear reads or
        // observe a null mapping for an already-registered name
        for (int r = 0; r < 2; r++) {
            tasks.add(() -> {
                start.await();
                for (int i = 0; i < 20000; i++) {
                    try {
                        Object out = StreamScalarFunctionResolver.call("len",
                                new Object[]{"abc"});
                        if (((Number) out).intValue() != 3)
                            failure.compareAndSet(null, new AssertionError(
                                    "torn read of registered function: " + out));
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                        return null;
                    }
                }
                return null;
            });
        }

        try {
            // submit first, THEN release the latch — invokeAll would block until
            // every task finishes while all tasks are still waiting on the latch
            List<Future<Void>> futures = new ArrayList<>();
            for (Callable<Void> task : tasks)
                futures.add(pool.submit(task));
            start.countDown();
            for (Future<Void> f : futures)
                f.get(60, TimeUnit.SECONDS);
            assertNull(failure.get(), () -> "concurrent register/call failed: "
                    + failure.get());
        } finally {
            pool.shutdownNow();
        }

        // every concurrently-registered name must be visible afterwards
        for (int i = 0; i < totalNames; i++) {
            assertEquals(1L, StreamScalarFunctionResolver.call("fn_" + i,
                    new Object[]{"x"}));
        }
    }

    @Test
    public void c10_001_namesListNoCmeDuringConcurrentRegistration() throws Exception {
        for (int i = 0; i < 200; i++)
            resolver.register("pre_" + i, args -> null);

        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        Thread writer = new Thread(() -> {
            try {
                start.await();
                for (int i = 0; i < 50000; i++)
                    resolver.register("w_" + i, args -> null);
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            } finally {
                done.countDown();
            }
        });

        Thread reader = new Thread(() -> {
            try {
                start.await();
                // 迭代量与 writer 注册量解耦：每次迭代对增长到 ~5 万条目的 map 做两次全量
                // 拷贝（namesList + registeredNames），20000 次迭代 = ~10^9 元素拷贝，在
                // 高负载构建机（load>200，4 surefire fork 并发）下会击穿 60s 时限（no-CME
                // 语义断言与迭代次数无关，2000 次已完整覆盖 writer 的注册增长窗口）。
                for (int i = 0; i < 2000 && failure.get() == null; i++) {
                    try {
                        // compile-time iteration faces: snapshot via namesList and
                        // iterate via registeredNames while puts are in flight
                        List<String> names = resolver.namesList();
                        if (names.size() < 200)
                            failure.compareAndSet(null, new AssertionError(
                                    "lost registration observed: " + names.size()));
                        for (String s : resolver.registeredNames()) {
                            if (s == null)
                                failure.compareAndSet(null, new AssertionError(
                                        "null name in iteration"));
                        }
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                        return;
                    }
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            } finally {
                done.countDown();
            }
        });

        writer.start();
        reader.start();
        start.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS), "stress threads did not finish");
        assertNull(failure.get(), () -> "iteration face failed under concurrent "
                + "registration: " + failure.get());
    }

    @Test
    public void c10_001_runtimeRebindWarnsAndTakesEffect() {
        Logger slf4jLogger = LoggerFactory.getLogger(StreamScalarFunctionResolver.class);
        ch.qos.logback.classic.Logger logbackLogger =
                (ch.qos.logback.classic.Logger) slf4jLogger;
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logbackLogger.addAppender(appender);
        StreamScalarFunctionResolver rebound = new StreamScalarFunctionResolver();
        try {
            // a default is already registered (setUp): re-binding must be
            // permitted (application-side hot swap) but leave a WARN trace
            rebound.register("shout", args -> String.valueOf(args[0]).toUpperCase());
            StreamScalarFunctionResolver.setDefault(rebound);

            boolean warned = appender.list.stream()
                    .anyMatch(e -> e.getLevel() == Level.WARN
                            && e.getFormattedMessage().contains("rebound"));
            assertTrue(warned, "runtime rebind must emit a WARN log; events="
                    + appender.list.size());

            // the rebind takes effect for subsequent per-record lookups
            // without recompilation (static call goes through defaultResolver)
            assertEquals("ABC", StreamScalarFunctionResolver.call("shout",
                    new Object[]{"abc"}));
            assertThrows(IllegalStateException.class,
                    () -> StreamScalarFunctionResolver.call("reverse",
                            new Object[]{"abc"}),
                    "the new resolver's function table must be authoritative "
                            + "after rebinding");
        } finally {
            logbackLogger.detachAppender(appender);
            StreamScalarFunctionResolver.setDefault(null);
        }
    }
}
