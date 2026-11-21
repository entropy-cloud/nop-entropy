package io.nop.ib.model;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.impl.FileResource;
import io.nop.ib.NopIbException;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses Maven's own effective-pom batch dump into {@link IbModule} facts via
 * the platform XML stack (analysis 2026-11-22-nop-ib-nop-git-platform-reuse;
 * this class previously hand-rolled StAX+DOM and both 2026-11-21 single-module
 * defect roots lived in that coupling — XNodeParser owns the document-shape
 * robustness instead).
 *
 * <p>The dump (design 05 §5.1, IB-G2 adjudication) is either a single XML
 * document with a namespaced {@code <projects>} root (multi-module reactors,
 * one {@code <project>} element per module) or a bare namespaced
 * {@code <project>} document (single-module projects, no trailing content).
 * The whole tree is materialized by XNodeParser; all paths are already
 * interpolated by Maven and only converted repo-relative below. We never parse
 * raw pom.xml — this document is Maven's own output.
 */
public final class EffectivePomParser {
    private final String repoRoot;
    /** true when the dump has the multi-module {@code <projects>} wrapper root;
     *  single-module dumps have a bare {@code <project>} document root instead. */
    private boolean projectsWrapper;

    public EffectivePomParser(String repoRoot) {
        this.repoRoot = repoRoot.endsWith("/") ? repoRoot : repoRoot + "/";
    }

    /** single-module dumps (bare {@code <project>} root) have no wrapper — the
     *  one project IS the repo root module, not an aggregator entry. */
    public boolean hasProjectsWrapper() {
        return projectsWrapper;
    }

    public List<IbModule> parse(File dump) {
        XNode doc;
        try {
            doc = XNodeParser.instance().parseFromResource(new FileResource(dump));
        } catch (Exception e) {
            throw new NopIbException("failed to parse effective-pom dump " + dump, e);
        }
        if (doc == null)
            throw new NopIbException("effective-pom dump is empty: " + dump);
        projectsWrapper = doc.getTagName().equals("projects");

        List<XNode> projects = doc.childrenByTag("project");
        if (projects.isEmpty() && doc.getTagName().equals("project"))
            projects = List.of(doc);
        if (projects.isEmpty())
            throw new NopIbException("effective-pom dump contains no project elements: " + dump);

        List<IbModule> modules = new ArrayList<>(projects.size());
        for (XNode project : projects)
            modules.add(parseProject(project));
        return modules;
    }

