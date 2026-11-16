package io.nop.ib.model;

import io.nop.ib.NopIbException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.stax.StAXSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;

/**
 * Streams Maven's own effective-pom batch dump into {@link IbModule} facts.
 *
 * <p>The dump (design 05 §5.1, IB-G2 adjudication) is a single XML document with a
 * {@code <projects>} root and one namespaced {@code <project>} element per reactor
 * module. Each project subtree is materialized to a DOM node (worst case ~1MB) so
 * the fact extraction below is plain tree navigation; the document itself is never
 * fully loaded. All paths are already interpolated by Maven; we only convert them
 * repo-relative. We never parse raw pom.xml — this document is Maven's own output.
 */
public final class EffectivePomParser {
    private final String repoRoot;

    public EffectivePomParser(String repoRoot) {
        this.repoRoot = repoRoot.endsWith("/") ? repoRoot : repoRoot + "/";
    }

    public List<IbModule> parse(File dump) {
        XMLInputFactory inputFactory = XMLInputFactory.newFactory();
        inputFactory.setProperty(XMLInputFactory.IS_COALESCING, true);
        inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        try (InputStream in = new FileInputStream(dump)) {
            XMLStreamReader reader = inputFactory.createXMLStreamReader(in);
            TransformerFactory transformerFactory = TransformerFactory.newInstance();

            List<IbModule> modules = new ArrayList<>();
            while (true) {
                int event = reader.next();
                if (event == XMLStreamConstants.END_DOCUMENT)
                    break;
                if (event == XMLStreamConstants.START_ELEMENT
                        && reader.getName().getLocalPart().equals("project")) {
                    DOMResult result = new DOMResult();
                    transformerFactory.newTransformer().transform(new StAXSource(reader), result);
                    Element project = (Element) result.getNode().getFirstChild();
                    modules.add(parseProject(project));
                }
            }
            reader.close();
            if (modules.isEmpty())
                throw new NopIbException("effective-pom dump contains no project elements: " + dump);
            return modules;
        } catch (IOException | XMLStreamException | javax.xml.transform.TransformerException e) {
            throw new NopIbException("failed to parse effective-pom dump " + dump, e);
        }
    }

