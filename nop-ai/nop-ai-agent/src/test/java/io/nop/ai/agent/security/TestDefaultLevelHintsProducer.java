package io.nop.ai.agent.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 tests: verifies {@link DefaultLevelHintsProducer} produces
 * semantically-distinct {@link LevelHints} (not an all-false stub) across a
 * range of tool-name × argument combinations, and is robust to null / empty
 * inputs.
 */
public class TestDefaultLevelHintsProducer {

    private final DefaultLevelHintsProducer producer = new DefaultLevelHintsProducer();

    @TempDir
    Path tempDir;

    private File workDir() {
        return tempDir.toFile();
    }

    // ========================================================================
    // Anti-Hollow: hints are semantically distinct across inputs
    // ========================================================================

    @Test
    void readFileInsideWorkspaceIsTrustedLowRisk() {
        Map<String, Object> args = new HashMap<>();
        args.put("path", new File(workDir(), "data.txt").getAbsolutePath());

        LevelHints hints = producer.produce("fs.read", args, workDir(), null);

        assertTrue(hints.isTrustedSource(), "agent-generated tool call is a trusted source");
        assertFalse(hints.isWritesOutsideWorkspace(), "read inside workspace is not outside");
        assertFalse(hints.isHighImpact(), "fs.read is not high-impact");
        assertFalse(hints.isNeedsNetwork(), "fs.read needs no network");
        assertFalse(hints.isCrossesTrustBoundary(), "conservatively false");
    }

    @Test
    void writeFileOutsideWorkspaceSetsWritesOutside() {
        Map<String, Object> args = new HashMap<>();
        args.put("file", "/etc/some/outside/path.txt");

        LevelHints hints = producer.produce("fs.write", args, workDir(), null);

        assertTrue(hints.isWritesOutsideWorkspace(),
                "absolute path outside workDir must set writesOutsideWorkspace");
    }

    @Test
    void relativePathInsideWorkspaceIsNotOutside() {
        Map<String, Object> args = new HashMap<>();
        args.put("file", "relative/output.txt");

        LevelHints hints = producer.produce("fs.write", args, workDir(), null);

        assertFalse(hints.isWritesOutsideWorkspace(),
                "relative path resolved against workDir is inside the workspace");
    }

    @Test
    void shellExecIsHighImpact() {
        LevelHints hints = producer.produce("shell_exec", Map.of(), workDir(), null);

        assertTrue(hints.isHighImpact(), "shell_exec must be classified as high-impact");
        assertFalse(hints.isNeedsNetwork(), "shell_exec is not a network tool");
    }

    @Test
    void bashIsHighImpact() {
        assertTrue(producer.produce("bash", Map.of(), workDir(), null).isHighImpact());
    }

    @Test
    void webFetchNeedsNetwork() {
        LevelHints hints = producer.produce("web_fetch", Map.of(), workDir(), null);

        assertTrue(hints.isNeedsNetwork(), "web_fetch must be classified as needs-network");
        assertFalse(hints.isHighImpact(), "web_fetch is not high-impact");
    }

    @Test
    void httpRequestNeedsNetwork() {
        assertTrue(producer.produce("http_request", Map.of(), workDir(), null).isNeedsNetwork());
    }

    @Test
    void hintsDifferAcrossToolCategories() {
        // Anti-Hollow: at least 3 hints (highImpact, needsNetwork, writesOutsideWorkspace)
        // must take distinct true/false values across inputs.
        LevelHints readHints = producer.produce("fs.read", Map.of("path", "in.txt"), workDir(), null);
        LevelHints shellHints = producer.produce("shell.exec", Map.of(), workDir(), null);
        LevelHints netHints = producer.produce("web.fetch", Map.of(), workDir(), null);
        LevelHints outsideHints = producer.produce(
                "fs.write", Map.of("file", "/etc/outside.txt"), workDir(), null);

        assertTrue(shellHints.isHighImpact() && !readHints.isHighImpact(),
                "highImpact must distinguish shell from fs.read");
        assertTrue(netHints.isNeedsNetwork() && !readHints.isNeedsNetwork(),
                "needsNetwork must distinguish web.fetch from fs.read");
        assertTrue(outsideHints.isWritesOutsideWorkspace() && !readHints.isWritesOutsideWorkspace(),
                "writesOutsideWorkspace must distinguish outside-path from inside-path");
        assertNotEquals(readHints, shellHints,
                "distinct inputs must produce distinct hints");
    }