    private IbModule parseProject(XNode project) {
        IbModule mod = new IbModule();
        mod.setGroupId(text(child(project, "groupId")));
        mod.setArtifactId(text(child(project, "artifactId")));
        mod.setVersion(text(child(project, "version")));
        mod.setPackaging(orDefault(text(child(project, "packaging")), "jar"));

        XNode parent = child(project, "parent");
        if (parent != null) {
            String pg = text(child(parent, "groupId"));
            String pa = text(child(parent, "artifactId"));
            mod.setParentGa(pg + ":" + pa);
        }

        XNode build = child(project, "build");
        if (build != null) {
            mod.setSourceDir(rel(text(child(build, "sourceDirectory"))));
            mod.setTestSourceDir(rel(text(child(build, "testSourceDirectory"))));
            mod.setOutputDir(rel(text(child(build, "outputDirectory"))));
            mod.setTestOutputDir(rel(text(child(build, "testOutputDirectory"))));
            for (XNode res : children(child(build, "resources"), "resource"))
                addDir(res, mod.getResourceDirs());
            for (XNode res : children(child(build, "testResources"), "testResource"))
                addDir(res, mod.getTestResourceDirs());

            XNode plugins = child(build, "plugins");
            if (plugins != null) {
                for (XNode plugin : children(plugins, "plugin")) {
                    String artifactId = text(child(plugin, "artifactId"));
                    switch (artifactId) {
                        case "maven-compiler-plugin":
                            parseCompilerPlugin(plugin, mod);
                            break;
                        case "exec-maven-plugin":
                            for (XNode exe : executions(plugin))
                                mod.getExecExecutionIds().add(exeName(exe));
                            break;
                        case "maven-surefire-plugin":
                            parseSurefire(plugin, mod);
                            break;
                        default:
                            break;
                    }
                }
            }
        }

        // property-driven skipTests fallback: poms like syncope-ext-openfga-client
        // define <skipTests>true</skipTests> as a PROPERTY consumed by surefire
        // config; some effective-pom shapes do not inline it into the plugin
        // configuration, leaving the module fact unset (defect 15: such modules
        // were dispatched although Maven skips them)
        if (mod.getSurefireSkipTests() == null) {
            XNode props = child(project, "properties");
            if (props != null) {
                String v = text(child(props, "skipTests"));
                if (v == null)
                    v = text(child(props, "skip"));
                if (v == null)
                    v = text(child(props, "maven.test.skip"));
                if (v != null)
                    mod.setSurefireSkipTests(v);
            }
        }

        XNode deps = child(project, "dependencies");
        if (deps != null) {
            for (XNode dep : children(deps, "dependency")) {
                IbModule.DepEdge edge = new IbModule.DepEdge();
                edge.ga = text(child(dep, "groupId")) + ":" + text(child(dep, "artifactId"));
                edge.scope = orDefault(text(child(dep, "scope")), "compile");
                edge.type = orDefault(text(child(dep, "type")), "jar");
                edge.classifier = text(child(dep, "classifier"));
                edge.optional = Boolean.parseBoolean(orDefault(text(child(dep, "optional")), "false"));
                mod.getDependencies().add(edge);
            }
        }

        // basedir = parent dir of outputDirectory's target segment; fall back to source dir.
        // Repo-relative single-module shape: outputDir is "target/classes" with no
        // leading path (defect: basedir stayed null and ModelRefresher skipped the
        // module as a root aggregator — 0-module model on every single-module
        // project). The module lives at the repo root itself.
        String dir = mod.getOutputDir();
        if (dir != null && dir.contains("/target/")) {
            mod.setBasedir(dir.substring(0, dir.lastIndexOf("/target/")));
        } else if (dir != null && (dir.equals("target/classes") || dir.startsWith("target/"))) {
            mod.setBasedir("");
        } else if (mod.getSourceDir() != null && mod.getSourceDir().contains("/src/")) {
            mod.setBasedir(mod.getSourceDir().substring(0, mod.getSourceDir().lastIndexOf("/src/")));
        }
        resolveSurefireExtras(mod);
        return mod;
    }

    /**
     * Post-basedir resolution of surefire extras that need the module location
     * (wave6 6A-2): repo-internal additionalClasspathElements and a non-default
     * workingDirectory both route the module to real surefire — the fork engine
     * cannot mirror them without breaking I1 (no intra-project artifacts) or the
     * cwd=module-basedir contract.
     */
    private void resolveSurefireExtras(IbModule mod) {
        String basedir = mod.getBasedir();
        for (int i = mod.getSurefireAdditionalClasspathElements().size() - 1; i >= 0; i--) {
            String raw = mod.getSurefireAdditionalClasspathElements().get(i);
            String rel = rel(raw);
            boolean internal = rel.startsWith(repoRoot) || !raw.startsWith("/")
                    || (basedir != null && (rel.equals(basedir) || rel.startsWith(basedir + "/")));
            if (internal) {
                mod.getSurefireAdditionalClasspathElements().remove(i);
                mod.setSurefireUnsupportedReason("additionalClasspathElements (repo-internal: "
                        + raw + ")" + suffixReason(mod));
            }
        }
        String workDir = mod.getPendingSurefireWorkingDirectory();
        if (workDir != null) {
            mod.setPendingSurefireWorkingDirectory(null);
            String rel = rel(workDir);
            boolean defaultCwd = basedir != null
                    && (rel.equals(basedir) || rel.equals(basedir + "/") || rel.startsWith(basedir + "/"));
            if (!defaultCwd)
                mod.setSurefireUnsupportedReason("workingDirectory (non-default: " + workDir + ")"
                        + suffixReason(mod));
        }
    }

