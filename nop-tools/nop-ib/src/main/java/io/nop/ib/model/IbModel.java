package io.nop.ib.model;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Build-facts model exported from Maven itself (design 05 §五). One document per
 * worktree at {@code .nop-ib/model.json}. Authority rule: nop-ib never parses raw
 * pom.xml; staleness is detected by re-hashing each module's pom.xml against the
 * hashes recorded at refresh time.
 */
public class IbModel {
    public static final String FORMAT_VERSION = "1";

    private String repoRoot;
    private String mavenVersion;
    private String jdkVersion;
    private String localRepo;
    /** sha256 of the effective-pom dump the model was built from */
    private String dumpSha256;
    /** unix millis of refresh */
    private long refreshedAt;
    /** module basedir (repo-relative) -> raw pom sha256 at refresh time */
    private Map<String, String> pomHashes = new LinkedHashMap<>();
    private Map<String, IbModule> modulesByArtifactId = new LinkedHashMap<>();
    /** reactor order preserved */
    private List<IbModule> modules = new ArrayList<>();
    /**
     * wave 11 item 8: artifactIds whose {@code dependency:list} failed at
     * refresh time (marked {@code depsUnresolved}) — refresh still succeeds,
     * but plans touching these modules must fall back to Maven (fail-safe).
     */
    private List<String> unresolvedModules = new ArrayList<>();

    public String getRepoRoot() {
        return repoRoot;
    }

    public void setRepoRoot(String repoRoot) {
        this.repoRoot = repoRoot;
    }

    public String getMavenVersion() {
        return mavenVersion;
    }

    public void setMavenVersion(String mavenVersion) {
        this.mavenVersion = mavenVersion;
    }

    public String getJdkVersion() {
        return jdkVersion;
    }

    public void setJdkVersion(String jdkVersion) {
        this.jdkVersion = jdkVersion;
    }

    public String getLocalRepo() {
        return localRepo;
    }

    public void setLocalRepo(String localRepo) {
        this.localRepo = localRepo;
    }

    public String getDumpSha256() {
        return dumpSha256;
    }

    public void setDumpSha256(String dumpSha256) {
        this.dumpSha256 = dumpSha256;
    }

    public long getRefreshedAt() {
        return refreshedAt;
    }

    public void setRefreshedAt(long refreshedAt) {
        this.refreshedAt = refreshedAt;
    }

    public Map<String, String> getPomHashes() {
        return pomHashes;
    }

    /** wave 11 item 8: artifactIds with unresolved dependency facts. */
    public List<String> getUnresolvedModules() {
        return unresolvedModules;
    }

    public void setUnresolvedModules(List<String> unresolvedModules) {
        this.unresolvedModules = unresolvedModules == null
                ? new ArrayList<>() : new ArrayList<>(unresolvedModules);
    }

    private static List<String> fromStringList(Object value) {
        List<String> out = new ArrayList<>();
        if (value instanceof Iterable<?> iter) {
            for (Object o : iter)
                out.add(String.valueOf(o));
        }
        return out;
    }

    public List<IbModule> getModules() {
        return modules;
    }

    public IbModule module(String artifactId) {
        return modulesByArtifactId.get(artifactId);
    }

    public IbModule moduleByBasedir(String basedirRel) {
        for (IbModule m : modules) {
            if (m.getBasedir() != null && m.getBasedir().equals(basedirRel))
                return m;
        }
        return null;
    }

    public void addModule(IbModule module) {
        if (modulesByArtifactId.put(module.getArtifactId(), module) != null)
            throw new IllegalArgumentException("duplicate reactor artifactId: " + module.getArtifactId());
        modules.add(module);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("formatVersion", IbModel.FORMAT_VERSION);
        m.put("repoRoot", repoRoot);
        m.put("mavenVersion", mavenVersion);
        m.put("jdkVersion", jdkVersion);
        m.put("localRepo", localRepo);
        m.put("dumpSha256", dumpSha256);
        m.put("refreshedAt", refreshedAt);
        m.put("pomHashes", pomHashes);
        if (!unresolvedModules.isEmpty())
            m.put("unresolvedModules", new ArrayList<>(unresolvedModules));
        List<Object> mods = new ArrayList<>();
        for (IbModule mod : modules)
            mods.add(mod.toMap());
        m.put("modules", mods);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static IbModel fromMap(Map<String, Object> m) {
        IbModel model = new IbModel();
        model.repoRoot = (String) m.get("repoRoot");
        model.mavenVersion = (String) m.get("mavenVersion");
        model.jdkVersion = (String) m.get("jdkVersion");
        model.localRepo = (String) m.get("localRepo");
        model.dumpSha256 = (String) m.get("dumpSha256");
        Object at = m.get("refreshedAt");
        model.refreshedAt = at instanceof Number n ? n.longValue() : 0L;
        Object hashes = m.get("pomHashes");
        if (hashes instanceof Map<?, ?> hm) {
            for (Map.Entry<?, ?> e : hm.entrySet())
                model.pomHashes.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
        }
        model.unresolvedModules = fromStringList(m.get("unresolvedModules"));
        Object mods = m.get("modules");
        if (mods instanceof Iterable<?> iter) {
            for (Object o : iter) {
                Map<String, Object> mm = (Map<String, Object>) o;
                model.addModule(IbModule.fromMap(mm));
            }
        }
        return model;
    }

    /** Same-module lookup by repo-relative file path (any file inside the module).
     *  An empty basedir (single-module repo-root project) matches any path; the
     *  longest-prefix rule still prefers a deeper nested module fact. */
    public IbModule moduleForPath(String relPath) {
        IbModule best = null;
        for (IbModule m : modules) {
            String b = m.getBasedir();
            if (b == null)
                continue;
            if (b.isEmpty() || relPath.equals(b) || relPath.startsWith(b + "/")) {
                if (best == null || b.length() > best.getBasedir().length())
                    best = m;
            }
        }
        return best;
    }

    public File resolve(String repoRelativePath) {
        return new File(repoRoot, repoRelativePath);
    }

    public Map<String, IbModule> getModulesByArtifactId() {
        return modulesByArtifactId;
    }
}