    @Test
    void underscoreAndDotToolNamesMatchSameCategory() {
        assertTrue(producer.produce("shell_exec", Map.of(), workDir(), null).isHighImpact());
        assertTrue(producer.produce("shell.exec", Map.of(), workDir(), null).isHighImpact());
        assertTrue(producer.produce("SHELL_EXEC", Map.of(), workDir(), null).isHighImpact());
    }

    // ========================================================================
    // Robustness: null / empty inputs never throw
    // ========================================================================

    @Test
    void nullToolNameProducesHintsNotException() {
        LevelHints hints = producer.produce(null, Map.of(), workDir(), null);

        assertNotNull(hints);
        assertFalse(hints.isHighImpact(), "null tool name is not high-impact");
        assertFalse(hints.isNeedsNetwork(), "null tool name needs no network");
        assertTrue(hints.isTrustedSource(), "null tool name still trusted (agent origin)");
    }

    @Test
    void nullArgumentsProduceHintsNotException() {
        LevelHints hints = producer.produce("fs.write", null, workDir(), null);

        assertNotNull(hints);
        assertFalse(hints.isWritesOutsideWorkspace(), "null args → no outside-write signal");
    }

    @Test
    void emptyArgumentsProduceHintsNotException() {
        LevelHints hints = producer.produce("fs.read", new HashMap<>(), workDir(), null);

        assertNotNull(hints);
        assertFalse(hints.isWritesOutsideWorkspace());
    }

    @Test
    void nullWorkDirUsesJvmCwdAsBase() {
        // A relative path against JVM CWD is inside → not outside.
        Map<String, Object> args = new HashMap<>();
        args.put("file", "local/relative.txt");

        LevelHints hints = producer.produce("fs.write", args, null, null);

        assertFalse(hints.isWritesOutsideWorkspace(),
                "relative path against JVM CWD (workDir=null) is inside");
    }

    @Test
    void nullWorkDirAbsoluteOutsidePathIsOutside() {
        // Absolute path outside JVM CWD with workDir=null is outside.
        Map<String, Object> args = new HashMap<>();
        args.put("file", "/etc/some-definitely-outside-marker.txt");

        LevelHints hints = producer.produce("fs.write", args, null, null);

        assertTrue(hints.isWritesOutsideWorkspace(),
                "absolute path outside JVM CWD with workDir=null is outside");
    }

    @Test
    void nonStringPathArgIsIgnored() {
        Map<String, Object> args = new HashMap<>();
        args.put("file", 42); // non-string value

        LevelHints hints = producer.produce("fs.write", args, workDir(), null);

        assertFalse(hints.isWritesOutsideWorkspace(),
                "non-string path arg must be ignored, not throw");
    }

    @Test
    void emptyOrBlankPathArgIsIgnored() {
        Map<String, Object> args = new HashMap<>();
        args.put("file", "   ");

        LevelHints hints = producer.produce("fs.write", args, workDir(), null);

        assertFalse(hints.isWritesOutsideWorkspace(),
                "blank path arg must be ignored");
    }

    @Test
    void nullContentTrustEvaluatorInConstructorFallsBackToDefault() {
        // Constructor null fallback must not throw and must still evaluate trusted=true.
        DefaultLevelHintsProducer p = new DefaultLevelHintsProducer(null);
        assertTrue(p.produce("fs.read", Map.of(), workDir(), null).isTrustedSource(),
                "null evaluator in constructor falls back to DefaultContentTrustEvaluator");
    }

    @Test
    void produceNeverReturnsNull() {
        assertNotNull(producer.produce(null, null, null, null));
    }

    @Test
    void customEvaluatorOverridesTrustedSource() {
        IContentTrustEvaluator alwaysUntrusted = (origin, ctx) -> false;
        DefaultLevelHintsProducer p = new DefaultLevelHintsProducer(alwaysUntrusted);

        assertFalse(p.produce("fs.read", Map.of(), workDir(), null).isTrustedSource(),
                "custom untrusted evaluator must drive trustedSource to false");
    }

    @Test
    void toStringReflectsHints() {
        LevelHints hints = producer.produce("shell.exec", Map.of(), workDir(), null);
        assertTrue(hints.toString().contains("highImpact=true"),
                "toString must reflect the produced hints: " + hints);
    }

    @Test
    void workDirItselfIsNotOutside() {
        Map<String, Object> args = new HashMap<>();
        args.put("dir", workDir().getAbsolutePath());

        LevelHints hints = producer.produce("fs.list", args, workDir(), null);

        assertFalse(hints.isWritesOutsideWorkspace(),
                "the workspace directory itself is not 'outside'");
    }
}