    private String suffixReason(IbModule mod) {
        String prev = mod.getSurefireUnsupportedReason();
        return prev == null ? "" : "; also: " + prev;
    }

    private void parseCompilerPlugin(XNode plugin, IbModule mod) {
        XNode pluginCfg = child(plugin, "configuration");
        List<XNode> execs = executions(plugin);
        boolean anyExecCfg = false;
        for (XNode exe : execs) {
            if (child(exe, "configuration") != null)
                anyExecCfg = true;
        }
        if (!execs.isEmpty() && anyExecCfg) {
            // per-execution effective config; fields the execution lacks are filled
            // from the plugin-level config (Maven merge semantics: execution wins)
            for (XNode exe : execs) {
                XNode cfg = child(exe, "configuration");
                if (cfg == null)
                    continue;
                mod.getCompilerExecs().add(compilerExecFrom(exeName(exe), cfg, pluginCfg));
            }
        } else if (pluginCfg != null) {
            // no execution-level config: the plugin-level config IS the compile config
            mod.getCompilerExecs().add(compilerExecFrom("default-compile", pluginCfg, null));
        }
    }

    private IbModule.CompilerExec compilerExecFrom(String id, XNode cfg, XNode defaults) {
        IbModule.CompilerExec exec = new IbModule.CompilerExec();
        exec.id = id;
        exec.release = firstNonEmpty(text(child(cfg, "release")), text(child(defaults, "release")));
        exec.proc = firstNonEmpty(text(child(cfg, "proc")), text(child(defaults, "proc")));
        XNode app = child(cfg, "annotationProcessorPaths");
        if (app == null && defaults != null)
            app = child(defaults, "annotationProcessorPaths");
        exec.annotationProcessorPaths = app != null;
        // check-only processors (error-prone, NullAway, SpotBugs) validate but
        // generate nothing: the fast path compiles with -proc:none, whose output
        // is byte-equivalent for them — they must not force an L2 fallback
        exec.checkOnlyProcessors = app != null && allPathsCheckOnly(app);
        exec.parameters = parametersRequested(cfg) || (defaults != null && parametersRequested(defaults));
        exec.unsupportedCompilerArgs = hasUnsupportedCompilerArgs(cfg)
                || (defaults != null && hasUnsupportedCompilerArgs(defaults));
        parseMirrorableArgs(exec, cfg, defaults);
        exec.source = firstNonEmpty(text(child(cfg, "source")), text(child(defaults, "source")));
        exec.target = firstNonEmpty(text(child(cfg, "target")), text(child(defaults, "target")));
        return exec;
    }

    private static final Set<String> CHECK_ONLY_PROCESSOR_GROUPS = Set.of(
            "com.google.errorprone", "com.uber.nullaway", "com.github.spotbugs");

    private boolean allPathsCheckOnly(XNode app) {
        for (XNode path : children(app, "path")) {
            XNode g = child(path, "groupId");
            if (g == null || !CHECK_ONLY_PROCESSOR_GROUPS.contains(text(g)))
                return false;
        }
        return true;
    }

    /** -parameters debug metadata: the standard <parameters>true</parameters>
     *  config element, or a free-form compilerArg entry "-parameters" (spring-batch
     *  style). Affects bytecode (MethodParameters attributes) — must be mirrored. */
    private boolean parametersRequested(XNode cfg) {
        if (cfg == null)
            return false;
        XNode p = child(cfg, "parameters");
        if (p != null && "true".equalsIgnoreCase(text(p)))
            return true;
        return compilerArgTokens(cfg).contains("-parameters");
    }

