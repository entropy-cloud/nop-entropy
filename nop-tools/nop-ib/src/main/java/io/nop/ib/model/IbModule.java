package io.nop.ib.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One reactor module's build facts, exported by Maven itself (design 05 §五).
 * Paths are stored repo-relative; dependency coordinates as group:artifact.
 */
public class IbModule {
    public static final String FORMAT_VERSION = "1";

    private String groupId;
    private String artifactId;
    private String version;
    private String packaging;
    private String parentGa;
    /** repo-relative basedir */
    private String basedir;
    private String sourceDir;
    private String testSourceDir;
    private String outputDir;
    private String testOutputDir;
    private List<String> resourceDirs = new ArrayList<>();
    private List<String> testResourceDirs = new ArrayList<>();
    private List<DepEdge> dependencies = new ArrayList<>();
    private List<CompilerExec> compilerExecs = new ArrayList<>();
    private List<String> execExecutionIds = new ArrayList<>();
    private String surefireArgLine;
    private String surefireSkipTests;
    private Map<String, String> surefireSystemProperties = new LinkedHashMap<>();
    /** resolved external artifacts in dependency order, "ga:version:scope" */
    private List<String> externalArtifacts = new ArrayList<>();
    /** sha256 of the module's raw pom.xml at model build time */
    private String pomHash;

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("groupId", groupId);
        m.put("artifactId", artifactId);
        m.put("version", version);
        m.put("packaging", packaging);
        m.put("parent", parentGa);
        m.put("basedir", basedir);
        m.put("sourceDirectory", sourceDir);
        m.put("testSourceDirectory", testSourceDir);
        m.put("outputDirectory", outputDir);
        m.put("testOutputDirectory", testOutputDir);
        m.put("resources", resourceDirs);
        m.put("testResources", testResourceDirs);
        List<Object> deps = new ArrayList<>();
        for (DepEdge d : dependencies)
            deps.add(d.toMap());
        m.put("dependencies", deps);
        List<Object> execs = new ArrayList<>();
        for (CompilerExec e : compilerExecs)
            execs.add(e.toMap());
        m.put("compilerExecutions", execs);
        m.put("execExecutionIds", execExecutionIds);
        Map<String, Object> surefire = new LinkedHashMap<>();
        surefire.put("argLine", surefireArgLine);
        surefire.put("skipTests", surefireSkipTests);
        surefire.put("systemPropertyVariables", surefireSystemProperties);
        m.put("surefire", surefire);
        m.put("externalArtifacts", externalArtifacts);
        m.put("pomHash", pomHash);
        return m;
    }

    public static IbModule fromMap(Map<String, Object> m) {
        IbModule mod = new IbModule();
        mod.groupId = (String) m.get("groupId");
        mod.artifactId = (String) m.get("artifactId");
        mod.version = (String) m.get("version");
        mod.packaging = (String) m.get("packaging");
        mod.parentGa = (String) m.get("parent");
        mod.basedir = (String) m.get("basedir");
        mod.sourceDir = (String) m.get("sourceDirectory");
        mod.testSourceDir = (String) m.get("testSourceDirectory");
        mod.outputDir = (String) m.get("outputDirectory");
        mod.testOutputDir = (String) m.get("testOutputDirectory");
        mod.resourceDirs = fromStringList(m.get("resources"));
        mod.testResourceDirs = fromStringList(m.get("testResources"));
        for (Object o : JsonListAdapter.asListOrEmpty(m.get("dependencies")))
            mod.dependencies.add(DepEdge.fromMap(JsonListAdapter.asMapOrEmpty(o)));
        for (Object o : JsonListAdapter.asListOrEmpty(m.get("compilerExecutions")))
            mod.compilerExecs.add(CompilerExec.fromMap(JsonListAdapter.asMapOrEmpty(o)));
        mod.execExecutionIds = fromStringList(m.get("execExecutionIds"));
        Object surefire = m.get("surefire");
        if (surefire instanceof Map<?, ?> sf) {
            mod.surefireArgLine = (String) sf.get("argLine");
            mod.surefireSkipTests = (String) sf.get("skipTests");
            Object props = sf.get("systemPropertyVariables");
            if (props instanceof Map<?, ?> pm) {
                for (Map.Entry<?, ?> e : pm.entrySet())
                    mod.surefireSystemProperties.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            }
        }
        mod.externalArtifacts = fromStringList(m.get("externalArtifacts"));
        mod.pomHash = (String) m.get("pomHash");
        return mod;
    }

    private static List<String> fromStringList(Object value) {
        List<String> out = new ArrayList<>();
        if (value instanceof Iterable<?> iter) {
            for (Object o : iter)
                out.add(String.valueOf(o));
        }
        return out;
    }

    public String getGa() {
        return groupId + ":" + artifactId;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getPackaging() {
        return packaging;
    }

    public void setPackaging(String packaging) {
        this.packaging = packaging;
    }

    public String getParentGa() {
        return parentGa;
    }

    public void setParentGa(String parentGa) {
        this.parentGa = parentGa;
    }

    public String getBasedir() {
        return basedir;
    }

    public void setBasedir(String basedir) {
        this.basedir = basedir;
    }

    public String getSourceDir() {
        return sourceDir;
    }

    public void setSourceDir(String sourceDir) {
        this.sourceDir = sourceDir;
    }

    public String getTestSourceDir() {
        return testSourceDir;
    }

    public void setTestSourceDir(String testSourceDir) {
        this.testSourceDir = testSourceDir;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    public String getTestOutputDir() {
        return testOutputDir;
    }

    public void setTestOutputDir(String testOutputDir) {
        this.testOutputDir = testOutputDir;
    }

    public List<String> getResourceDirs() {
        return resourceDirs;
    }

    public List<String> getTestResourceDirs() {
        return testResourceDirs;
    }

    public List<DepEdge> getDependencies() {
        return dependencies;
    }

    public List<CompilerExec> getCompilerExecs() {
        return compilerExecs;
    }

    public List<String> getExecExecutionIds() {
        return execExecutionIds;
    }

    public String getSurefireArgLine() {
        return surefireArgLine;
    }

    public void setSurefireArgLine(String argLine) {
        this.surefireArgLine = argLine;
    }

    public void setSurefireSkipTests(String skipTests) {
        this.surefireSkipTests = skipTests;
    }

    public String getSurefireSkipTests() {
        return surefireSkipTests;
    }

    public Map<String, String> getSurefireSystemProperties() {
        return surefireSystemProperties;
    }

    public List<String> getExternalArtifacts() {
        return externalArtifacts;
    }

    public void setExternalArtifacts(List<String> externalArtifacts) {
        this.externalArtifacts = externalArtifacts;
    }

    public String getPomHash() {
        return pomHash;
    }

    public void setPomHash(String pomHash) {
        this.pomHash = pomHash;
    }

    /** Capability level per design 01 §六 (evidence-based adjudication, Wave 0). */
    public String capabilityLevel(boolean hasAopArtifacts, boolean ownsXgenTemplates) {
        if (hasAopArtifacts)
            return "L3";
        if (isProcessorModule())
            return "L2";
        if (ownsXgenTemplates)
            return "L1";
        return "L0";
    }

    /** Effective compiler execution configures annotationProcessorPaths or explicit proc. */
    public boolean isProcessorModule() {
        for (CompilerExec e : compilerExecs) {
            if (e.annotationProcessorPaths || e.proc != null)
                return true;
        }
        return false;
    }

    /** Default compile release across executions (first found), else null. */
    public String getCompileRelease() {
        for (CompilerExec e : compilerExecs) {
            if (e.release != null)
                return e.release;
        }
        return null;
    }

    public static class DepEdge {
        String ga;
        String scope;
        String type;
        String classifier;
        boolean optional;

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ga", ga);
            m.put("scope", scope);
            m.put("type", type);
            m.put("classifier", classifier);
            m.put("optional", optional);
            return m;
        }

        public static DepEdge fromMap(Map<String, Object> m) {
            DepEdge d = new DepEdge();
            d.ga = (String) m.get("ga");
            d.scope = (String) m.get("scope");
            d.type = (String) m.get("type");
            d.classifier = (String) m.get("classifier");
            Object opt = m.get("optional");
            d.optional = Boolean.TRUE.equals(opt) || "true".equals(opt);
            return d;
        }

        public String getGa() {
            return ga;
        }

        public String getScope() {
            return scope;
        }

        public String getType() {
            return type;
        }

        public boolean isOptional() {
            return optional;
        }
    }

    public static class CompilerExec {
        String id;
        String release;
        String proc;
        boolean annotationProcessorPaths;
        String source;
        String target;

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("release", release);
            m.put("proc", proc);
            m.put("annotationProcessorPaths", annotationProcessorPaths);
            m.put("source", source);
            m.put("target", target);
            return m;
        }

        public static CompilerExec fromMap(Map<String, Object> m) {
            CompilerExec e = new CompilerExec();
            e.id = (String) m.get("id");
            e.release = (String) m.get("release");
            e.proc = (String) m.get("proc");
            Object app = m.get("annotationProcessorPaths");
            e.annotationProcessorPaths = Boolean.TRUE.equals(app) || "true".equals(app);
            e.source = (String) m.get("source");
            e.target = (String) m.get("target");
            return e;
        }

        public String getId() {
            return id;
        }

        public String getRelease() {
            return release;
        }

        public String getProc() {
            return proc;
        }

        public boolean isAnnotationProcessorPaths() {
            return annotationProcessorPaths;
        }
    }

    /** Shims used by fromMap so the model layer does not depend on io.nop.ib.json directly. */
    static final class JsonListAdapter {
        static Map<String, Object> asMapOrEmpty(Object value) {
            Map<String, Object> out = new LinkedHashMap<>();
            if (value instanceof Map<?, ?> m) {
                for (Map.Entry<?, ?> e : m.entrySet())
                    out.put(String.valueOf(e.getKey()), e.getValue());
            }
            return out;
        }

        static List<Object> asListOrEmpty(Object value) {
            List<Object> out = new ArrayList<>();
            if (value instanceof Iterable<?> iter) {
                for (Object o : iter)
                    out.add(o);
            }
            return out;
        }
    }
}
