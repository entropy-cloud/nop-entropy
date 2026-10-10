/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.validate;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.impl.FileResource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * xdsl-full-coverage roadmap WI4 (plan 2350): the L2 corpus gate. Every
 * `.stream.xml` under nop-stream (target dirs excluded, the _delta layer included —
 * it is a live model layer) goes through the model-aware linter; the registered
 * whitelists below ARE the retire-on-release ledger:
 * <ul>
 *   <li>WARNING faces are whitelisted per SITE (path suffix + severity + errorCode +
 *       target — never by message text: bean names and element ids change); every
 *       entry carries its roadmap release WI, so WI5–WI16 releases shrink the table
 *       in the same change (release three-step).</li>
 *   <li>FAIL faces are whitelisted per FILE: the lint folds the conf-validator
 *       verdict, and the corpus legitimately contains by-design negative fixtures
 *       plus bean-referencing models whose beans live in test containers that the
 *       gate's null resolver (global container) cannot see.</li>
 *   <li>The R6/R13/R14 SKIP notes are dormant-check annotations, not violations —
 *       filtered explicitly (activation duty: WI15 / WI21).</li>
 * </ul>
 * A new finding on any corpus file fails this test with a diff-style message naming
 * the file, the code, and the target.
 */
class TestXdslCorpusLintGate {

