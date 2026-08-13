package io.nop.ai.agent.gate;

import io.nop.ai.api.secure.SecureDefault;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate ① (INV-1, plan 2026-08-12-1120-2 Phase 2): every {@code Default*} class
 * in the I0 target set table (invariant-catalog §3.1, agent+core+toolkit subset
 * covered here; the shell subset is covered by
 * {@code TestInvariantGate1SecureDefaultShell}) must declare secure-default
 * configuration.
 *
 * <p>Declaration form (I1 Phase 1 Decision D3): the class-level marker
 * annotation {@link SecureDefault} (declaration-only, no behaviour). This gate
 * verifies <b>declaration presence + category attributes only</b> — it does
 * NOT verify behaviour semantics (behaviour verification = I2 wiring
 * spot-checks and the committed behaviour tests such as TestSecureByDefault /
 * TestLayer23SecureDefaults).
 *
 * <p>Known-gaps mechanism (catalog §3.5): a table entry passes iff it is
 * annotated <b>or</b> registered in {@code gate-gaps.yaml} (family
 * {@code gate-1-default-secure}). First-run instances without the annotation
 * are all registered (I1 contract introduction; I4 adds the annotations).
 *
 * <p>Table completeness: Default* classes found in this module's
 * {@code src/main} tree but missing from the gate table are a hard failure
 * (new Default* class must be added to catalog §3.1 before the gate can cover
 * it — Loop Rule trigger). The negative tests prove the gate actually
 * intercepts (no silent pass).
 */
public class TestInvariantGate1SecureDefault {

    private static final String GAP_FAMILY = "gate-1-default-secure";

    /**
     * AR-8 fix (plan 2026-08-12-2311-2): negative-test fixtures live in the
     * module build dir ({@code <module>/target/gate-fixture}) instead of
     * {@code src/main} — an interrupted run (kill -9 / surefire crash) leaves
     * no residue in the shipped artifact nor in the gate-1 source-tree scan.
     * Written / scanned / deleted through this single shared constant so the
     * three sites cannot drift apart.
     */
    private static final Path FIXTURE_ROOT = moduleRoot().resolve("target/gate-fixture");

    /**
     * I0 target set table §3.1 — classes visible on the nop-ai-agent test
     * classpath (agent 22 + core 8 + toolkit 1 = 31; shell's 2 are covered by
     * the shell-side gate test). Must equal the reproduction command output
     * (catalog §4, "Default* 类表" find command) minus the shell rows.
     */
    static final List<String> TABLE = List.of(
            "io.nop.ai.agent.engine.DefaultAgentEngine",
            "io.nop.ai.agent.engine.DefaultAgentEngineConfig",
            "io.nop.ai.agent.engine.DefaultAgentEventPublisher",
            "io.nop.ai.agent.fencing.DefaultFencingTokenService",
            "io.nop.ai.agent.hook.DefaultHookRegistry",
            "io.nop.ai.agent.quota.DefaultResourceGuard",
            "io.nop.ai.agent.reliability.DefaultWaitCoordinator",
            "io.nop.ai.agent.runtime.recovery.DefaultOrphanRecoveryHandler",
            "io.nop.ai.agent.runtime.recovery.DefaultSessionTimeoutHandler",
            "io.nop.ai.agent.runtime.recovery.DefaultTeamTaskRecoveryHandler",
            "io.nop.ai.agent.security.DefaultApprovalGate",
            "io.nop.ai.agent.security.DefaultContentTrustEvaluator",
            "io.nop.ai.agent.security.DefaultDenialLedger",
            "io.nop.ai.agent.security.DefaultLevelHintsProducer",
            "io.nop.ai.agent.security.DefaultPathAccessChecker",
            "io.nop.ai.agent.security.DefaultPermissionMatrix",
            "io.nop.ai.agent.security.DefaultPermissionProvider",
            "io.nop.ai.agent.security.DefaultPostDenialGuard",
            "io.nop.ai.agent.security.DefaultSecurityLevelResolver",
            "io.nop.ai.agent.security.DefaultToolAccessChecker",
            "io.nop.ai.agent.team.DefaultMemberSpawner",
            "io.nop.ai.agent.team.DefaultTeamAclChecker",
            "io.nop.ai.core.api.tool.DefaultAiChatFunctionTool",
            "io.nop.ai.core.api.tool.DefaultAiChatToolSet",
            "io.nop.ai.core.persist.DefaultAiChatExchangePersister",
            "io.nop.ai.core.persist.DefaultAiChatResponseCache",
            "io.nop.ai.core.prompt.DefaultSystemPromptLoader",
            "io.nop.ai.core.service.DefaultAiChatService",
            "io.nop.ai.core.service.DefaultAiChatSession",
            "io.nop.ai.core.service.DefaultChatLogger",
            "io.nop.ai.toolkit.executor.DefaultToolExecutorProvider"
    );

