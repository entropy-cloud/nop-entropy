package io.nop.lint.core.node;

import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.node.TreeCache;
import io.nop.lint.core.node.TreeSitterLintNode;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The per-tree node cache (plan 09): a node's children list and wrapper are
 * materialized at most once per tree, the same list instance comes back on
 * every later walk, wrapper identity is stable, trees never share cache
 * state, concurrent first walks cooperate, and no static field anywhere in
 * the facade can retain a tree (the lifecycle contract — review F1/F5).
 */
class TestTreeSitterNodeCache {

    private static TreeSitterLanguageAdapter java;

    @BeforeAll
    static void loadLanguage() {
        java = new TreeSitterLanguageAdapter("java",
                Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"), null);
    }

    @Test
    void repeatedChildrenWalksReturnTheSameImmutableList() {
        LintTree tree = java.parse(SOURCE);
        LintNode root = tree.root();

        List<LintNode> first = root.children();
        for (int i = 0; i < 3; i++) {
            List<LintNode> again = root.children();
            assertSame(first, again, "the cached list instance is reused");
            assertEquals(first, again);
        }
        // the same holds one level down, through wrappers from the first walk
        LintNode child = first.get(0);
        assertSame(child.children(), child.children(), "child lists cache too");
    }

    @Test
    void wrapperIdentityIsStableAcrossDerivations() {
        LintTree tree = java.parse(SOURCE);
        LintNode root = tree.root();

        // the first walk interns every wrapper; a second full walk must hand
        // back the identical objects rather than fresh wrappers
        Map<LintNode, LintNode> firstWalk = new HashMap<>();
        for (LintNode node : root) {
            firstWalk.put(node, node);
        }
        for (LintNode node : root) {
            LintNode firstSeen = firstWalk.get(node);
            assertTrue(firstSeen != null, "the same value-identity node reappears");
            assertSame(firstSeen, node, "iteration reuses the interned wrapper");
        }
    }

    @Test
    void separateTreesNeverShareCacheState() {
        LintTree a = java.parse(SOURCE);
        LintTree b = java.parse(SOURCE);

        LintNode aChild = a.root().children().get(0);
        LintNode bChild = b.root().children().get(0);

        // node value-equality is (tree, id) — cross-tree lists are never
        // equals-equal; the cache isolation shows up as separate wrappers
        // over structurally identical content
        List<LintNode> aNodes = aChild.children();
        List<LintNode> bNodes = bChild.children();
        assertEquals(aNodes.size(), bNodes.size());
        for (int i = 0; i < aNodes.size(); i++) {
            assertEquals(aNodes.get(i).kind(), bNodes.get(i).kind());
            assertEquals(aNodes.get(i).text(), bNodes.get(i).text());
        }
        assertTrue(aChild != bChild, "separate trees hold separate wrappers");
    }

    @Test
    void concurrentFirstWalksCooperate() throws Exception {
        LintTree tree = java.parse(SOURCE);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger walks = new AtomicInteger();

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (LintNode node : tree.root()) {
                        node.children();
                        walks.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "all walks finish");
        // every traversal sees the same node count — no lost/duplicated nodes
        assertEquals((long) threads * countNodes(tree.root()), walks.get(),
                "every thread walked the identical cached structure");
    }

    private static int countNodes(LintNode root) {
        int count = 0;
        for (LintNode ignored : root) {
            count++;
        }
        return count;
    }

    @Test
    void noStaticFieldRetainsTreeOrWrapperState() {
        // the lifecycle contract's structural proof (review F5): the cache is
        // owned by LintTree instances — no static reference field may exist in
        // the node facade that could outlive a tree
        for (Class<?> type : List.of(LintTree.class, TreeSitterLintNode.class, TreeCache.class,
                NodeIterator.class)) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())
                        && !field.getType().isPrimitive()) {
                    assertTrue(field.getType() == String.class
                            || field.getType() == java.nio.charset.Charset.class,
                            type.getSimpleName() + "." + field.getName()
                                    + " is a static reference field — the facade must not"
                                    + " gain static state that can retain trees");
                }
            }
        }
    }

    /** Small deterministic source with enough nesting to be interesting. */
    private static final String SOURCE = """
            package demo.cache;
            import java.util.List;
            public class Store {
                private final List<Item> items = new java.util.ArrayList<>();
                public Item find(long id) {
                    for (Item item : items) {
                        if (item.id() == id) {
                            return item;
                        }
                    }
                    throw new RuntimeException("missing: " + id);
                }
                public void save(Item item) {
                    this.dao().save(item);
                }
                private Store dao() {
                    return this;
                }
            }
            """;
}
