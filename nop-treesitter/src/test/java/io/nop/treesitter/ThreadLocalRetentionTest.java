package io.nop.treesitter;

import io.nop.treesitter.language.Language;
import io.nop.treesitter.scanner.ScannerProgram;
import io.nop.treesitter.scanner.ScannerVM;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the pooled ThreadLocal reference leak (plan 2271 TS-0):
 * the TSNode scratch cursor and the ScannerVM pool must drop their tree /
 * source references when a call finishes — including the failure path — so a
 * long-lived thread does not pin the last parsed tree's arena and source bytes.
 */
class ThreadLocalRetentionTest {

    private static final Language JSON = Language.fromClasspath("/grammars/json/tree-sitter-json-blob.bin");

    private static Object threadLocalValue(Class<?> owner, String fieldName) throws Exception {
        Field field = owner.getDeclaredField(fieldName);
        field.setAccessible(true);
        ThreadLocal<?> threadLocal = (ThreadLocal<?>) field.get(null);
        return threadLocal.get();
    }

    private static Object fieldValue(Object owner, String fieldName) throws Exception {
        Field field = owner.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(owner);
    }

    @Test
    void tsNodeScratchCursorDropsTreeReferencesAfterAccessorCall() throws Exception {
        TSTree tree = TSParser.parse(JSON, "{\"a\": [1, 2, 3]}");
        TSNode root = tree.rootNode();
        // any structural accessor routes through the pooled scratch cursor
        assertTrue(root.childCount() > 0);

        Object cursor = threadLocalValue(TSNode.class, "SCRATCH");
        assertNotNull(cursor, "scratch cursor should be pooled for reuse");
        assertNull(fieldValue(cursor, "tree"), "scratch cursor must not pin the tree");
        assertNull(fieldValue(cursor, "nav"), "scratch cursor must not pin the navigator");
    }

    @Test
    void releasedScratchCursorIsReusableAcrossTreesAndAccessors() {
        TSTree first = TSParser.parse(JSON, "{\"a\": 1}");
        TSTree second = TSParser.parse(JSON, "[1, 2, 3]");

        // across two trees and several accessor kinds: release + resetTo must
        // rebuild the navigator and answer correctly each time
        assertEquals(1, first.rootNode().childCount());
        assertNotNull(first.rootNode().child(0));
        assertEquals(1, second.rootNode().childCount());
        assertEquals("array", second.rootNode().namedChild(0).type());
        // 3 number children + the anonymous [ , , ] tokens = 7 visible children
        assertEquals(7, second.rootNode().namedChild(0).childCount());
        assertEquals(3, second.rootNode().namedChild(0).namedChildCount());
    }

    @Test
    void scannerVmPoolDropsSourceReferencesAfterSuccessfulScan() throws Exception {
        byte[] source = "x".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        // EMIT(<ordinal 7>); — a complete program that accepts immediately
        byte[] program = {(byte) ScannerProgram.EMIT, 0, 7};

        ScannerVM.Result result = ScannerVM.run(program, source, 0, new boolean[8]);
        assertNotNull(result);
        assertEquals(7, result.symbol());

        Object vm = threadLocalValue(ScannerVM.class, "POOL");
        assertNotNull(vm, "scanner VM should be pooled for reuse");
        assertNull(fieldValue(vm, "source"), "pooled VM must not pin the source bytes");
        assertNull(fieldValue(vm, "program"), "pooled VM must not pin the program");
        assertNull(fieldValue(vm, "validSymbols"), "pooled VM must not pin the valid symbols");
    }

    @Test
    void scannerVmPoolDropsSourceReferencesWhenExecutionThrows() throws Exception {
        byte[] source = "irrelevant".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        // unknown opcode 0xFF — execute() raises before any EMIT
        byte[] program = {(byte) 0xFF};

        assertThrows(TreeSitterException.class, () -> ScannerVM.run(program, source, 0, new boolean[4]));

        Object vm = threadLocalValue(ScannerVM.class, "POOL");
        assertNotNull(vm);
        assertNull(fieldValue(vm, "source"), "pooled VM must not pin the source bytes after a failure");
        assertNull(fieldValue(vm, "program"), "pooled VM must not pin the program after a failure");
        assertNull(fieldValue(vm, "validSymbols"), "pooled VM must not pin the valid symbols after a failure");
    }
}
