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
        return mod;
    }

    private void parseCompilerPlugin(Element plugin, IbModule mod) {
        // executions first, then plugin-level configuration as the "default" entry
        for (Element exe : executions(plugin)) {
            Element cfg = first(exe, "configuration");
            if (cfg == null)
                continue;
            IbModule.CompilerExec exec = compilerExecFrom(exeName(exe), cfg);
            mod.getCompilerExecs().add(exec);
        }
        Element cfg = first(plugin, "configuration");
        if (cfg != null && !mod.getCompilerExecs().isEmpty()) {
            // plugin-level config merges under each execution in effective poms are
            // already materialized per-execution; plugin-level alone covers the case
            // where executions carry no own configuration
            boolean anyExecCfg = false;
            for (Element exe : executions(plugin)) {
                if (first(exe, "configuration") != null)
                    anyExecCfg = true;
            }
            if (!anyExecCfg) {
                mod.getCompilerExecs().add(compilerExecFrom("default-compile", cfg));
            }
        } else if (cfg != null && mod.getCompilerExecs().isEmpty()) {
            mod.getCompilerExecs().add(compilerExecFrom("default-compile", cfg));
        }
    }

    private IbModule.CompilerExec compilerExecFrom(String id, Element cfg) {
        IbModule.CompilerExec exec = new IbModule.CompilerExec();
        exec.id = id;
        exec.release = text(first(cfg, "release"));
        exec.proc = text(first(cfg, "proc"));
        exec.annotationProcessorPaths = first(cfg, "annotationProcessorPaths") != null;
        exec.source = text(first(cfg, "source"));
        exec.target = text(first(cfg, "target"));
        return exec;
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
        Element props = first(cfg, "systemPropertyVariables");
        if (props != null) {
            Map<String, String> map = new LinkedHashMap<>();
            for (Element prop : children(props, null)) {
                if (prop.getNodeName().equals("property"))
                    continue;
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
