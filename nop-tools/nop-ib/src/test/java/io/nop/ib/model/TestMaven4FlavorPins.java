package io.nop.ib.model;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave 8 Phase 3b — Maven 4 flavor parsing pins for {@link EffectivePomParser}.
 *
 * <p>Live corpus fact: the nop-entropy repo wrapper IS Maven 4.0.0-rc-5 (see
 * {@code .mvn/wrapper/maven-wrapper.properties} in the nop-entropy repo), so every
 * effective-pom dump nop-ib consumes is produced by Maven 4. These pins lock the
 * parser's behavior across the Maven 3 / Maven 4 effective-pom shape differences:
 *
 * <ul>
 *   <li>(a) the parser matches the {@code <project>} element by localPart
 *       ({@code EffectivePomParser#parse}, line 56: {@code reader.getName().getLocalPart()}),
 *       so a project element under the legacy default POM 4.0.0 namespace and one
 *       carrying Maven 4 pom-flavor namespace attributes both parse — namespace URIs
 *       are never compared;</li>
 *   <li>(b) {@code maven.compiler.release} in a Maven 4 build is interpolated by
 *       Maven into the effective compiler-plugin {@code <configuration><release>}
 *       element before the dump; the parser must surface it as a
 *       {@link IbModule.CompilerExec#getRelease()} fact (fallback Level 2 depends on
 *       it to mirror {@code --release});</li>
 *   <li>(c) a root {@code <projects>} with multiple reactor projects yields one
 *       {@link IbModule} per project with per-project facts kept separate.</li>
 * </ul>
 *
 * <p>Fixture style follows {@code TestEffectivePomParser}: inline XML strings,
 * temp-file dump, temp repo root. Unprefixed child elements are matched by
 * {@code getNodeName()}, which equals the localPart for unprefixed elements in
 * both namespace shapes — exactly the shapes Maven's own effective-pom dump emits.
 */
class TestMaven4FlavorPins {

    private static final String POM40_NS = "http://maven.apache.org/POM/4.0.0";
    /** Maven 4 pom-flavor namespace sample (4.1.x model); the parser must not care. */
    private static final String MAVEN4_FLAVOR_NS = "http://maven.apache.org/POM/v4.1.0";

    private List<IbModule> parse(String xml) throws Exception {
        File tmp = File.createTempFile("ib-maven4-pin", ".xml");
        tmp.deleteOnExit();
        Files.writeString(tmp.toPath(), xml.replace("/REPO/", tempRepoRoot()), StandardCharsets.UTF_8);
        return new EffectivePomParser(tempRepoRoot()).parse(tmp);
    }

    private String tempRepoRoot() {
        return System.getProperty("java.io.tmpdir").replaceAll("/$", "") + "/";
    }

    /**
     * Pin (a): namespace-agnostic project matching. One dump mixes a default-namespace
     * project (Maven 3 shape) with a Maven 4 flavor project element that carries the
     * flavor namespace plus xsi schemaLocation attributes; both must parse.
     */
    @Test
    void testDefaultNamespaceAndMaven4FlavorProjectElementsParse() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <projects xmlns="%s">
                  <project xmlns="%s">
                    <groupId>io.github.entropy-cloud</groupId>
                    <artifactId>nop-ib-maven3-shape</artifactId>
                    <version>2.0.0-SNAPSHOT</version>
                    <build>
                      <sourceDirectory>/REPO/m3/src/main/java</sourceDirectory>
                      <testSourceDirectory>/REPO/m3/src/test/java</testSourceDirectory>
                      <outputDirectory>/REPO/m3/target/classes</outputDirectory>
                      <testOutputDirectory>/REPO/m3/target/test-classes</testOutputDirectory>
                    </build>
                  </project>
                  <project xmlns="%s" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                           xsi:schemaLocation="%s https://maven.apache.org/xsd/maven-v4_1_0.xsd">
                    <groupId>io.github.entropy-cloud</groupId>
                    <artifactId>nop-ib-maven4-flavor</artifactId>
                    <version>2.0.0-SNAPSHOT</version>
                    <build>
                      <sourceDirectory>/REPO/m4/src/main/java</sourceDirectory>
                      <testSourceDirectory>/REPO/m4/src/test/java</testSourceDirectory>
                      <outputDirectory>/REPO/m4/target/classes</outputDirectory>
                      <testOutputDirectory>/REPO/m4/target/test-classes</testOutputDirectory>
                    </build>
                  </project>
                </projects>
                """
                .formatted(POM40_NS, POM40_NS, MAVEN4_FLAVOR_NS, MAVEN4_FLAVOR_NS);

        List<IbModule> modules = parse(xml);
        assertEquals(2, modules.size());
        assertEquals("nop-ib-maven3-shape", modules.get(0).getArtifactId());
        assertEquals("nop-ib-maven4-flavor", modules.get(1).getArtifactId());
        assertEquals("m4/src/main/java", modules.get(1).getSourceDir());
        assertEquals("m4", modules.get(1).getBasedir());
    }

    /**
     * Pin (b): {@code maven.compiler.release} — after Maven 4 interpolation the
     * effective dump carries {@code <release>} inside the compiler plugin
     * configuration; the parser must surface it as the release fact on the
     * default-compile exec (Level 2 fallback mirrors {@code --release} from it).
     */
    @Test
    void testCompilerReleasePropertyBecomesReleaseFact() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <projects xmlns="%s">
                  <project xmlns="%s">
                    <groupId>io.github.entropy-cloud</groupId>
                    <artifactId>nop-ib-release-pin</artifactId>
                    <version>2.0.0-SNAPSHOT</version>
                    <properties>
                      <maven.compiler.release>25</maven.compiler.release>
                    </properties>
                    <build>
                      <sourceDirectory>/REPO/r/src/main/java</sourceDirectory>
                      <testSourceDirectory>/REPO/r/src/test/java</testSourceDirectory>
                      <outputDirectory>/REPO/r/target/classes</outputDirectory>
                      <testOutputDirectory>/REPO/r/target/test-classes</testOutputDirectory>
                      <plugins>
                        <plugin>
                          <artifactId>maven-compiler-plugin</artifactId>
                          <configuration>
                            <release>25</release>
                          </configuration>
                        </plugin>
                      </plugins>
                    </build>
                  </project>
                </projects>
                """
                .formatted(POM40_NS, POM40_NS);

        List<IbModule> modules = parse(xml);
        assertEquals(1, modules.size());
        IbModule mod = modules.get(0);
        assertEquals(1, mod.getCompilerExecs().size());
        IbModule.CompilerExec exec = mod.getCompilerExecs().get(0);
        assertEquals("default-compile", exec.getId());
        assertEquals("25", exec.getRelease());
        assertNull(exec.getProc());
    }

    /**
     * Pin (c): a root {@code <projects>} holding multiple reactor projects — each
     * project keeps its own compiler exec facts and dependency edges, order follows
     * document order.
     */
    @Test
    void testRootProjectsElementWithMultipleReactorProjects() throws Exception {
        String compilerPluginA = """
                <plugins>
                  <plugin>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <configuration>
                      <release>17</release>
                    </configuration>
                  </plugin>
                </plugins>
                """;
        String compilerPluginB = compilerPluginA.replace("17", "25");
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <projects xmlns="%s">
                  <project xmlns="%s">
                    <groupId>g</groupId>
                    <artifactId>mod-a</artifactId>
                    <version>1.0</version>
                    <build>
                      <sourceDirectory>/REPO/a/src/main/java</sourceDirectory>
                      <outputDirectory>/REPO/a/target/classes</outputDirectory>
                      <testOutputDirectory>/REPO/a/target/test-classes</testOutputDirectory>
                      %s
                    </build>
                    <dependencies>
                      <dependency>
                        <groupId>g</groupId>
                        <artifactId>mod-b</artifactId>
                        <scope>compile</scope>
                      </dependency>
                    </dependencies>
                  </project>
                  <project xmlns="%s">
                    <groupId>g</groupId>
                    <artifactId>mod-b</artifactId>
                    <version>1.0</version>
                    <build>
                      <sourceDirectory>/REPO/b/src/main/java</sourceDirectory>
                      <outputDirectory>/REPO/b/target/classes</outputDirectory>
                      <testOutputDirectory>/REPO/b/target/test-classes</testOutputDirectory>
                      %s
                    </build>
                  </project>
                </projects>
                """
                .formatted(POM40_NS, POM40_NS, compilerPluginA, POM40_NS, compilerPluginB);

        List<IbModule> modules = parse(xml);
        assertEquals(2, modules.size());
        IbModule a = modules.get(0);
        IbModule b = modules.get(1);
        assertEquals("mod-a", a.getArtifactId());
        assertEquals("mod-b", b.getArtifactId());
        assertEquals("a", a.getBasedir());
        assertEquals("b", b.getBasedir());
        assertEquals("17", a.getCompilerExecs().get(0).getRelease());
        assertEquals("25", b.getCompilerExecs().get(0).getRelease());
        // intra-reactor dependency edge stays on module a only
        assertEquals(1, a.getDependencies().size());
        assertEquals("g:mod-b", a.getDependencies().get(0).getGa());
        assertTrue(b.getDependencies().isEmpty());
        assertNotNull(a.getOutputDir());
    }
}