    private IbModule parseProject(Element project) {
        IbModule mod = new IbModule();
        mod.setGroupId(text(first(project, "groupId")));
        mod.setArtifactId(text(first(project, "artifactId")));
        mod.setVersion(text(first(project, "version")));
        mod.setPackaging(orDefault(text(first(project, "packaging")), "jar"));

        Element parent = first(project, "parent");
        if (parent != null) {
            String pg = text(first(parent, "groupId"));
            String pa = text(first(parent, "artifactId"));
            mod.setParentGa(pg + ":" + pa);
        }

        Element build = first(project, "build");
        if (build != null) {
            mod.setSourceDir(rel(text(first(build, "sourceDirectory"))));
            mod.setTestSourceDir(rel(text(first(build, "testSourceDirectory"))));
            mod.setOutputDir(rel(text(first(build, "outputDirectory"))));
            mod.setTestOutputDir(rel(text(first(build, "testOutputDirectory"))));
            for (Element res : children(first(build, "resources"), "resource"))
                addDir(res, mod.getResourceDirs());
            for (Element res : children(first(build, "testResources"), "testResource"))
                addDir(res, mod.getTestResourceDirs());

            Element plugins = first(build, "plugins");
            if (plugins != null) {
                for (Element plugin : children(plugins, "plugin")) {
                    String artifactId = text(first(plugin, "artifactId"));
                    switch (artifactId) {
                        case "maven-compiler-plugin":
                            parseCompilerPlugin(plugin, mod);
                            break;
                        case "exec-maven-plugin":
                            for (Element exe : executions(plugin))
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
            Element props = first(project, "properties");
            if (props != null) {
                String v = text(first(props, "skipTests"));
                if (v == null)
                    v = text(first(props, "skip"));
                if (v == null)
                    v = text(first(props, "maven.test.skip"));
                if (v != null)
                    mod.setSurefireSkipTests(v);
            }
        }

        Element deps = first(project, "dependencies");
        if (deps != null) {
            for (Element dep : children(deps, "dependency")) {
                IbModule.DepEdge edge = new IbModule.DepEdge();
                edge.ga = text(first(dep, "groupId")) + ":" + text(first(dep, "artifactId"));
                edge.scope = orDefault(text(first(dep, "scope")), "compile");
                edge.type = orDefault(text(first(dep, "type")), "jar");
                edge.classifier = text(first(dep, "classifier"));
                edge.optional = Boolean.parseBoolean(orDefault(text(first(dep, "optional")), "false"));
                mod.getDependencies().add(edge);
            }
        }

        // basedir = parent dir of outputDirectory's target segment; fall back to source dir
        String dir = mod.getOutputDir();
        if (dir != null && dir.contains("/target/")) {
            mod.setBasedir(dir.substring(0, dir.lastIndexOf("/target/")));
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

    private void parseCompilerPlugin(Element plugin, IbModule mod) {
        Element pluginCfg = first(plugin, "configuration");
        List<Element> execs = executions(plugin);
        boolean anyExecCfg = false;
        for (Element exe : execs) {
            if (first(exe, "configuration") != null)
                anyExecCfg = true;
        }
        if (!execs.isEmpty() && anyExecCfg) {
            // per-execution effective config; fields the execution lacks are filled
            // from the plugin-level config (Maven merge semantics: execution wins)
            for (Element exe : execs) {
                Element cfg = first(exe, "configuration");
                if (cfg == null)
                    continue;
                mod.getCompilerExecs().add(compilerExecFrom(exeName(exe), cfg, pluginCfg));
            }
        } else if (pluginCfg != null) {
            // no execution-level config: the plugin-level config IS the compile config
            mod.getCompilerExecs().add(compilerExecFrom("default-compile", pluginCfg, null));
        }
    }

    private IbModule.CompilerExec compilerExecFrom(String id, Element cfg, Element defaults) {
        IbModule.CompilerExec exec = new IbModule.CompilerExec();
        exec.id = id;
        exec.release = firstNonEmpty(text(first(cfg, "release")), text(first(defaults, "release")));
        exec.proc = firstNonEmpty(text(first(cfg, "proc")), text(first(defaults, "proc")));
        Element app = first(cfg, "annotationProcessorPaths");
        if (app == null && defaults != null)
            app = first(defaults, "annotationProcessorPaths");
        exec.annotationProcessorPaths = app != null;
        // check-only processors (error-prone, NullAway, SpotBugs) validate but
        // generate nothing: the fast path compiles with -proc:none, whose output
        // is byte-equivalent for them — they must not force an L2 fallback
        exec.checkOnlyProcessors = app != null && allPathsCheckOnly(app);
        exec.parameters = parametersRequested(cfg) || (defaults != null && parametersRequested(defaults));
        exec.source = firstNonEmpty(text(first(cfg, "source")), text(first(defaults, "source")));
        exec.target = firstNonEmpty(text(first(cfg, "target")), text(first(defaults, "target")));
        return exec;
    }

    private static final Set<String> CHECK_ONLY_PROCESSOR_GROUPS = Set.of(
            "com.google.errorprone", "com.uber.nullaway", "com.github.spotbugs");

    private boolean allPathsCheckOnly(Element app) {
        for (Element path : children(app, "path")) {
            Element g = first(path, "groupId");
            if (g == null || !CHECK_ONLY_PROCESSOR_GROUPS.contains(text(g)))
                return false;
        }
        return true;
    }

    /** -parameters debug metadata: the standard <parameters>true</parameters>
     *  config element, or a free-form compilerArg entry "-parameters" (spring-batch
     *  style). Affects bytecode (MethodParameters attributes) — must be mirrored. */
    private boolean parametersRequested(Element cfg) {
        if (cfg == null)
            return false;
        Element p = first(cfg, "parameters");
        if (p != null && "true".equalsIgnoreCase(text(p)))
            return true;
        Element args = first(cfg, "compilerArgs");
        if (args != null) {
            for (Element arg : children(args, "compilerArg")) {
                String t = text(arg);
                if (t != null && "-parameters".equals(t.trim()))
                    return true;
            }
        }
        return false;
    }

    private String firstNonEmpty(String a, String b) {
        return a != null && !a.isEmpty() ? a : b;
    }

    private void parseSurefire(Element plugin, IbModule mod) {
        Element cfg = first(plugin, "configuration");
        if (cfg == null)
            return;
        mod.setSurefireArgLine(text(first(cfg, "argLine")));
        String skip = text(first(cfg, "skipTests"));
        if (skip == null)
            skip = text(first(cfg, "skip"));
        mod.setSurefireSkipTests(skip);
        Element includes = first(cfg, "includes");
        if (includes != null) {
            for (Element inc : children(includes, "include")) {
                String v = text(inc);
                if (v != null && !v.isBlank())
                    mod.getSurefireIncludes().add(v.trim());
            }
        }
        String excludedGroups = text(first(cfg, "excludedGroups"));
        if (excludedGroups != null && !excludedGroups.isBlank())
            mod.setSurefireExcludedGroups(excludedGroups.trim());
        // positive tag filter parity (wave6 6A-2; mirror of defect 27's
        // excludedGroups): surefire runs ONLY the tag-matched subset
        String groups = text(first(cfg, "groups"));
        if (groups != null && !groups.isBlank())
            mod.setSurefireGroups(groups.trim());
        // additionalClasspathElements (wave6 6A-2): external (repo-external)
        // entries are appended to the fork classpath; repo-internal ones are
        // resolved after basedir computation in parseProject (they route the
        // module to surefire — no-intra-project-jar invariant I1)
        Element acles = first(cfg, "additionalClasspathElements");
        if (acles != null) {
            for (Element el : children(acles, "additionalClasspathElement")) {
                String v = text(el);
                if (v != null && !v.isBlank())
                    mod.getSurefireAdditionalClasspathElements().add(v.trim());
            }
        }
        // additionalClasspathDependencies (wave6 6A-2): resolving Maven
        // coordinates to jars is dependency-resolution machinery the fork
        // engine does not replicate — route the module to surefire
        if (first(cfg, "additionalClasspathDependencies") != null)
            mod.setSurefireUnsupportedReason("additionalClasspathDependencies");
        // non-default workingDirectory (wave6 6A-2): fork cwd is a hard contract
        // (module basedir); a surefire-configured cwd change cannot be mirrored
        // without re-verifying every test's relative-path assumptions
        String workDir = text(first(cfg, "workingDirectory"));
        if (workDir != null && !workDir.isBlank())
            mod.setPendingSurefireWorkingDirectory(workDir.trim());
        Element excludes = first(cfg, "excludes");
        if (excludes != null) {
            for (Element exc : children(excludes, "exclude")) {
                String v = text(exc);
                if (v != null && !v.isBlank())
                    mod.getSurefireExcludes().add(v.trim());
            }
        }
        Element envVars = first(cfg, "environmentVariables");
        if (envVars != null) {
            Map<String, String> env = new LinkedHashMap<>();
            for (Element prop : children(envVars, null)) {
                if (prop.getNodeName().equals("property"))
                    continue;
                String k = text(first(prop, "key"));
                String v = text(first(prop, "value"));
                if (k == null) {
                    k = prop.getNodeName();
                    v = text(prop);
                }
                if (k != null && v != null)
                    env.put(k, v);
            }
            if (!env.isEmpty())
                mod.getSurefireEnvironmentVariables().putAll(env);
        }
        Element props = first(cfg, "systemPropertyVariables");
        if (props != null) {
            Map<String, String> map = new LinkedHashMap<>();
            for (Element prop : children(props, null)) {
                // <property><name>k</name><value>v</value></property> form (wave6
                // 6A-2): silently dropping it lost facts — ingest like the
                // element-name form
                if (prop.getNodeName().equals("property")) {
                    String k = text(first(prop, "name"));
                    String v = text(first(prop, "value"));
                    if (k != null && v != null)
                        map.put(k, v);
                    continue;
                }
                map.put(prop.getNodeName(), text(prop));
            }
            mod.getSurefireSystemProperties().putAll(map);
        }
    }

    private void addDir(Element res, List<String> dirs) {
        String dir = rel(text(first(res, "directory")));
        if (dir != null)
            dirs.add(dir);
    }

    private List<Element> executions(Element plugin) {
        return children(first(plugin, "executions"), "execution");
    }

    private String exeName(Element exe) {
        return orDefault(text(first(exe, "id")), "default");
    }

    private Element first(Element parent, String name) {
        if (parent == null)
            return null;
        for (Element child : children(parent, name)) {
            return child;
        }
        return null;
    }

    private List<Element> children(Element parent, String name) {
        List<Element> out = new ArrayList<>();
        if (parent == null)
            return out;
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (n instanceof Element && (name == null || n.getNodeName().equals(name)))
                out.add((Element) n);
        }
        return out;
    }

    private String text(Element el) {
        if (el == null)
            return null;
        String t = el.getTextContent();
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
