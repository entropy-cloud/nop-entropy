package io.nop.ib.scope;

import io.nop.ib.cli.Commands;
import io.nop.ib.model.IbModel;
import io.nop.ib.model.IbModule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave 11 item 8 pins (model refresh per-module tolerance): a module whose
 * {@code dependency:list} failed at refresh time is recorded
 * ({@code depsUnresolved}, {@code unresolvedModules}), refresh still reports
 * ok, and the planner routes any plan touching the degraded module to the
 * Maven fallback with an explicit reason — while normal modules keep the fast
 * path.
 */
class TestDepsUnresolvedFallback {

    private IbModule module(String artifactId, String parentArtifact) {
        IbModule m = new IbModule();
        m.setGroupId("g");
        m.setArtifactId(artifactId);
        m.setVersion("1");
        if (parentArtifact != null)
            m.setParentGa("g:" + parentArtifact);
        m.setBasedir(artifactId);
        m.setSourceDir(artifactId + "/src/main/java");
        m.setTestSourceDir(artifactId + "/src/test/java");
        return m;
    }

    private IbModel modelWithDegraded() {
        IbModel model = new IbModel();
        model.setRepoRoot("/repo");
        IbModule degraded = module("degraded", "root");
        degraded.setDepsUnresolved(true);
        model.addModule(module("root", null));
        model.addModule(degraded);
        model.addModule(module("normal", "root"));
        for (IbModule m : model.getModules())
            model.getModulesByArtifactId().put(m.getArtifactId(), m);
        model.setUnresolvedModules(List.of("degraded"));
        return model;
    }

    /** pin 2: a change inside a degraded module forces Maven fallback naming it. */
    @Test
    void degradedModuleChangeForcesFallbackNamingIt() {
        ScopePlanner planner = new ScopePlanner(modelWithDegraded(),
                new ModuleGraph(modelWithDegraded()), id -> null);
        PlanResult plan = planner.plan(List.of("degraded/src/main/java/A.java"), null, true);
        assertTrue(plan.requiresMavenFallback(),
                "degraded module must not take the fast path (fail-safe)");
        assertTrue(plan.getFallbackReason().contains("degraded"),
                "fallback reason must name the degraded module: " + plan.getFallbackReason());
        assertTrue(plan.getFallbackReason().contains("depsUnresolved"),
                "fallback reason must state the degradation: " + plan.getFallbackReason());
    }

    /** a degraded module pulled in via reverse closure also forces the fallback. */
    @Test
    void dependentOfDegradedModuleAlsoFallsBack() {
        IbModel model = modelWithDegraded();
        // normal depends on degraded
        IbModule.DepEdge edge = new IbModule.DepEdge();
        edge.setGa("g:degraded");
        model.module("normal").getDependencies().add(edge);
        ScopePlanner planner = new ScopePlanner(model, new ModuleGraph(model), id -> null);
        PlanResult plan = planner.plan(List.of("degraded/src/main/java/A.java"), null, true);
        assertTrue(plan.requiresMavenFallback());
        assertTrue(plan.getCompileModules().contains("normal"),
                "the dependent module is in the closure");
        assertTrue(plan.getFallbackReason().contains("degraded"));
    }

    /** pin 3: a normal module keeps the fast path — no fallback, degraded module untouched. */
    @Test
    void normalModuleIsUnaffected() {
        ScopePlanner planner = new ScopePlanner(modelWithDegraded(),
                new ModuleGraph(modelWithDegraded()), id -> null);
        PlanResult plan = planner.plan(List.of("normal/src/main/java/B.java"), null, true);
        assertFalse(plan.requiresMavenFallback(),
                "a change in a healthy module must not trigger the degraded-module fallback");
        assertTrue(plan.getCompileModules().contains("normal"));
        assertFalse(plan.getCompileModules().contains("degraded"));
        assertNull(plan.getFallbackReason());
    }

    /** pin 1 (report half): refresh reports ok with unresolvedModules in the contract. */
    @Test
    void refreshReportCarriesUnresolvedModulesExplicitly() {
        IbModel model = modelWithDegraded();
        Map<String, Object> report = Commands.refreshReport(model);
        assertEquals(true, report.get("ok"), "refresh still succeeds (exit 0)");
        assertEquals(List.of("degraded"), report.get("unresolvedModules"),
                "the degraded modules must be listed explicitly");
        assertTrue(((List<?>) report.get("warnings")).stream()
                        .anyMatch(w -> String.valueOf(w).contains("degraded")),
                "warnings must name the degraded module for human output too");
    }

    /** a refresh without failures reports an empty unresolved list, no warnings. */
    @Test
    void cleanRefreshReportsEmptyUnresolved() {
        IbModel model = new IbModel();
        model.setRepoRoot("/repo");
        model.addModule(module("root", null));
        Map<String, Object> report = Commands.refreshReport(model);
        assertEquals(true, report.get("ok"));
        assertEquals(List.of(), report.get("unresolvedModules"));
        assertTrue(((List<?>) report.get("warnings")).isEmpty());
    }

    /** the degradation survives a model.json round trip (IbModel.toMap/fromMap). */
    @Test
    void depsUnresolvedSurvivesRoundTrip() {
        IbModel saved = modelWithDegraded();
        IbModel loaded = IbModel.fromMap(saved.toMap());
        assertTrue(loaded.module("degraded").isDepsUnresolved(),
                "depsUnresolved must persist in model.json");
        assertFalse(loaded.module("normal").isDepsUnresolved());
        assertEquals(List.of("degraded"), loaded.getUnresolvedModules());
    }
}
