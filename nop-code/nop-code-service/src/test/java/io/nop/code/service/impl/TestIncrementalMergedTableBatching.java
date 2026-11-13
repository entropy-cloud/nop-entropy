package io.nop.code.service.impl;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import io.nop.code.service.api.ICodeIndexService;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W-16 (plan 2565, adjudication K / TP-009): the incremental persist path must build the
 * merged global+file symbol table ONCE PER BATCH (getAll defensive snapshot frequency drops
 * F -> batch count) and grow it incrementally per file with global-wins fill-gap semantics
 * (R2-2 accumulation + R2-1/勘正 global-wins equivalence preserved).
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestIncrementalMergedTableBatching extends JunitAutoTestCase {

    @Inject
    ICodeIndexService codeIndexService;

    private CodeIndexService impl() {
        return (CodeIndexService) codeIndexService;
    }

    // ==================== global-wins unit legs (no DB) ====================

    private CodeSymbol symbol(String id, String qn) {
        CodeSymbol s = new CodeSymbol();
        s.setId(id);
        s.setQualifiedName(qn);
        s.setKind(CodeSymbolKind.CLASS);
        return s;
    }

    /** R2-1 (勘正 B2): a file symbol must NOT override an existing global entry (global-wins) */
    @Test
    void testGlobalWinsFileFillsGapsOnly() throws Exception {
        CodeIndexService.MergedTableContext ctx = newCtx();
        SymbolTable global = new SymbolTable();
        global.add(symbol("global-x", "com.example.X"));
        ctx.resetFromGlobalIfNeeded(global);

        SymbolTable file = new SymbolTable();
        file.add(symbol("file-x", "com.example.X"));     // duplicate qn — must NOT override
        file.add(symbol("file-y", "com.example.Y"));     // gap — must fill
        ctx.mergeFileSymbols(file);

        assertEquals("global-x", ctx.table().getByQualifiedName("com.example.X").getId(),
                "global-wins: file duplicate must not override the global entry");
        assertEquals("file-y", ctx.table().getByQualifiedName("com.example.Y").getId(),
                "file symbol fills the qualified-name gap");
    }

    /** R2-2: batch accumulation — later files see earlier files' symbols of the same batch */
    @Test
    void testBatchAccumulationAcrossFiles() throws Exception {
        CodeIndexService.MergedTableContext ctx = newCtx();
        SymbolTable global = new SymbolTable();
        ctx.resetFromGlobalIfNeeded(global);

        SymbolTable file1 = new SymbolTable();
        file1.add(symbol("f1-a", "com.example.A"));
        ctx.mergeFileSymbols(file1);

        SymbolTable file2 = new SymbolTable();
        file2.add(symbol("f2-b", "com.example.B"));
        ctx.mergeFileSymbols(file2);

        assertEquals("f1-a", ctx.table().getByQualifiedName("com.example.A").getId(),
                "file 2 must resolve symbols introduced by file 1 (accumulation)");
        assertNotNull(ctx.table().getByQualifiedName("com.example.B"));
    }

    /** snapshot built once per context even across many files (F -> batch-count) */
    @Test
    void testSnapshotBuiltOncePerContext() throws Exception {
        CodeIndexService.MergedTableContext ctx = newCtx();
        SymbolTable global = new SymbolTable();
        global.add(symbol("g", "com.example.G"));
        for (int i = 0; i < 10; i++) {
            ctx.resetFromGlobalIfNeeded(global);
            SymbolTable file = new SymbolTable();
            file.add(symbol("f-" + i, "com.example.F" + i));
            ctx.mergeFileSymbols(file);
        }
        assertEquals(1, ctx.snapshotBuildCount, "snapshot built once per context (batch)");
        assertEquals(11, ctx.table().size(), "snapshot accumulated all file symbols");
    }

    private CodeIndexService.MergedTableContext newCtx() {
        // MergedTableContext is an inner class capturing the service (cumulative counter)
        return impl().new MergedTableContext();
    }

    // ==================== batch-count leg (DB-backed, triggerIncrementalIndex) ====================

    private void writeJavaFile(Path src, String pkgDir, String name, String content) throws Exception {
        Path file = src.resolve(pkgDir).resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    /** TP-009: snapshot build count = ceil(F / BATCH_SIZE) (=1 for a small batch), not F */
    @Test
    void testMergedTableSnapshotCountEqualsBatchCount() throws Exception {
        Path src = Files.createTempDirectory("w16-src");
        String[] files = {"One", "Two", "Three"};
        for (String name : files) {
            writeJavaFile(src, "w16", name + ".java",
                    "package w16;\npublic class " + name + " { public void go() {} }\n");
        }
        String indexId = "w16-batch";
        codeIndexService.indexDirectory(indexId, src.toAbsolutePath().toString().replace('\\', '/'), null);

        // modify all 3 files so the incremental pass re-persists them (single batch: F=3 << BATCH_SIZE=1000)
        for (String name : files) {
            writeJavaFile(src, "w16", name + ".java",
                    "package w16;\npublic class " + name + " { public void goDeep() {} }\n");
        }
        setLongField("lastBatchSnapshotBuilds", 0); // reset: per-file rebuild would end at F
        int changed = codeIndexService.triggerIncrementalIndex(indexId,
                "file:" + src.toAbsolutePath().toString().replace('\\', '/'), null);
        assertEquals(3, changed, "fixture requires 3 re-persisted files");
        assertEquals(1, getLongField("lastBatchSnapshotBuilds"),
                "merged-table snapshot must be built ONCE per batch, not per file (pre-fix = 3)");
        codeIndexService.deleteIndex(indexId);
    }

    private void setLongField(String name, long value) throws Exception {
        Field f = CodeIndexService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.setLong(impl(), value);
    }

    private long getLongField(String name) throws Exception {
        Field f = CodeIndexService.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.getLong(impl());
    }
}
