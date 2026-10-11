package io.nop.refactor.graphql;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.GraphQLEngine;
import io.nop.refactor.core.FileEdit;
import io.nop.refactor.core.NonApply;
import io.nop.refactor.core.RefactorResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The WI8 closed-loop proof (plan nop-refactor/08, roadmap WI8 closure
 * responsibility): the p0 demo transform set drives the full AI loop —
 * {@code Refactor__previewRewrite} (dry-run payload, nothing written) then
 * {@code Refactor__applyRewrite} (atomic landing, byte-identical after
 * sources) — through the GraphQLEngine RPC path with the container-wired
 * bean ({@code TestNopRefactorGraphQL.BizModelSchemaLoader} harness form).
 * Every payload face is asserted: per-file edits (path/range/summary), the
 * four verification sub-fields, the six stats faces, and the exempted
 * legacy rewrite surfacing as {@code NonApply(OUT_OF_SCOPE)} — never a
 * silent drop. Determinism: identical inputs rerun produce identical
 * payloads (stateless re-execution, no server-side session).
 */
class TestRefactorP0ClosedLoop {

    private static final String P0_PREFIX = "/test/lint/refactor-p0";
    private static final Path FIXTURES = Path.of(
            "src/test/resources/_vfs/test/lint/refactor-p0/fixtures");

    static GraphQLEngine engine;
    static NopRefactorBizModel bizModel;

