package io.nop.treesitter.provider;

import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The language-instance identity contract under concurrent first touch
 * (plan 2531 W-05, red⑤ — M-P2-2, deterministic current-red): N threads
 * released together through a cold provider cache must converge on ONE
 * {@link Language} instance per grammar — two instances break the
 * incremental-parse identity chain (TSParser validates the instance) and
 * double-decode the parse tables. Each round builds a fresh provider so the
 * cache is provably cold (instance isolation instead of grammar-name
 * parameterization — the built-in set is only seven names).
 */
public class TestTreeSitterProviderConcurrency_W_05 {

    private static final int THREADS = 8;
    private static final int ROUNDS = 20;

    @Test
    public void testW_05_concurrentFirstTouchConvergesOnOneLanguageInstance() throws Exception {
        List<String> divergentRounds = new ArrayList<>();
        for (int round = 0; round < ROUNDS; round++) {
            // a fresh provider per round: the cache is cold by construction
            DefaultTreeSitterLanguageProvider provider = new DefaultTreeSitterLanguageProvider();

            CountDownLatch ready = new CountDownLatch(THREADS);
            CountDownLatch go = new CountDownLatch(1);
            List<AtomicReference<Language>> slots = new ArrayList<>();
            List<Thread> threads = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                AtomicReference<Language> slot = new AtomicReference<>();
                slots.add(slot);
                Thread thread = new Thread(() -> {
                    ready.countDown();
                    try {
                        go.await(10, TimeUnit.SECONDS);
                        slot.set(provider.getLanguage("java"));
                    } catch (Exception e) {
                        slot.set(null);
                    }
                }, "w05-first-touch-" + i);
                threads.add(thread);
                thread.start();
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS), "all racers must arm");
            go.countDown();
            for (Thread thread : threads) {
                thread.join(20_000);
            }

            Language first = slots.get(0).get();
            assertNotNull(first, "round " + round + ": the first touch must resolve");
            for (int i = 1; i < slots.size(); i++) {
                Language instance = slots.get(i).get();
                if (instance != first) {
                    divergentRounds.add("round " + round + ": thread " + i
                            + " observed a divergent instance ("
                            + System.identityHashCode(instance) + " vs "
                            + System.identityHashCode(first) + ")");
                }
            }
        }
        assertTrue(divergentRounds.isEmpty(),
                () -> "divergent instance rounds: " + divergentRounds.stream().limit(5).toList()
                        + " (total " + divergentRounds.size() + "/" + ROUNDS + ")");
    }

    /**
     * The sequential contract anchor: the resolved instance is cached and
     * reused, and an unknown name still resolves to null (zero-change face
     * of the computeIfAbsent form).
     */
    @Test
    public void testW_05_resolveIsCachedAndUnknownNameStaysNull() {
        DefaultTreeSitterLanguageProvider provider = new DefaultTreeSitterLanguageProvider();
        Language first = provider.getLanguage("java");
        Language second = provider.getLanguage("java");
        assertNotNull(first);
        assertSame(first, second, "the warm path must reuse the cached instance");
        assertEquals(null, provider.getLanguage("no-such-grammar"),
                "unknown grammars stay null (fail-closed at the caller)");
        assertTrue(provider.languageNames().contains("java"));
    }
}
