package io.nop.lint.core.cli;

import io.nop.lint.core.engine.Diagnostic;
import io.nop.lint.core.node.SourceRange;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SHA-256 hex face the cache and baseline fingerprints ride on (plan 08
 * Phase 4): the encoding must stay byte-identical to the former
 * {@code String.format("%02x")} form — {@code --cache} artifacts and baseline
 * files remain valid across the optimization — and the cloned digest
 * prototype must be safe under concurrent use.
 */
class TestSha256HexEncoding {

    @Test
    void encodingMatchesTheStandardDigestGoldens() {
        // independent golden values (FIPS 180-2 test vectors), not
        // self-referential round-trips
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                RuleResultCache.sha256("abc".getBytes(StandardCharsets.UTF_8)));
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                RuleResultCache.sha256(new byte[0]));
    }

    @Test
    void fingerprintIsStableAcrossRepeatedCalls() {
        byte[] source = "class A { void f() { throw new RuntimeException(); } }"
                .getBytes(StandardCharsets.UTF_8);
        String first = io.nop.lint.core.suppress.BaselineEngine.fingerprint(
                "demo/rule", source, new SourceRange(10, 20));
        for (int i = 0; i < 8; i++) {
            assertEquals(first, io.nop.lint.core.suppress.BaselineEngine.fingerprint(
                    "demo/rule", source, new SourceRange(10, 20)),
                    "the fingerprint must not depend on digest instance state");
        }
    }

    @Test
    void concurrentHashingStaysCorrect() throws Exception {
        int threads = 8;
        int iterations = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();
        for (int t = 0; t < threads; t++) {
            byte[] input = ("input-" + t).getBytes(StandardCharsets.UTF_8);
            String expected = RuleResultCache.sha256(input);
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < iterations; i++) {
                        if (!expected.equals(RuleResultCache.sha256(input))) {
                            failures.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "all workers finish");
        assertEquals(0, failures.get(), "no cross-thread digest state bleed");
    }
}
