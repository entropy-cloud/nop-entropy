package io.nop.ib.cli;

import io.nop.ib.engine.IbEngine;
import io.nop.ib.index.UnitIndex;
import io.nop.ib.json.JsonIn;
import io.nop.ib.json.JsonOut;
import io.nop.ib.model.IbModel;
import io.nop.ib.model.IbModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Opt-in test result cache (roadmap Wave 9): when {@code --cache} is set, a
 * module whose input fingerprint is unchanged AND whose last recorded run was
 * GREEN skips JUnit execution and reports {@code cached-up-to-date}. Zero
 * fake-green guards:
 *
 * <ul>
 *   <li>cached results are EXPLICIT ({@code kind=cached-up-to-date}, JSON
 *       {@code cached:true}) — a cache hit never masquerades as an execution;</li>
 *   <li>only GREEN results are recorded — a preexisting-red module re-runs
 *       every time (fail-safe direction);</li>
 *   <li>missing/stale model, missing unit index, or missing fingerprint
 *       components → no hit (fail-safe);</li>
 *   <li>{@code --force} bypasses hits entirely.</li>
 * </ul>
 *
 * <p>Fingerprint inputs: the module unit index's per-class content hashes and
 * ABI digests, the ABI digests of every (transitively) reached reactor module,
 * external artifacts, and ALL surefire facts (argLine / tags / includes /
 * excludes / system properties / environment variables). NOT in the
 * fingerprint (disclosed limitation, design 05): external test environments —
 * databases, containers, host state. A cached green whose environment changed
 * is the residual risk the opt-in flag accepts; {@code --force} re-executes.
 */
public final class TestCacheStore {
    public static final String CACHE_FILE = "test-cache.json";

    private final Path file;
    private final Map<String, Map<String, Object>> entries = new LinkedHashMap<>();

    public TestCacheStore(IbEngine engine) {
        this.file = engine.getRepoRoot().resolve(".nop-ib").resolve(CACHE_FILE);
        load();
    }

    private void load() {
        if (!Files.isRegularFile(file))
            return;
        try {
            Object parsed = JsonIn.silentParse(Files.readString(file, StandardCharsets.UTF_8));
            Map<String, Object> root = JsonIn.asMapOrEmpty(parsed);
            for (Map.Entry<String, Object> e : root.entrySet())
                if (e.getValue() instanceof Map<?, ?> m)
                    entries.put(e.getKey(), JsonIn.asMapOrEmpty(m));
        } catch (Exception e) {
            // unreadable cache = empty cache (fail-safe: never serve a hit we
            // cannot fully validate)
            entries.clear();
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, JsonOut.write(entries), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // best-effort persistence: a failed write disables future hits, it
            // cannot corrupt a verdict
            entries.clear();
        }
    }

    /** cached green entry for the module, or null (no hit). */
    public Map<String, Object> hit(String artifactId, String fingerprint) {
        if (fingerprint == null)
            return null;
        Map<String, Object> e = entries.get(artifactId);
        if (e == null || !fingerprint.equals(e.get("fingerprint")))
            return null;
        Integer exit = e.get("exitCode") instanceof Number n ? n.intValue() : null;
        if (exit == null || exit != 0)
            return null; // only greens are served
        return e;
    }

    /** record a run; only GREEN results are stored (red → previous entry dropped). */
    public synchronized void record(String artifactId, String fingerprint, int exitCode,
                                    int tests, int failed, int errors, int skipped) {
        if (fingerprint == null)
            return;
        if (exitCode != 0) {
            entries.remove(artifactId);
            return;
        }
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("fingerprint", fingerprint);
        e.put("exitCode", exitCode);
        e.put("tests", tests);
        e.put("failed", failed);
        e.put("errors", errors);
        e.put("skipped", skipped);
        e.put("cachedAt", System.currentTimeMillis());
        entries.put(artifactId, e);
    }

    /**
     * Input fingerprint, or null when any component is unavailable (missing
     * unit index / model) — null never hits.
     */
    public String fingerprint(IbEngine engine, IbModule module) {
        UnitIndex index = engine.index(module.getArtifactId());
        if (index == null)
            return null;
        IbModel model = engine.model();
        StringBuilder sb = new StringBuilder(64);
        // 1. own classes: sorted (class → contentHash → abiDigest)
        Map<String, String[]> own = new TreeMap<>();
        for (var info : index.getClasses().values()) {
            if (info.getContentHash() == null || info.getAbiDigest() == null)
                return null;
            own.put(info.getClassName(), new String[]{info.getContentHash(), info.getAbiDigest()});
        }
        for (Map.Entry<String, String[]> e : own.entrySet())
            sb.append(e.getKey()).append('=').append(e.getValue()[0]).append(',')
                    .append(e.getValue()[1]).append('\n');
        // 2. reached reactor modules' ABI digests (transitive dependency closure)
        for (String depId : reachedModules(model, module.getArtifactId())) {
            UnitIndex depIndex = engine.index(depId);
            if (depIndex == null)
                continue; // dep without index: its products are external jars already in externalArtifacts
            Map<String, String> digests = new TreeMap<>();
            for (var info : depIndex.getClasses().values()) {
                if (info.getAbiDigest() == null)
                    return null;
                digests.put(info.getClassName(), info.getAbiDigest());
            }
            sb.append("#dep:").append(depId).append('\n');
            digests.forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
        }
        // 3. external artifacts (versions/scopes of resolved jars)
        for (String ea : module.getExternalArtifacts())
            sb.append("#ea:").append(ea).append('\n');
        // 4. surefire facts (argLine, tags, includes/excludes, sysprops, env)
        sb.append("#argLine:").append(module.getSurefireArgLine()).append('\n');
        sb.append("#skipTests:").append(module.getSurefireSkipTests()).append('\n');
        sb.append("#groups:").append(module.getSurefireGroups()).append('\n');
        sb.append("#excludedGroups:").append(module.getSurefireExcludedGroups()).append('\n');
        module.getSurefireIncludes().forEach(i -> sb.append("#inc:").append(i).append('\n'));
        module.getSurefireExcludes().forEach(x -> sb.append("#exc:").append(x).append('\n'));
        module.getSurefireSystemProperties()
                .forEach((k, v) -> sb.append("#sys:").append(k).append('=').append(v).append('\n'));
        module.getSurefireEnvironmentVariables()
                .forEach((k, v) -> sb.append("#env:").append(k).append('=').append(v).append('\n'));
        return sha256Hex(sb.toString());
    }

    /** transitive reactor dependency artifactIds (forward closure). */
    static List<String> reachedModules(IbModel model, String rootArtifactId) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<String> queue = new ArrayList<>();
        seen.add(rootArtifactId);
        IbModule root = model.module(rootArtifactId);
        if (root != null)
            for (IbModule.DepEdge d : root.getDependencies()) {
                String ga = d.getGa();
                for (IbModule m : model.getModules())
                    if (m.getGa().equals(ga) && seen.add(m.getArtifactId()))
                        queue.add(m.getArtifactId());
            }
        while (!queue.isEmpty()) {
            String id = queue.remove(queue.size() - 1);
            out.add(id);
            IbModule m = model.module(id);
            if (m == null)
                continue;
            for (IbModule.DepEdge d : m.getDependencies()) {
                for (IbModule dm : model.getModules())
                    if (dm.getGa().equals(d.getGa()) && seen.add(dm.getArtifactId()))
                        queue.add(dm.getArtifactId());
            }
        }
        return out;
    }

    static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] d = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(d.length * 2);
            for (byte b : d) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
