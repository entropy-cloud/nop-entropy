package io.nop.ib.scope;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The plan/feedback contract payload (design 05 §九). {@code partial},
 * {@code wideImpact} and per-module fallback reasons are mandatory visible
 * fields — silent truncation is a hard-constraint violation.
 */
public class PlanResult {
    private boolean ok = true;
    private String fallbackReason;
    /** repo-relative changed paths considered */
    private List<String> changed = new ArrayList<>();
    /** modules selected for compilation (ordered by reactor dependency) */
    private List<String> compileModules = new ArrayList<>();
    /** module -> changed source files to compile */
    private Map<String, List<String>> compileFiles = new LinkedHashMap<>();
    /** predicted compile-only (no ABI prediction evidence) downstream modules */
    private List<String> downstreamCandidates = new ArrayList<>();
    private boolean abiPredictionPrecise;
    /** modules where full test run is mandated */
    private List<String> fullTestModules = new ArrayList<>();
    /** module -> selected test class binary names */
    private Map<String, List<String>> selectedTests = new TreeMap<>();
    private boolean wideImpact;
    private boolean partial;
    private List<String> warnings = new ArrayList<>();

    public boolean requiresMavenFallback() {
        return fallbackReason != null;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("required", requiresMavenFallback());
        fallback.put("reason", fallbackReason);

        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("changed", changed);
        scope.put("compileModules", compileModules);
        List<String> files = new ArrayList<>();
        compileFiles.values().forEach(files::addAll);
        scope.put("compileFiles", files.size());
        scope.put("compileFilesByModule", compileFiles);
        scope.put("downstreamCandidates", downstreamCandidates);
        scope.put("abiPredictionPrecise", abiPredictionPrecise);
        scope.put("testModules", testModulesUnion());
        int testClassCount = 0;
        for (List<String> l : selectedTests.values())
            testClassCount += l.size();
        scope.put("testClasses", testClassCount);
        scope.put("selectedTestsByModule", selectedTests);
        scope.put("fullTestModules", fullTestModules);
        scope.put("wideImpact", wideImpact);
        scope.put("partial", partial);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", ok);
        m.put("fallback", fallback);
        m.put("scope", scope);
        if (!warnings.isEmpty())
            m.put("warnings", warnings);
        return m;
    }

    public List<String> testModulesUnion() {
        List<String> modules = new ArrayList<>(selectedTests.keySet());
        for (String m : fullTestModules)
            if (!modules.contains(m))
                modules.add(m);
        return modules;
    }

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public String getFallbackReason() {
        return fallbackReason;
    }

    public void setFallbackReason(String fallbackReason) {
        this.fallbackReason = fallbackReason;
    }

    public List<String> getChanged() {
        return changed;
    }

    public void setChanged(List<String> changed) {
        this.changed = changed;
    }

    public List<String> getCompileModules() {
        return compileModules;
    }

    public void setCompileModules(List<String> compileModules) {
        this.compileModules = compileModules;
    }

    public Map<String, List<String>> getCompileFiles() {
        return compileFiles;
    }

    public List<String> getDownstreamCandidates() {
        return downstreamCandidates;
    }

    public void setDownstreamCandidates(List<String> downstreamCandidates) {
        this.downstreamCandidates = downstreamCandidates;
    }

    public boolean isAbiPredictionPrecise() {
        return abiPredictionPrecise;
    }

    public void setAbiPredictionPrecise(boolean abiPredictionPrecise) {
        this.abiPredictionPrecise = abiPredictionPrecise;
    }

    public List<String> getFullTestModules() {
        return fullTestModules;
    }

    public void addFullTestModule(String module) {
        if (!fullTestModules.contains(module))
            fullTestModules.add(module);
    }

    public Map<String, List<String>> getSelectedTests() {
        return selectedTests;
    }

    public void addSelectedTests(String module, List<String> classes) {
        if (classes.isEmpty())
            return;
        List<String> existing = selectedTests.computeIfAbsent(module, k -> new ArrayList<>());
        for (String c : classes)
            if (!existing.contains(c))
                existing.add(c);
    }

    public boolean isWideImpact() {
        return wideImpact;
    }

    public void setWideImpact(boolean wideImpact) {
        this.wideImpact = wideImpact;
    }

    public boolean isPartial() {
        return partial;
    }

    public void setPartial(boolean partial) {
        this.partial = partial;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void warn(String message) {
        if (!warnings.contains(message))
            warnings.add(message);
    }
}