    /** compilerArgs entries: both child element names are accepted — <arg> is the
     *  current form (nop-rg-vector uses --add-modules via <arg>), <compilerArg> the
     *  deprecated alias still emitted by some poms. */
    private static final Set<String> MIRRORABLE_COMPILER_ARGS = Set.of(
            "-parameters", "-g", "--add-modules", "--enable-preview");

    private List<String> compilerArgTokens(XNode cfg) {
        List<String> tokens = new ArrayList<>();
        if (cfg == null)
            return tokens;
        for (String tag : new String[]{"compilerArgs", "compilerArguments"}) {
            XNode args = child(cfg, tag);
            if (args == null)
                continue;
            for (XNode arg : children(args, "arg")) {
                String t = text(arg);
                if (t != null && !t.isBlank())
                    tokens.add(t.trim());
            }
            for (XNode arg : children(args, "compilerArg")) {
                String t = text(arg);
                if (t != null && !t.isBlank())
                    tokens.add(t.trim());
            }
        }
        return tokens;
    }

    /** ordered compilerArgs with --add-modules values consumed (token stream model:
     *  "--add-modules" then "jdk.incubator.vector"; "--add-modules=x" single-token form). */
    private List<String> orderedArgTokens(XNode cfg) {
        List<String> raw = compilerArgTokens(cfg);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            String t = raw.get(i);
            if (t.startsWith("--add-modules=")) {
                out.add("--add-modules");
                out.add(t.substring("--add-modules=".length()));
            } else {
                out.add(t);
            }
        }
        return out;
    }

    private void parseMirrorableArgs(IbModule.CompilerExec exec, XNode cfg, XNode defaults) {
        for (XNode src : new XNode[]{defaults, cfg}) {
            if (src == null)
                continue;
            List<String> tokens = orderedArgTokens(src);
            for (int i = 0; i < tokens.size(); i++) {
                String t = tokens.get(i);
                if ("--add-modules".equals(t) && i + 1 < tokens.size()) {
                    for (String m : tokens.get(i + 1).split(","))
                        if (!m.isBlank() && !exec.addModules.contains(m.trim()))
                            exec.addModules.add(m.trim());
                    i++;
                } else if ("--enable-preview".equals(t)) {
                    exec.enablePreview = true;
                }
            }
        }
    }

    /** free-form compilerArgs the fast path cannot mirror (anything outside the
     *  MIRRORABLE set): silent omission would produce wrong products, so the
     *  module is flagged and routed to Maven (fail-safe, mirrors depsUnresolved
     *  routing). Mirrorable product-affecting args (--add-modules, --enable-preview,
     *  -parameters, -g) are captured and replayed by the fast path instead. */
    private boolean hasUnsupportedCompilerArgs(XNode cfg) {
        List<String> tokens = orderedArgTokens(cfg);
        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);
            if ("--add-modules".equals(t)) {
                i++; // 值 token 随 flag 一同镜像
                continue;
            }
            if (!MIRRORABLE_COMPILER_ARGS.contains(t))
                return true;
        }
        return false;
    }

    private String firstNonEmpty(String a, String b) {
        return a != null && !a.isEmpty() ? a : b;
    }

    private void parseSurefire(XNode plugin, IbModule mod) {
        XNode cfg = child(plugin, "configuration");
        if (cfg == null)
            return;
        mod.setSurefireArgLine(text(child(cfg, "argLine")));
        String skip = text(child(cfg, "skipTests"));
        if (skip == null)
            skip = text(child(cfg, "skip"));
        mod.setSurefireSkipTests(skip);
        XNode includes = child(cfg, "includes");
        if (includes != null) {
            for (XNode inc : children(includes, "include")) {
                String v = text(inc);
                if (v != null && !v.isBlank())
                    mod.getSurefireIncludes().add(v.trim());
            }
        }
        String excludedGroups = text(child(cfg, "excludedGroups"));
        if (excludedGroups != null && !excludedGroups.isBlank())
            mod.setSurefireExcludedGroups(excludedGroups.trim());
        // positive tag filter parity (wave6 6A-2; mirror of defect 27's
        // excludedGroups): surefire runs ONLY the tag-matched subset
        String groups = text(child(cfg, "groups"));
        if (groups != null && !groups.isBlank())
            mod.setSurefireGroups(groups.trim());
        // additionalClasspathElements (wave6 6A-2): external (repo-external)
        // entries are appended to the fork classpath; repo-internal ones are
        // resolved after basedir computation in parseProject (they route the
        // module to surefire — no-intra-project-jar invariant I1)
        XNode acles = child(cfg, "additionalClasspathElements");
        if (acles != null) {
            for (XNode el : children(acles, "additionalClasspathElement")) {
                String v = text(el);
                if (v != null && !v.isBlank())
                    mod.getSurefireAdditionalClasspathElements().add(v.trim());
            }
        }
        // additionalClasspathDependencies (wave6 6A-2): resolving Maven
        // coordinates to jars is dependency-resolution machinery the fork
        // engine does not replicate — route the module to surefire
        if (child(cfg, "additionalClasspathDependencies") != null)
            mod.setSurefireUnsupportedReason("additionalClasspathDependencies");
        // non-default workingDirectory (wave6 6A-2): fork cwd is a hard contract
        // (module basedir); a surefire-configured cwd change cannot be mirrored
        // without re-verifying every test's relative-path assumptions
        String workDir = text(child(cfg, "workingDirectory"));
        if (workDir != null && !workDir.isBlank())
            mod.setPendingSurefireWorkingDirectory(workDir.trim());
        XNode excludes = child(cfg, "excludes");
        if (excludes != null) {
            for (XNode exc : children(excludes, "exclude")) {
                String v = text(exc);
                if (v != null && !v.isBlank())
                    mod.getSurefireExcludes().add(v.trim());
            }
        }
        XNode envVars = child(cfg, "environmentVariables");
        if (envVars != null) {
            Map<String, String> env = new LinkedHashMap<>();
            for (XNode prop : envVars.getChildren()) {
                if (prop.getTagName().equals("property"))
                    continue;
                String k = text(child(prop, "key"));
                String v = text(child(prop, "value"));
                if (k == null) {
                    k = prop.getTagName();
                    v = text(prop);
                }
                if (k != null && v != null)
                    env.put(k, v);
            }
            if (!env.isEmpty())
                mod.getSurefireEnvironmentVariables().putAll(env);
        }
        XNode props = child(cfg, "systemPropertyVariables");
        if (props != null) {
            Map<String, String> map = new LinkedHashMap<>();
            for (XNode prop : props.getChildren()) {
                // <property><name>k</name><value>v</value></property> form (wave6
                // 6A-2): silently dropping it lost facts — ingest like the
                // element-name form
                if (prop.getTagName().equals("property")) {
                    String k = text(child(prop, "name"));
                    String v = text(child(prop, "value"));
                    if (k != null && v != null)
                        map.put(k, v);
                    continue;
                }
                map.put(prop.getTagName(), text(prop));
            }
            mod.getSurefireSystemProperties().putAll(map);
        }
    }

    private void addDir(XNode res, List<String> dirs) {
        String dir = rel(text(child(res, "directory")));
        if (dir != null)
            dirs.add(dir);
    }

    private List<XNode> executions(XNode plugin) {
        return children(child(plugin, "executions"), "execution");
    }

    private String exeName(XNode exe) {
        return orDefault(text(child(exe, "id")), "default");
    }

    private XNode child(XNode parent, String name) {
        if (parent == null)
            return null;
        return parent.childByTag(name);
    }

    private List<XNode> children(XNode parent, String name) {
        if (parent == null)
            return new ArrayList<>();
        return parent.childrenByTag(name);
    }

    private String text(XNode node) {
        if (node == null)
            return null;
        String t = node.contentText();
        return t == null ? null : t.trim();
    }

    private String orDefault(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private String rel(String path) {
        if (path == null)
            return null;
        if (path.startsWith(repoRoot))
            return path.substring(repoRoot.length());
        return path;
    }
}
