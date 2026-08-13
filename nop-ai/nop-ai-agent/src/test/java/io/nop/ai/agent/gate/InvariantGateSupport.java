package io.nop.ai.agent.gate;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared helpers for the nop-ai invariant gates (① secure-default
 * declaration, ② orchestration timeout declaration, ③ entry-point cleanup
 * symmetry). Resolves the repo root from the Maven {@code basedir} and reads
 * the known-gaps registry {@code ai-dev/audits/nop-ai-invariants/gate-gaps.yaml}
 * (catalog §3.5).
 */
final class InvariantGateSupport {

    private InvariantGateSupport() {
    }

    static Path repoRoot() {
        String basedir = System.getProperty("basedir");
        Path base = basedir != null ? Path.of(basedir) : Path.of("").toAbsolutePath();
        return base.normalize().resolve("../..").normalize();
    }

    static Path moduleRoot(String moduleDir) {
        return repoRoot().resolve("nop-ai").resolve(moduleDir);
    }

    static Path sourceFile(String moduleDir, String relativePath) {
        return moduleRoot(moduleDir).resolve("src/main/java/io/nop/ai/agent").resolve(relativePath);
    }

    static String readSource(String moduleDir, String relativePath) {
        Path file = sourceFile(moduleDir, relativePath);
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read source file: " + file, e);
        }
    }

    /**
     * Count occurrences of {@code marker} in the <b>code</b> of the given
     * source (plan 2026-08-12-2050-2 / AR-2 / WS4 branch-level gate
     * upgrade): {@code //} line comments, {@code /* *}{@code /} block
     * comments, string literals and char literals are stripped before
     * counting, so comments / log text cannot satisfy a declaration marker
     * (e.g. {@code MemberFanOutDispatcher} has "orTimeout" in javadoc — a
     * raw count would pass with one real marker + one comment marker).
     *
     * <p>Semantics are duplicated (not shared) with the self-contained
     * gateway gate test {@code TestInvariantGate2GatewayTimeout} — both
     * implementations must stay in sync (agent version is authoritative).
     *
     * <p>Limitation (recorded in the plan): marker counting cannot detect a
     * cancel-semantics regression ({@code orTimeout} stays present while the
     * raw-future cancel disappears) — the cancel contract is enforced by the
     * WS3 behavior tests instead.
     */
    static int countCodeOccurrences(String source, String marker) {
        StringBuilder code = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();
        boolean inBlockComment = false;
        while (i < n) {
            char c = source.charAt(i);
            char next = i + 1 < n ? source.charAt(i + 1) : '\0';
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i += 2;
                } else {
                    i++;
                }
                continue;
            }
            if (c == '/' && next == '/') {
                while (i < n && source.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (c == '/' && next == '*') {
                inBlockComment = true;
                i += 2;
                continue;
            }
            if (c == '"' || c == '\'') {
                char quote = c;
                i++;
                while (i < n && source.charAt(i) != quote) {
                    if (source.charAt(i) == '\\') {
                        i++;
                    }
                    i++;
                }
                i++;
                continue;
            }
            code.append(c);
            i++;
        }
        int count = 0;
        int idx = 0;
        while ((idx = code.indexOf(marker, idx)) >= 0) {
            count++;
            idx += marker.length();
        }
        return count;
    }

    static Set<String> loadGappedInstances(String family) {
        Path gapFile = repoRoot().resolve("ai-dev/audits/nop-ai-invariants/gate-gaps.yaml");
        if (!Files.exists(gapFile)) {
            throw new IllegalStateException("known-gaps file missing: " + gapFile
                    + " (gate cannot be evaluated without the registry)");
        }
        try {
            Map<String, Object> root = new Yaml().load(Files.readString(gapFile));
            Object fam = root.get(family);
            if (!(fam instanceof List)) {
                return Set.of();
            }
            Set<String> instances = new HashSet<>();
            for (Object raw : (List<?>) fam) {
                if (raw instanceof Map) {
                    Object instance = ((Map<?, ?>) raw).get("instance");
                    if (instance != null) {
                        instances.add(String.valueOf(instance));
                    }
                }
            }
            return instances;
        } catch (IOException e) {
            throw new IllegalStateException("failed to read known-gaps file: " + gapFile, e);
        }
    }
}
