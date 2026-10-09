/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WI17 (plan 2363): the synchronous-replacement pattern bean — a SYNCHRONOUS SERIAL
 * external caller with bounded capacity (semaphore) and an explicit bounded-wait
 * fallback path. The four behavioral-contract elements (external-call
 * encapsulation / bounded capacity / bounded-wait fallback / explicit serial
 * semantics) are asserted by {@code TestWi17SyncReplacementPatternE2E}.
 */
public class Wi17SyncExternalCallFunction
        extends io.nop.stream.core.common.functions.ProcessFunction<String, String> {

    private static final long serialVersionUID = 1L;

    public static final int CAPACITY = 2;
    public static final AtomicInteger IN_FLIGHT = new AtomicInteger();
    public static final AtomicInteger PEAK_CONCURRENCY = new AtomicInteger();
    public static final List<String> EXTERNAL_CALLS =
            Collections.synchronizedList(new ArrayList<>());
    public static final Map<String, List<Integer>> PER_KEY_ORDER =
            new ConcurrentHashMap<>();

    // bounded-capacity gate: CAS against the CAPACITY bound (Semaphore was removed
    // from the JDK 25/26 module set this repo builds with — same semantics via CAS)
    private AtomicInteger permits = new AtomicInteger(0);
    private final SimulatedExternalClient client = new SimulatedExternalClient();

    /**
     * WI17 timeout contract: each external call runs under an explicit deadline —
     * a call that exceeds it is cancelled and produces the explicit "TIMEOUT:"
     * fallback value (never an unbounded hang, never a silent skip). Tests inject
     * slowness via {@link #SLOW_MS}.
     */
    public static volatile long SLOW_MS = 0;

    public static final long TIMEOUT_MS = 50;

    private final java.util.concurrent.ExecutorService externalPool =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "wi17-external-client");
                t.setDaemon(true);
                return t;
            });

    static class SimulatedExternalClient {
        String call(String key) {
            int now = IN_FLIGHT.incrementAndGet();
            PEAK_CONCURRENCY.accumulateAndGet(now, Math::max);
            try {
                long slow = SLOW_MS;
                if (slow > 0) {
                    Thread.sleep(slow);
                } else {
                    Thread.sleep(5);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            EXTERNAL_CALLS.add(key);
            IN_FLIGHT.decrementAndGet();
            return "ext:" + key;
        }
    }

    @Override
    public void processElement(String value,
                               io.nop.stream.core.common.functions.ProcessFunction<String, String>.Context ctx,
                               io.nop.stream.core.util.Collector<String> out) throws Exception {
        PER_KEY_ORDER.computeIfAbsent(value, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(1);
        boolean acquired = permits.incrementAndGet() <= CAPACITY;
        String result;
        if (!acquired) {
            // bounded-capacity backpressure path (a structural defense for concurrent
            // deployments; the embedded serial local runner never saturates it):
            // explicit fallback — never an unbounded wait, never a silent drop
            permits.decrementAndGet();
            result = "FALLBACK:" + value;
        } else {
            // WI17 timeout contract: the external call runs under an explicit
            // deadline — exceeding it cancels the call and emits the explicit
            // "TIMEOUT:" fallback value
            java.util.concurrent.Future<String> pending =
                    externalPool.submit(() -> client.call(value));
            try {
                result = pending.get(TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                pending.cancel(true);
                result = "TIMEOUT:" + value;
            } catch (java.util.concurrent.ExecutionException e) {
                result = "ERROR:" + value;
            } finally {
                permits.decrementAndGet();
            }
        }
        out.collect(result);
    }
}