    @BeforeAll
    public static void init() throws Exception {
        CoreInitialization.initialize();
        bizModel = BeanContainer.getBeanByType(NopRefactorBizModel.class);
        engine = new GraphQLEngine();
        engine.setSchemaLoader(new TestNopRefactorGraphQL.BizModelSchemaLoader(bizModel));
        engine.init();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static String fixture(String name) throws Exception {
        return Files.readString(FIXTURES.resolve(name), StandardCharsets.UTF_8);
    }

    private static Path writeTarget(String relPath, String content) throws Exception {
        Path file = Path.of("target", "refactor-p0-loop", relPath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    /** The demo target pair: the three-pattern sample + the exempted legacy file. */
    private record Targets(Path sample, Path legacy) {
    }

    private Targets writeFreshTargets() throws Exception {
        Path sample = writeTarget("p0/Sample.java", fixture("sample.before.java"));
        Path legacy = writeTarget("p0/legacy/Legacy.java", fixture("legacy.before.java"));
        return new Targets(sample, legacy);
    }

    private static Map<String, Object> input(String... paths) {
        Map<String, Object> inner = new HashMap<>();
        inner.put("rulesetPrefix", P0_PREFIX);
        inner.put("paths", List.of(paths));
        Map<String, Object> data = new HashMap<>();
        data.put("input", inner);
        return data;
    }

    private static RewriteInput typedInput(List<String> paths) {
        RewriteInput input = new RewriteInput();
        input.setRulesetPrefix(P0_PREFIX);
        input.setPaths(paths);
        return input;
    }

    private static ApiResponse<?> rpc(String action, Map<String, Object> data) {
        IGraphQLExecutionContext context = engine.newRpcContext(
                GraphQLOperationType.mutation, action, request(data));
        return FutureHelper.syncGet(engine.executeRpcAsync(context));
    }

    private static ApiRequest<Map<String, Object>> request(Map<String, Object> data) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(data);
        return request;
    }

    // ==================== (a) preview over the RPC face ====================

    @Test
    void previewRpcCarriesTheFullPayloadAndWritesNothing() throws Exception {
        Targets targets = writeFreshTargets();
        String sampleBefore = fixture("sample.before.java");
        String legacyBefore = fixture("legacy.before.java");

        ApiResponse<?> response = rpc("Refactor__previewRewrite",
                input(targets.sample().toString(), targets.legacy().toString()));

        assertTrue(response.isOk(), String.valueOf(response));
        String body = String.valueOf(response.get());
        assertTrue(body.contains("applied=false"), body);
        // the diff carries all three demo rewrites
        assertTrue(body.contains("log.info"), body);
        assertTrue(body.contains("Integer.valueOf"), body);
        assertTrue(body.contains("literal"), body);
        // the verification and stats faces ride the same payload
        assertTrue(body.contains("parseOk=true"), body);
        assertTrue(body.contains("errorNodeCount=0"), body);
        assertTrue(body.contains("IN_PROCESS"), body);
        assertTrue(body.contains("residualRuleCount=0"), body);
        // the exempted legacy rewrite is enumerated, not silently dropped
        assertTrue(body.contains("OUT_OF_SCOPE"), body);
        // preview never touches the files
        assertEquals(sampleBefore, Files.readString(targets.sample()),
                "preview must not write the sample");
        assertEquals(legacyBefore, Files.readString(targets.legacy()),
                "preview must not write the legacy file");
    }

    // ==================== (b) apply over the RPC face ====================

    @Test
    void applyRpcLandsByteIdenticalAfterSources() throws Exception {
        Targets targets = writeFreshTargets();

        ApiResponse<?> response = rpc("Refactor__applyRewrite",
                input(targets.sample().toString(), targets.legacy().toString()));

        assertTrue(response.isOk(), String.valueOf(response));
        assertTrue(String.valueOf(response.get()).contains("applied=true"),
                "apply reports applied=true: " + response);
        assertEquals(fixture("sample.after.java"), Files.readString(targets.sample()),
                "apply landed the byte-identical after source");
        assertEquals(fixture("legacy.before.java"), Files.readString(targets.legacy()),
                "the exempted legacy file is untouched by apply");
    }

    // ==================== (c) the typed full-payload face ====================

    @Test
    void previewPayloadExposesEveryContractField() throws Exception {
        Targets targets = writeFreshTargets();

        RefactorResult result = bizModel.previewRewrite(
                typedInput(List.of(targets.sample().toString(), targets.legacy().toString())));

        assertFalse(result.applied(), "preview is a dry run");
        // edits: path + range + summary, one per demo rewrite, sample only
        assertEquals(3, result.edits().size());
        for (FileEdit edit : result.edits()) {
            assertTrue(edit.path().endsWith("Sample.java"), edit.path());
            assertTrue(edit.range().startByte() >= 0, String.valueOf(edit.range()));
            assertTrue(edit.range().endByte() > edit.range().startByte(),
                    String.valueOf(edit.range()));
            assertNotNull(edit.summary());
        }
        assertTrue(result.edits().stream().anyMatch(e -> e.summary().equals("Rewrite println to log.info")));
        assertTrue(result.edits().stream().anyMatch(e -> e.summary().equals("Rewrite new Integer to Integer.valueOf")));
        assertTrue(result.edits().stream().anyMatch(e -> e.summary().equals("Rewrite new String literal to the literal itself")));

        // verification: the four sub-fields (rewrite face: symbolIntact null)
        assertTrue(result.verification().parseOk());
        assertEquals(0, result.verification().errorNodeCount());
        assertEquals(0, result.verification().residualDiagnostics());
        assertNull(result.verification().symbolIntact(), "rewrite face leaves symbolIntact null");

        // stats: the six faces
        assertEquals(1, result.stats().filesAffected());
        assertEquals(3, result.stats().editsApplied());
        assertEquals(0, result.stats().skipped().conflict());
        assertEquals(1, result.stats().skipped().outOfScope());
        assertEquals(0, result.stats().skipped().unresolvedTarget());
        assertEquals(0, result.stats().skipped().rolledBack());
        assertEquals(io.nop.refactor.core.RefactorStats.CostTier.IN_PROCESS, result.stats().costTier());
        assertEquals(0, result.stats().residualRuleCount(), "no residual subset configured");

        // nonApplied: the exempted rewrite, with reason + locatable context
        assertEquals(1, result.nonApplied().size());
        NonApply nonApply = result.nonApplied().get(0);
        assertEquals(NonApply.Reason.OUT_OF_SCOPE, nonApply.reason());
        assertTrue(nonApply.path().endsWith("Legacy.java"), nonApply.path());
        assertFalse(nonApply.detail().isBlank());

        // the diff is the AI review surface: all three rewrites visible
        assertTrue(result.diff().contains("log.info"), result.diff());
        assertTrue(result.diff().contains("Integer.valueOf"), result.diff());
        assertTrue(result.diff().contains("\"literal\""), result.diff());
    }

    // ==================== (d) stateless re-execution determinism ====================

    @Test
    void identicalInputsRerunProduceIdenticalPayloads() throws Exception {
        Targets first = writeFreshTargets();
        Targets second = writeFreshTargets();

        Map<String, Object> firstInput = input(first.sample().toString(), first.legacy().toString());
        Map<String, Object> secondInput = input(second.sample().toString(), second.legacy().toString());

        ApiResponse<?> firstRun = rpc("Refactor__previewRewrite", firstInput);
        ApiResponse<?> secondRun = rpc("Refactor__previewRewrite", secondInput);

        assertTrue(firstRun.isOk() && secondRun.isOk());
        assertEquals(String.valueOf(firstRun.get()), String.valueOf(secondRun.get()),
                "preview over identical inputs must be deterministic (stateless re-execution)");
        // and the same input set re-previewed is stable too
        ApiResponse<?> rerun = rpc("Refactor__previewRewrite", firstInput);
        assertEquals(String.valueOf(firstRun.get()), String.valueOf(rerun.get()));
    }

    // ==================== the gate rejects non-transform rules ====================

    @Test
    void demoSetPassesTheRewriteLoadGate() {
        // every p0 rule is transform-only with no requires — the shared load
        // gate accepts the set (the fix-bearing production rules are rejected
        // here by design; that rejection face is WI6's own test domain)
        var loaded = new io.nop.lint.core.cli.RuleSetLoader().loadRuleSet(P0_PREFIX);
        io.nop.refactor.core.RefactorRuleGates.verifyRewriteRuleset(loaded,
                io.nop.lint.core.engine.LanguageRegistry.discoverDefaults());
    }
}
