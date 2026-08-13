package io.nop.ai.shell.gate;

import io.nop.ai.api.secure.SecureDefault;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate ① (INV-1) — nop-ai-shell subset: the 2 Default* classes of the I0
 * target set table §3.1 that live in nop-ai-shell (not visible from the
 * nop-ai-agent test classpath, which covers agent+core+toolkit — I1 Phase 1
 * Decision D2, per-module rules).
 *
 * <p>Same contract as {@code TestInvariantGate1SecureDefault} in nop-ai-agent:
 * declaration form = {@link SecureDefault} marker annotation or known-gaps
 * registration (family {@code gate-1-default-secure}); table completeness via
 * source-tree mechanical re-query; negative tests prove the gate intercepts.
 */
public class TestInvariantGate1SecureDefaultShell {

    private static final String GAP_FAMILY = "gate-1-default-secure";

    /**
     * AR-8 fix (plan 2026-08-12-2311-2): negative-test fixtures live in the
     * module build dir ({@code <module>/target/gate-fixture}) instead of
     * {@code src/main}. Shared by the write / scan / delete sites.
     */
    private static final Path FIXTURE_ROOT = moduleRoot().resolve("target/gate-fixture");

    /** I0 target set table §3.1 — nop-ai-shell rows (2). */
    static final List<String> TABLE = List.of(
            "io.nop.ai.shell.checker.DefaultCommandChecker",
            "io.nop.ai.shell.commands.DefaultShellExecutionContext"
    );

    static boolean satisfiesGate(String fqcn, boolean annotated, Set<String> gapped) {
        return annotated || gapped.contains(fqcn);
    }

    static Path repoRoot() {
        String basedir = System.getProperty("basedir");
        Path base = basedir != null ? Path.of(basedir) : Path.of("").toAbsolutePath();
        return base.normalize().resolve("../..").normalize();
    }

    static Path moduleRoot() {
        return repoRoot().resolve("nop-ai/nop-ai-shell");
    }

    static Set<String> loadGappedInstances(String family) {
        Path gapFile = repoRoot().resolve("ai-dev/audits/nop-ai-invariants/gate-gaps.yaml");
        if (!Files.exists(gapFile)) {
            throw new IllegalStateException("known-gaps file missing: " + gapFile);
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

    static JavaClasses importTableClasses() {
        return new ClassFileImporter()
                .importPackages("io.nop.ai.shell..", "io.nop.ai.api.secure..");
    }

    static List<String> checkTable(List<String> table, JavaClasses classes, Set<String> gapped) {
        List<String> violations = new ArrayList<>();
        for (String fqcn : table) {
            JavaClass c = classes.get(fqcn);
            if (c == null) {
                violations.add(fqcn + ": not importable on the test classpath (module dependency gap)");
                continue;
            }
            boolean annotated = c.isAnnotatedWith(SecureDefault.class);
            if (!satisfiesGate(fqcn, annotated, gapped)) {
                violations.add(fqcn + ": lacks @" + SecureDefault.class.getSimpleName()
                        + " and is not registered in gate-gaps.yaml family '" + GAP_FAMILY + "'");
            }
        }
        return violations;
    }

    static Set<String> scanSourceTree() throws IOException {
        return scanSourceTree(moduleRoot().resolve("src/main/java"));
    }

    /**
     * Parameterized scan root (AR-8 fix): the shell negative fixture lives
     * under {@code <module>/target/gate-fixture}; exclusion rules are reused
     * verbatim.
     */
    static Set<String> scanSourceTree(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return Set.of();
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith("Default")
                            && p.getFileName().toString().endsWith(".java"))
                    .filter(p -> !hasExcludedSegment(root.relativize(p)))
                    .map(p -> root.relativize(p).toString().replace(".java", "")
                            .replace(java.io.File.separatorChar, '.'))
                    .collect(Collectors.toSet());
        }
    }

    private static boolean hasExcludedSegment(Path rel) {
        for (Path segment : rel) {
            String name = segment.toString();
            if ("test".equals(name) || "_gen".equals(name) || "target".equals(name)) {
                return true;
            }
        }
        return false;
    }

    // ========================================================================
    // Gate assertions
    // ========================================================================

    @Test
    void tableClassesDeclareSecureDefaultOrAreRegistered() {
        Set<String> gapped = loadGappedInstances(GAP_FAMILY);
        List<String> violations = checkTable(TABLE, importTableClasses(), gapped);
        assertTrue(violations.isEmpty(),
                "Gate 1 (shell subset) violations:\n" + String.join("\n", violations));
    }

    @Test
    void tableCompleteness_matchesSourceTree() {
        try {
            Set<String> sourceDefaults = scanSourceTree();
            Set<String> tableSet = new HashSet<>(TABLE);
            Set<String> unregistered = new TreeSet<>(sourceDefaults);
            unregistered.removeAll(tableSet);
            assertTrue(unregistered.isEmpty(),
                    "Default* classes found in nop-ai-shell src/main but NOT in the gate table: "
                            + unregistered);
            Set<String> missing = new TreeSet<>(tableSet);
            missing.removeAll(sourceDefaults);
            assertTrue(missing.isEmpty(), "Gate table entries missing from source tree: " + missing);
        } catch (IOException e) {
            throw new IllegalStateException("source-tree scan failed", e);
        }
    }

    /**
     * End-to-end negative: a temporary Default* class in
     * {@code target/gate-fixture} must be flagged (completeness) — proves the
     * shell-side gate intercepts new classes too.
     */
    @Test
    void negative_newUndeclaredDefaultClassIsRejected() throws Exception {
        Path fixtureDir = FIXTURE_ROOT.resolve("io/nop/ai/shell/gatefixture");
        Path fixture = fixtureDir.resolve("DefaultShellGateNegativeFixture.java");
        writeFixture(fixture);
        try {
            Set<String> sourceDefaults = scanSourceTree(FIXTURE_ROOT);
            Set<String> unregistered = new TreeSet<>(sourceDefaults);
            unregistered.removeAll(TABLE);
            assertTrue(unregistered.contains("io.nop.ai.shell.gatefixture.DefaultShellGateNegativeFixture"),
                    "a new Default* class without a table entry must be flagged as unregistered; got: "
                            + unregistered);
        } finally {
            deleteFixture(FIXTURE_ROOT);
        }
    }

    private static void writeFixture(Path fixture) throws IOException {
        Files.createDirectories(fixture.getParent());
        Files.writeString(fixture, "package io.nop.ai.shell.gatefixture;\n\n"
                + "public class " + fixture.getFileName().toString().replace(".java", "") + " {\n"
                + "}\n");
    }

    /**
     * Recursive delete of the whole fixture root (AR-8 upgrade — the shell
     * side previously inlined single-file cleanup in finally).
     */
    private static void deleteFixture(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new UncheckedIOException("failed to delete fixture: " + p, e);
                }
            }
        }
    }
}
