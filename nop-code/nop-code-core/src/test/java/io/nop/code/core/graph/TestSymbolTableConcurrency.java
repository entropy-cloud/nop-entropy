package io.nop.code.core.graph;

import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W-11 (plan 2565, adjudication A / facility F3): SymbolTable single-point concurrency
 * convergence. Structural pins: both index maps must be ConcurrentHashMap, truncated must be
 * volatile; point reads are safe via CHM weakly-consistent reads; dedup must be atomic via
 * putIfAbsent (CodeCacheManager.addToSymbolTableCache check-then-act closure, G11 alias).
 *
 * getAll() keeps the synchronized defensive snapshot contract (WP-5 AR-155/158 frozen —
 * CHM weakly-consistent iteration does not provide immunity-to-later-mutation semantics).
 */
class TestSymbolTableConcurrency {

    private CodeSymbol symbol(String id, String qualifiedName) {
        CodeSymbol s = new CodeSymbol();
        s.setId(id);
        s.setQualifiedName(qualifiedName);
        s.setKind(CodeSymbolKind.CLASS);
        return s;
    }

    // ---------- structural pins (red pre-fix, deterministic) ----------

    @Test
    void testIndexMapsAreConcurrentHashMaps() throws Exception {
        assertFieldTypeIsChm("byQualifiedName");
        assertFieldTypeIsChm("byId");
    }

    private void assertFieldTypeIsChm(String fieldName) throws Exception {
        Field f = SymbolTable.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        assertEquals("java.util.concurrent.ConcurrentHashMap", f.get(new SymbolTable()).getClass().getName(),
                fieldName + " must be a ConcurrentHashMap (W-11 structural pin)");
    }

    @Test
    void testTruncatedIsVolatile() throws Exception {
        Field f = SymbolTable.class.getDeclaredField("truncated");
        f.setAccessible(true);
        assertTrue(Modifier.isVolatile(f.getModifiers()),
                "truncated must be volatile (W-11 structural pin: site #27 isTruncated read class)");
    }

    // ---------- putIfAbsent atomic dedup leg ----------

    @Test
    void testPutIfAbsentIsIdempotent() {
        SymbolTable table = new SymbolTable();
        assertTrue(table.putIfAbsent(symbol("id1", "com.example.Foo")),
                "first putIfAbsent for a qualified name must insert");
        assertFalse(table.putIfAbsent(symbol("id2", "com.example.Foo")),
                "second putIfAbsent for the same qualified name must not overwrite (atomic dedup)");
        assertEquals("id1", table.getByQualifiedName("com.example.Foo").getId());
    }

    @Test
    void testPutIfAbsentNullKeysGuarded() {
        SymbolTable table = new SymbolTable();
        CodeSymbol anon = new CodeSymbol();
        anon.setId("id1");
        anon.setKind(CodeSymbolKind.CLASS);
        // qualified name null: must not NPE (CHM putIfAbsent(null, ...) would throw)
        assertTrue(table.putIfAbsent(anon));
        assertNotNull(table.getById("id1"));

        CodeSymbol noId = symbol(null, "com.example.Anon");
        assertTrue(table.putIfAbsent(noId));
        assertNotNull(table.getByQualifiedName("com.example.Anon"));
    }

    @Test
    void testAddNullKeysGuarded() {
        SymbolTable table = new SymbolTable();
        CodeSymbol anon = new CodeSymbol();
        anon.setId("id1");
        anon.setKind(CodeSymbolKind.CLASS);
        assertDoesNotThrow(() -> table.add(anon));
        assertNotNull(table.getById("id1"));
    }

    // ---------- concurrent add + point-read equivalence (regression green leg) ----------

    @Test
    void testConcurrentAddAndPointReads() throws Exception {
        final SymbolTable table = new SymbolTable();
        final int writers = 8;
        final int perWriter = 500;
        ExecutorService pool = Executors.newFixedThreadPool(writers + 4);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(writers + 4);
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        for (int w = 0; w < writers; w++) {
            final int base = w * perWriter;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perWriter; i++) {
                        table.add(symbol("id" + (base + i), "com.example.C" + (base + i)));
                    }
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                } finally {
                    done.countDown();
                }
            });
        }
        // readers exercise point reads, prefix scan and snapshot concurrently
        for (int r = 0; r < 4; r++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 500; i++) {
                        CodeSymbol s = table.getByQualifiedName("com.example.C" + (i % (writers * perWriter)));
                        if (s != null) {
                            assertNotNull(s.getId());
                            assertNotNull(table.getById(s.getId()));
                        }
                        table.size();
                        List<CodeSymbol> prefix = table.findAllByQualifiedNamePrefix("com.example.C1");
                        for (CodeSymbol ps : prefix) {
                            assertNotNull(ps.getQualifiedName());
                        }
                        Collection<CodeSymbol> snap = new ArrayList<>(table.getAll());
                        snap.clear(); // mutating the copy must be safe
                    }
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "concurrent add/read must not deadlock");
        pool.shutdownNow();
        assertNull(failure.get(), "no exception under concurrent add + point reads");
        assertEquals(writers * perWriter, table.size());
    }

    // ---------- getAll defensive snapshot frozen contract (WP-5 AR-155/158) ----------

    @Test
    void testGetAllSnapshotImmuneToConcurrentMutation() throws Exception {
        final SymbolTable table = new SymbolTable();
        table.add(symbol("id1", "com.example.A"));
        Collection<CodeSymbol> snapshot = table.getAll();
        assertEquals(1, snapshot.size());

        // concurrent writer must not affect the already-taken snapshot
        Thread mutator = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                table.add(symbol("x" + i, "com.example.X" + i));
            }
        });
        mutator.start();
        mutator.join();
        assertEquals(1, snapshot.size(), "AR-155/158: snapshot immune to later mutation");
        assertEquals(101, table.getAll().size());
    }

    // ---------- mutation-chain helpers: pin the single declaration point ----------

    @Test
    void testFindAllByQualifiedNamePrefixStillWorks() {
        SymbolTable table = new SymbolTable();
        table.add(symbol("id1", "com.example.Foo"));
        table.add(symbol("id2", "com.example.FooBar"));
        List<CodeSymbol> hits = table.findAllByQualifiedNamePrefix("com.example.Foo");
        assertEquals(2, hits.size());
        assertEquals(1, table.findAllByQualifiedNamePrefix("com.example.FooBar").size());
        assertEquals(0, table.findAllByQualifiedNamePrefix("nope").size());
        // concurrent prefix scan must not throw CME (CHM traversal is weakly consistent)
        assertDoesNotThrow(() -> {
            Thread t = new Thread(() -> table.add(symbol("id3", "com.example.FooBaz")));
            t.start();
            table.findAllByQualifiedNamePrefix("com.example.Foo");
            t.join();
        });
    }

    // ensure Collections import is used (keeps imports grouped convention visible)
    @Test
    void testSymbolsCollectionsContract() {
        SymbolTable table = new SymbolTable();
        table.add(symbol("id1", "com.example.Foo"));
        Collection<CodeSymbol> all = table.getAll();
        assertNotNull(all);
        assertFalse(Collections.unmodifiableCollection(all).isEmpty());
    }
}
