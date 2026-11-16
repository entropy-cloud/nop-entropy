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

class TestEffectivePomParser {
    private static final String FIXTURE = """
                <?xml version="1.0" encoding="UTF-8"?>
                <projects xmlns="http://maven.apache.org/POM/4.0.0">
                  <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <groupId>io.github.entropy-cloud</groupId>
                    <artifactId>nop-ib-fixture</artifactId>
                    <version>2.0.0-SNAPSHOT</version>
                    <packaging>jar</packaging>
                    <parent>
                      <groupId>io.github.entropy-cloud</groupId>
                      <artifactId>nop-tools</artifactId>
                      <version>2.0.0-SNAPSHOT</version>
                    </parent>
                    <dependencyManagement>
                      <dependencies>
                        <dependency>
                          <groupId>junit</groupId>
                          <artifactId>junit-managed</artifactId>
                          <version>1.0</version>
                        </dependency>
                      </dependencies>
                    </dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>io.github.entropy-cloud</groupId>
                        <artifactId>nop-javac</artifactId>
                        <scope>compile</scope>
                      </dependency>
                      <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit-jupiter</artifactId>
                        <scope>test</scope>
                      </dependency>
                      <dependency>
                        <groupId>io.github.entropy-cloud</groupId>
                        <artifactId>nop-upstream</artifactId>
                        <type>test-jar</type>
                        <scope>test</scope>
                        <optional>true</optional>
                      </dependency>
                    </dependencies>
                    <build>
                      <sourceDirectory>/REPO/nop-tools/nop-ib/src/main/java</sourceDirectory>
                      <testSourceDirectory>/REPO/nop-tools/nop-ib/src/test/java</testSourceDirectory>
                      <outputDirectory>/REPO/nop-tools/nop-ib/target/classes</outputDirectory>
                      <testOutputDirectory>/REPO/nop-tools/nop-ib/target/test-classes</testOutputDirectory>
                      <resources>
                        <resource>
                          <directory>/REPO/nop-tools/nop-ib/src/main/resources</directory>
                        </resource>
                      </resources>
                      <testResources>
                        <testResource>
                          <directory>/REPO/nop-tools/nop-ib/src/test/resources</directory>
                        </testResource>
                      </testResources>
                      <plugins>
                        <plugin>
                          <artifactId>maven-compiler-plugin</artifactId>
                          <executions>
                            <execution>
                              <id>default-compile</id>
                              <configuration>
                                <release>25</release>
                                <proc>none</proc>
                                <annotationProcessorPaths>
                                  <path><groupId>x</groupId><artifactId>y</artifactId></path>
                                </annotationProcessorPaths>
                              </configuration>
                            </execution>
                            <execution>
                              <id>default-testCompile</id>
                              <configuration>
                                <release>17</release>
                              </configuration>
                            </execution>
                          </executions>
                        </plugin>
                        <plugin>
                          <artifactId>exec-maven-plugin</artifactId>
                          <executions>
                            <execution>
                              <id>precompile</id>
                              <goals><goal>java</goal></goals>
                            </execution>
                          </executions>
                        </plugin>
                        <plugin>
                          <artifactId>maven-surefire-plugin</artifactId>
                          <configuration>
                            <argLine>-Xmx1G -Dfoo=bar</argLine>
                            <excludedGroups>slow, performance, randomized</excludedGroups>
                            <systemPropertyVariables>
                              <nop.test>1</nop.test>
                            </systemPropertyVariables>
                          </configuration>
                        </plugin>
                      </plugins>
                    </build>
                  </project>
                </projects>
                """;

    private IbModule parseFixture() throws Exception {
        File tmp = File.createTempFile("ib-effective", ".xml");
        tmp.deleteOnExit();
        Files.writeString(tmp.toPath(), FIXTURE.replace("/REPO/", tempRepoRoot()), StandardCharsets.UTF_8);
        List<IbModule> modules = new EffectivePomParser(tempRepoRoot()).parse(tmp);
        assertEquals(1, modules.size());
        return modules.get(0);
    }

    private String tempRepoRoot() {
        return System.getProperty("java.io.tmpdir").replaceAll("/$", "") + "/";
    }

    @Test
    void testBasicFacts() throws Exception {
        IbModule mod = parseFixture();
        assertEquals("nop-ib-fixture", mod.getArtifactId());
        assertEquals("io.github.entropy-cloud:nop-tools", mod.getParentGa());
        assertEquals("nop-tools/nop-ib", mod.getBasedir());
        assertEquals("nop-tools/nop-ib/target/classes", mod.getOutputDir());
        assertEquals("nop-tools/nop-ib/src/main/java", mod.getSourceDir());
        assertEquals(1, mod.getResourceDirs().size());
        assertEquals(1, mod.getTestResourceDirs().size());
    }

    @Test
    void testDependencyEdgesAndDependencyManagementExclusion() throws Exception {
        IbModule mod = parseFixture();
        assertEquals(3, mod.getDependencies().size());
        // dependencyManagement's junit-managed must NOT appear as an edge
        assertTrue(mod.getDependencies().stream().noneMatch(d -> d.getGa().endsWith("junit-managed")));
        IbModule.DepEdge testJar = mod.getDependencies().stream()
                .filter(d -> "test-jar".equals(d.getType())).findFirst().orElseThrow();
        assertEquals("test", testJar.getScope());
        assertTrue(testJar.isOptional());
        assertEquals("nop-upstream", testJar.getGa().substring(testJar.getGa().indexOf(':') + 1));
    }

    @Test
    void testCompilerExecutionsPerExecution() throws Exception {
        IbModule mod = parseFixture();
        assertEquals(2, mod.getCompilerExecs().size());
        IbModule.CompilerExec compile = mod.getCompilerExecs().get(0);
        assertEquals("default-compile", compile.getId());
        assertEquals("25", compile.getRelease());
        assertEquals("none", compile.getProc());
        assertTrue(compile.isAnnotationProcessorPaths());
        IbModule.CompilerExec testCompile = mod.getCompilerExecs().get(1);
        assertEquals("17", testCompile.getRelease());
        assertNull(testCompile.getProc());
        assertTrue(mod.isProcessorModule());
    }

    @Test
    void testExecAndSurefire() throws Exception {
        IbModule mod = parseFixture();
        assertEquals(List.of("precompile"), mod.getExecExecutionIds());
        assertEquals("-Xmx1G -Dfoo=bar", mod.getSurefireArgLine());
        assertEquals("1", mod.getSurefireSystemProperties().get("nop.test"));
        assertNull(mod.getSurefireSkipTests());
    }

    /**
     * Defect 27: surefire excludedGroups must land on the module fact so the
     * fork runner can mirror it via TagFilter (zeebe excludes slow/performance
     * tag groups; without the field ib over-dispatched those tests).
     */
    @Test
    void testSurefireExcludedGroupsParsed() throws Exception {
        IbModule mod = parseFixture();
        assertEquals("slow, performance, randomized", mod.getSurefireExcludedGroups());
    }

    @Test
    void testCapabilityLevel() throws Exception {
        IbModule mod = parseFixture();
        assertEquals("L3", mod.capabilityLevel(true, false));
        assertEquals("L2", mod.capabilityLevel(false, false));
        // fresh module without processors/templates
        IbModule plain = new IbModule();
        assertEquals("L0", plain.capabilityLevel(false, false));
        assertEquals("L1", plain.capabilityLevel(false, true));
    }
}