    /**
     * The nop-ai-agent module's own Default* classes (I0 table §3.1 rows
     * 1-22). Used for table-completeness against this module's source tree;
     * the declaration check uses {@link #TABLE} (agent+core+toolkit, all
     * visible on this test classpath).
     */
    static final List<String> MODULE_TABLE = List.of(
            "io.nop.ai.agent.engine.DefaultAgentEngine",
            "io.nop.ai.agent.engine.DefaultAgentEngineConfig",
            "io.nop.ai.agent.engine.DefaultAgentEventPublisher",
            "io.nop.ai.agent.fencing.DefaultFencingTokenService",
            "io.nop.ai.agent.hook.DefaultHookRegistry",
            "io.nop.ai.agent.quota.DefaultResourceGuard",
            "io.nop.ai.agent.reliability.DefaultWaitCoordinator",
            "io.nop.ai.agent.runtime.recovery.DefaultOrphanRecoveryHandler",
            "io.nop.ai.agent.runtime.recovery.DefaultSessionTimeoutHandler",
            "io.nop.ai.agent.runtime.recovery.DefaultTeamTaskRecoveryHandler",
            "io.nop.ai.agent.security.DefaultApprovalGate",
            "io.nop.ai.agent.security.DefaultContentTrustEvaluator",
            "io.nop.ai.agent.security.DefaultDenialLedger",
            "io.nop.ai.agent.security.DefaultLevelHintsProducer",
            "io.nop.ai.agent.security.DefaultPathAccessChecker",
            "io.nop.ai.agent.security.DefaultPermissionMatrix",
            "io.nop.ai.agent.security.DefaultPermissionProvider",
            "io.nop.ai.agent.security.DefaultPostDenialGuard",
            "io.nop.ai.agent.security.DefaultSecurityLevelResolver",
            "io.nop.ai.agent.security.DefaultToolAccessChecker",
            "io.nop.ai.agent.team.DefaultMemberSpawner",
            "io.nop.ai.agent.team.DefaultTeamAclChecker"
    );

    /**
     * The decision rule shared by the gate and its negative tests: a table
     * entry satisfies the gate iff it is annotated or registered in the
     * known-gaps list (same family).
     */
    static boolean satisfiesGate(String fqcn, boolean annotated, Set<String> gapped) {
        return annotated || gapped.contains(fqcn);
    }

    static Path repoRoot() {
        return InvariantGateSupport.repoRoot();
    }

    static Path moduleRoot() {
        return InvariantGateSupport.moduleRoot("nop-ai-agent");
    }

    static Set<String> loadGappedInstances(String family) {
        return InvariantGateSupport.loadGappedInstances(family);
    }