    private static Path repoRoot;

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
        repoRoot = findRepoRoot();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * Mirror of InvariantTableCompleteness.findInventoryFile — surefire runs with
     * basedir = module dir; walk up (bounded) until the repo marker dir shows up.
     */
    static Path findRepoRoot() {
        String prop = System.getProperty("nop.xdsl.gate.root");
        if (prop != null) {
            return Paths.get(prop).toAbsolutePath().normalize();
        }
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8; i++) {
            if (Files.isDirectory(dir.resolve("ai-dev"))) {
                return dir;
            }
            Path parent = dir.getParent();
            if (parent == null) {
                break;
            }
            dir = parent;
        }
        throw new IllegalStateException("repo root not found (searched up from "
                + Paths.get("").toAbsolutePath() + "); no silent skip — set -Dnop.xdsl.gate.root=<abs path>");
    }

    /**
     * WARNING-site whitelist: key = "pathSuffix|errorCode|target". Every entry names
     * its roadmap release WI — retire together with the builder fail-fast (release
     * three-step).
     */
    private static final java.util.Set<String> WARNING_WHITELIST = java.util.Set.of(
            // ---- R4 存量（模型使用 window/aggregate/join 未声明 <schemas>；消号 = 补
            //      schemas 声明；target 为模型名）----
            "wi21-stability.stream.xml|nop.err.stream.invalid-arg|wi21-stability",
            "test-dim-lookup-pipeline.stream.xml|nop.err.stream.invalid-arg|test-dim-lookup-pipeline",
            "fraud-s2-file.stream.xml|nop.err.stream.invalid-arg|fraud-s2-file-pipeline",
            "fraud-s1-cdc.stream.xml|nop.err.stream.invalid-arg|fraud-s1-cdc-pipeline",
            "topology2-window-aggregation.stream.xml|nop.err.stream.invalid-arg|topology2-window-aggregation",
            "test-join-pipeline-asym.stream.xml|nop.err.stream.invalid-arg|test-join-pipeline-asym",
            "test-join-pipeline.stream.xml|nop.err.stream.invalid-arg|test-join-pipeline",
            // ---- R5 存量（roadmap R5：window 无水位供给；消号 = 补 timestampsAndWatermarks；
            //      与 R4 同模型同键，Set 语义去重）----
            "test-param-agg.stream.xml|nop.err.stream.invalid-arg|test-param-agg",
            // ---- WI21-WI26 存量（U2/plan 2375 全量回归发现：E 场景 fixture 落地时
            //      只跑了场景定向套件、未重跑本门禁——R2 process 启发/R4 模型级 schemas/
            //      R5 window 水位供给的 WARNING 提示未登记。各场景行为面由 TestXdslScenarioE1-E12
            //      亲跑钉定（绿）；消号 = 补 tsw/schemas 声明或按 R2 裁定登记。
            //      注：E3/E8 在全量套件上下文中不产出该提示（隔离单跑会产出——lint 的
            //      bean 面上下文差异），故不登记，见 STALE 双向检查）----
            "xdsl-e1-statemachine.stream.xml|nop.err.stream.invalid-arg|xdsl-e1-statemachine",
            "xdsl-e2-topspeed.stream.xml|nop.err.stream.invalid-arg|xdsl-e2-topspeed",
            "xdsl-e4-windowjoin.stream.xml|nop.err.stream.invalid-arg|xdsl-e4-windowjoin",
            "xdsl-e5-newstimer.stream.xml|nop.err.stream.invalid-arg|xdsl-e5-newstimer",
            "xdsl-e6-async.stream.xml|nop.err.stream.invalid-arg|xdsl-e6-async",
            "xdsl-e7-tablesql.stream.xml|nop.err.stream.invalid-arg|xdsl-e7-tablesql",
            "xdsl-e9-tdengine-window.stream.xml|nop.err.stream.invalid-arg|xdsl-e9-tdengine-window",
            "xdsl-e10-hazelcast-window.stream.xml|nop.err.stream.invalid-arg|xdsl-e10-hazelcast-window",
            "xdsl-e11-enrichment.stream.xml|nop.err.stream.invalid-arg|xdsl-e11-enrichment",
            "xdsl-e12-early-tail.stream.xml|nop.err.stream.invalid-arg|xdsl-e12-early-tail",
            // ---- R12 存量（WI12 放行后载体更新：streams 已移除，coders 为 pinned
            //      Java-only 面，delta fail-fast fixture 的 NOT_IMPLEMENTED 键随之换载）----
            "test-delta-failfast-extends.stream.xml|nop.err.stream.not-implemented|<coders>",
            "test-loc-bad-source-config.stream.xml|nop.err.stream.not-implemented|source src");

    /**
     * FAIL-face whitelist: test-fixture directories wholesale (their models
     * legitimately reference test-container beans or encode by-design violations the
     * folded conf-validator must reject), plus the main-resource demo/template models
     * whose beans live in application containers the gate cannot see (registered
     * explicitly — a NEW main-resource FAIL file must be added here deliberately).
     */
    private static final List<String> FAIL_MAIN_RESOURCE_SUFFIXES = List.of(
            "demo/fraud-s1-cdc.stream.xml",       // bean cdcSource lives in the fraud app container
            "demo/fraud-s2-file.stream.xml",      // bean fileSource (same)
            "demo/fraud-s2-file-delta.stream.xml",// x:extends base resolves through the app VFS, not the flow test classpath
            "quickstart/topology2-window-aggregation.stream.xml", // bean tradeSource
            "quickstart/topology3-cep-pattern.stream.xml");       // bean cepSource

    @Test
    void corpusFindingsStayWithinTheRegisteredWhitelist() throws IOException {
        List<Path> corpus = collectCorpus();
        assertTrue(corpus.size() >= 30,
                "corpus shrunk below the registered baseline scale (" + corpus.size() + ") — "
                        + "check the collector or the ledger");
        Map<String, List<String>> violations = new LinkedHashMap<>();
        List<String> registeredWarnings = new ArrayList<>();
        List<String> registeredFails = new ArrayList<>();
        for (Path file : corpus) {
            String rel = repoRoot.relativize(file).toString();
            IResource resource = resourceFor(file);
            StreamConfValidationReport report = new StreamModelLinter().lint(resource, null, false);
            for (ValidationIssue issue : report.getIssues()) {
                if (issue.getSeverity() == ValidationIssue.Severity.SKIP) {
                    continue; // R6/R13/R14 dormant notes — activation duty WI15/WI21
                }
                String key = rel.substring(rel.lastIndexOf('/') + 1) + "|"
                        + issue.getErrorCode() + "|" + issue.getTarget();
                if (issue.getSeverity() == ValidationIssue.Severity.WARNING) {
                    registeredWarnings.add(key);
                    if (!WARNING_WHITELIST.contains(key)) {
                        violations.computeIfAbsent(rel, k -> new ArrayList<>())
                                .add("UNWHITELISTED WARNING " + key);
                    }
                } else {
                    registeredFails.add(rel);
                    if (!isFailWhitelisted(rel)) {
                        violations.computeIfAbsent(rel, k -> new ArrayList<>())
                                .add("UNWHITELISTED FAIL " + issue.getErrorCode() + " — "
                                        + issue.getMessage());
                    }
                }
            }
        }
        // the whitelist may never drift from the corpus silently either direction:
        // a stale whitelist entry (violation fixed without retiring the line) fails
        // the gate and forces the ledger to shrink in the same change.
        java.util.Set<String> liveKeys = new java.util.HashSet<>(registeredWarnings);
        for (String registered : WARNING_WHITELIST) {
            if (!liveKeys.contains(registered)) {
                violations.computeIfAbsent("<whitelist>", k -> new ArrayList<>())
                        .add("STALE WARNING ENTRY (fix landed? retire it): " + registered);
            }
        }
        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder("corpus lint gate violations:\n");
            violations.forEach((file, list) -> list.forEach(v -> sb.append("  ").append(file)
                    .append(" :: ").append(v).append('\n')));
            sb.append("消号义务: fix the finding or register it here WITH its release WI, ")
                    .append("then retire the entry in the same change (L1: regenerate ")
                    .append("ai-dev/tools/nop-lint-xdsl-baseline.txt).");
            throw new AssertionError(sb.toString());
        }
    }

    private boolean isFailWhitelisted(String rel) {
        // test fixtures wholesale: bean references resolve in their module's test
        // containers, and by-design negative fixtures fail construction on purpose
        if (rel.contains("/src/test/resources/")) {
            return true;
        }
        for (String suffix : FAIL_MAIN_RESOURCE_SUFFIXES) {
            if (rel.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private List<Path> collectCorpus() throws IOException {
        List<Path> result = new ArrayList<>();
        Path base = repoRoot.resolve("nop-stream");
        try (Stream<Path> walk = Files.walk(base)) {
            walk.filter(p -> p.getFileName().toString().endsWith(".stream.xml"))
                    .filter(p -> !p.toString().contains("/target/"))
                    .forEach(result::add);
        }
        return result;
    }

    /**
     * Files under a `_vfs/` ancestor load through the VFS logical path (production
     * resolution — delta layering via x:extends works); the rest load as plain file
     * resources.
     */
    private IResource resourceFor(Path file) {
        Path root = repoRoot;
        List<Path> parts = new ArrayList<>();
        Path cur = file;
        boolean sawVfs = false;
        while (!cur.equals(root) && cur.getParent() != null) {
            parts.add(0, cur.getFileName());
            if (cur.getFileName().toString().equals("_vfs")) {
                sawVfs = true;
                break;
            }
            cur = cur.getParent();
        }
        if (sawVfs) {
            String logical = "/" + String.join("/", parts.stream().skip(1)
                    .map(Path::toString).toList());
            IResource res = VirtualFileSystem.instance().getResource(logical);
            if (res != null && res.exists()) {
                return res;
            }
        }
        return new FileResource(file.toFile());
    }
}
