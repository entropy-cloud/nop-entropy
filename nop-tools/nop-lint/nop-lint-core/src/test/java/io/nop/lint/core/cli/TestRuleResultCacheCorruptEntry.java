package io.nop.lint.core.cli;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.engine.Diagnostic;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.core.node.SourceRange;
import io.nop.treesitter.parser.incremental.TSInputEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cache artifact's fail-closed contract at ENTRY level (plan 11 Phase 3,
 * audit finding C10b): a structurally broken entry — missing diagnostics, a
 * mis-typed field — aborts the run with a diagnosable "cache entry corrupt"
 * exception, never a bare NPE/CCE with lost context. Legit entries written
 * by {@code put} keep loading (the fingerprint/hex semantics did not move).
 */
class TestRuleResultCacheCorruptEntry {

    private static final byte[] CONTENT = "class Warn {\n}\n".getBytes(StandardCharsets.UTF_8);

    private RuleResultCache cacheWithOneEntry(Path cache) throws Exception {
        RuleResultCache cache0 = RuleResultCache.load(cache, "fp-test");
        cache0.put("src/Warn.java", CONTENT,
                List.of(new Diagnostic("demo/r", "warning", "msg",
                        new SourceRange(0, 5))));
        cache0.save();
        return RuleResultCache.load(cache, "fp-test");
    }

    @Test
    void missingDiagnosticsFieldFailsClosed(@TempDir Path dir) throws Exception {
        Path cache = dir.resolve("cache.json");
        cacheWithOneEntry(cache);
        // break the entry: drop the whole diagnostics list, keep hash + path
        String json = Files.readString(cache);
        json = json.replaceAll("\"diagnostics\":\\[[^]]*\\]", "\"diagnostics\": null");
        Files.writeString(cache, json);
        RuleResultCache reloaded = RuleResultCache.load(cache, "fp-test");

        NopLintException ex = assertThrows(NopLintException.class,
                () -> reloaded.get("src/Warn.java", CONTENT));
        assertTrue(ex.getMessage().contains("cache entry corrupt"), ex.getMessage());
        assertTrue(ex.getMessage().contains("diagnostics"), ex.getMessage());
    }

    @Test
    void misTypedEntryFieldFailsClosed(@TempDir Path dir) throws Exception {
        Path cache = dir.resolve("cache.json");
        cacheWithOneEntry(cache);
        // break one field's type: startByte becomes a string
        String json = Files.readString(cache);
        json = json.replace("\"startByte\":0", "\"startByte\":\"zero\"");
        Files.writeString(cache, json);
        RuleResultCache reloaded = RuleResultCache.load(cache, "fp-test");

        NopLintException ex = assertThrows(NopLintException.class,
                () -> reloaded.get("src/Warn.java", CONTENT));
        assertTrue(ex.getMessage().contains("cache entry corrupt"), ex.getMessage());
        assertTrue(ex.getMessage().contains("startByte"), ex.getMessage());
    }

    @Test
    void legitEntryStillReplays(@TempDir Path dir) throws Exception {
        Path cache = dir.resolve("cache.json");
        RuleResultCache reloaded = cacheWithOneEntry(cache);

        List<Diagnostic> replayed = reloaded.get("src/Warn.java", CONTENT).diagnostics();
        assertEquals(1, replayed.size());
        assertEquals("demo/r", replayed.get(0).ruleId());
        assertEquals(new SourceRange(0, 5), replayed.get(0).range());
    }
}