    static JavaClasses importTableClasses() {
        return new ClassFileImporter()
                .importPackages("io.nop.ai.agent..", "io.nop.ai.core..",
                        "io.nop.ai.toolkit..", "io.nop.ai.api.secure..");
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

    /**
     * Source-tree mechanical re-query of the I0 reproduction command: every
     * {@code Default*.java} under this module's {@code src/main/java}, with
     * the I0 exclusion rules ({@code /test/}, {@code /_gen/}, {@code /target/}
     * path segments — note this excludes the guardrail-test grader whose
     * package path contains a {@code test} segment, per I0 edge ruling).
     */
    static Set<String> scanSourceTree() throws IOException {
        return scanSourceTree(moduleRoot().resolve("src/main/java"));
    }

    /**
     * Parameterized scan root (AR-8 fix): the negative fixtures now live under
     * {@code <module>/target/gate-fixture} (build dir), so the negative tests
     * scan that root instead of mutating {@code src/main}. Exclusion rules are
     * reused verbatim — the gate's semantics are unchanged.
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
                    .map(p -> toFqcn(root, p))
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

    private static String toFqcn(Path root, Path file) {
        String rel = root.relativize(file).toString()
                .replace(".java", "")
                .replace(java.io.File.separatorChar, '.');
        return rel;
    }

    // ========================================================================
    // Gate assertions
    // ========================================================================

    @Test
    void tableClassesDeclareSecureDefaultOrAreRegistered() {
        Set<String> gapped = loadGappedInstances(GAP_FAMILY);
        List<String> violations = checkTable(TABLE, importTableClasses(), gapped);
        assertTrue(violations.isEmpty(),
                "Gate 1 (secure-default declaration) violations:\n" + String.join("\n", violations));
    }

    @Test
    void tableCompleteness_matchesSourceTree() {
        try {
            Set<String> sourceDefaults = scanSourceTree();
            Set<String> tableSet = new HashSet<>(MODULE_TABLE);
            Set<String> unregistered = new TreeSet<>(sourceDefaults);
            unregistered.removeAll(tableSet);
            assertTrue(unregistered.isEmpty(),
                    "Default* classes found in src/main but NOT in the gate table — must be added to "
                            + "invariant-catalog §3.1 before the gate can cover them (Loop Rule): " + unregistered);
            Set<String> missing = new TreeSet<>(tableSet);
            missing.removeAll(sourceDefaults);
            assertTrue(missing.isEmpty(), "Gate table entries missing from source tree: " + missing);
        } catch (IOException e) {
            throw new IllegalStateException("source-tree scan failed", e);
        }
    }

    // ========================================================================
    // Anti-hollow: the gate must actually intercept (no silent pass)
    // ========================================================================

    /**
     * End-to-end negative: a temporary Default* class dropped into the
     * {@code target/gate-fixture} tree (no annotation, no table entry) must be
     * reported as a completeness violation — proving a developer adding a new
     * Default* class is intercepted by the gate. The fixture mirrors the
     * package-relative layout of {@code src/main} so the FQCN assertion is
     * byte-identical to the pre-AR-8 form.
     */
    @Test
    void negative_newUndeclaredDefaultClassIsRejected() throws Exception {
        Path fixtureDir = FIXTURE_ROOT.resolve("io/nop/ai/agent/gatefixture");
        Path fixture = fixtureDir.resolve("DefaultGateNegativeFixture.java");
        writeFixture(fixture, false);
        try {
            Set<String> sourceDefaults = scanSourceTree(FIXTURE_ROOT);
            Set<String> unregistered = new TreeSet<>(sourceDefaults);
            unregistered.removeAll(TABLE);
            assertTrue(unregistered.contains("io.nop.ai.agent.gatefixture.DefaultGateNegativeFixture"),
                    "a new Default* class without a table entry must be flagged as unregistered; got: "
                            + unregistered);
        } finally {
            deleteFixture(FIXTURE_ROOT);
        }
    }

    /**
     * End-to-end negative 2: even an annotated Default* class must still be in
     * the target set table (table completeness is independent of the
     * declaration) — annotation alone is not a bypass.
     */
    @Test
    void negative_annotatedDefaultClassStillRequiresTableEntry() throws Exception {
        Path fixtureDir = FIXTURE_ROOT.resolve("io/nop/ai/agent/gatefixture");
        Path fixture = fixtureDir.resolve("DefaultGateAnnotatedFixture.java");
        writeFixture(fixture, true);
        try {
            Set<String> sourceDefaults = scanSourceTree(FIXTURE_ROOT);
            Set<String> unregistered = new TreeSet<>(sourceDefaults);
            unregistered.removeAll(TABLE);
            assertTrue(unregistered.contains("io.nop.ai.agent.gatefixture.DefaultGateAnnotatedFixture"),
                    "an annotated Default* class not in the table must still be flagged (completeness); got: "
                            + unregistered);
        } finally {
            deleteFixture(FIXTURE_ROOT);
        }
    }

    /**
     * Decision-rule negative: a class with neither annotation nor gap
     * registration must be rejected (the case the end-to-end tests cannot
     * construct without mutating the catalog table). Also proves the positive
     * arms of the rule (annotation OR registration suffice).
     *
     * <p>Since I4 every Default* table class carries {@code @SecureDefault},
     * so the victim here is an un-annotated non-Default class from the
     * imported packages (the decision rule itself is class-name agnostic).
     */
    @Test
    void negative_logicRejectsInTableUndeclaredUnregistered() {
        String victim = "io.nop.ai.agent.engine.AgentExecutionContext";
        Set<String> gapped = loadGappedInstances(GAP_FAMILY);
        List<String> violations = checkTable(List.of(victim), importTableClasses(), gapped);
        assertTrue(violations.stream().anyMatch(v -> v.contains(victim)),
                "class without annotation and without gap registration must be a violation");
        assertTrue(satisfiesGate(victim, true, Set.of()),
                "annotation must satisfy the gate");
        assertTrue(satisfiesGate(victim, false, Set.of(victim)),
                "gap registration must satisfy the gate");
        assertFalse(satisfiesGate(victim, false, Set.of()),
                "neither annotation nor registration must fail the gate");
    }

    // ========================================================================
    // Fixture helpers
    // ========================================================================

    private static void writeFixture(Path fixture, boolean annotated) throws IOException {
        Files.createDirectories(fixture.getParent());
        String annotation = annotated ? "@io.nop.ai.api.secure.SecureDefault\n" : "";
        Files.writeString(fixture, "package io.nop.ai.agent.gatefixture;\n\n"
                + annotation
                + "public class " + fixture.getFileName().toString().replace(".java", "") + " {\n"
                + "}\n");
    }

    /**
     * Recursive delete of the whole fixture root (AR-8 upgrade — no longer
     * relies on single-file deletion). Also removes any interrupted-run
     * residue (kill -9) left under {@code target/gate-fixture}.
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
