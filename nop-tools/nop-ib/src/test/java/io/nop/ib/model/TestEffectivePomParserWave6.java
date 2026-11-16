package io.nop.ib.model;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave6 6A-2 pins: surefire config dimensions the parser previously dropped.
 * Each test pins one parsed dimension or one route-to-surefire ruling — the
 * defect family 23-31 root cause was exactly an unparsed dimension biting a
 * real corpus, so every dimension here is a regression pin.
 */
class TestEffectivePomParserWave6 {

    private String repoRoot() {
        return System.getProperty("java.io.tmpdir").replaceAll("/$", "");
    }

    private IbModule parse(String surefireCfg) throws Exception {
        String fixture = """
                <?xml version="1.0" encoding="UTF-8"?>
                <projects xmlns="http://maven.apache.org/POM/4.0.0">
                  <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <groupId>g</groupId><artifactId>fixture</artifactId><version>1</version>
                    <build>
                      <sourceDirectory>REPO/nop-tools/nop-ib/src/main/java</sourceDirectory>
                      <testSourceDirectory>REPO/nop-tools/nop-ib/src/test/java</testSourceDirectory>
                      <outputDirectory>REPO/nop-tools/nop-ib/target/classes</outputDirectory>
                      <testOutputDirectory>REPO/nop-tools/nop-ib/target/test-classes</testOutputDirectory>
                      <plugins>
                        <plugin>
                          <artifactId>maven-surefire-plugin</artifactId>
                          <configuration>SUREFIRE</configuration>
                        </plugin>
                      </plugins>
                    </build>
                  </project>
                </projects>
                """.replace("REPO", repoRoot()).replace("SUREFIRE", surefireCfg);
        File tmp = File.createTempFile("ib-effective-w6", ".xml");
        tmp.deleteOnExit();
        Files.writeString(tmp.toPath(), fixture, StandardCharsets.UTF_8);
        List<IbModule> modules = new EffectivePomParser(repoRoot() + "/").parse(tmp);
        assertEquals(1, modules.size());
        return modules.get(0);
    }

    /** wave6 6A-2 pin: positive <groups> tag filter lands on the module fact. */
    @Test
    void testGroupsParsed() throws Exception {
        IbModule mod = parse("<groups>fast, unit</groups>");
        assertEquals("fast, unit", mod.getSurefireGroups());
        // roundtrip through model persistence
        IbModule back = IbModule.fromMap(mod.toMap());
        assertEquals("fast, unit", back.getSurefireGroups());
    }

    /** wave6 6A-2 pin: <property><name/><value/></property> form of
     *  systemPropertyVariables is ingested, not silently dropped. */
    @Test
    void testSystemPropertyVariablePropertyForm() throws Exception {
        IbModule mod = parse("""
                <systemPropertyVariables>
                  <property><name>db.url</name><value>jdbc:h2:mem</value></property>
                  <plainKey>plainValue</plainKey>
                </systemPropertyVariables>
                """);
        assertEquals("jdbc:h2:mem", mod.getSurefireSystemProperties().get("db.url"));
        assertEquals("plainValue", mod.getSurefireSystemProperties().get("plainKey"));
    }

    /** wave6 6A-2 pin: repo-external additionalClasspathElements are kept for
     *  fork classpath append; repo-internal ones route to surefire (I1). */
    @Test
    void testAdditionalClasspathElementsInternalRoutesToSurefire() throws Exception {
        IbModule external = parse("""
                <additionalClasspathElements>
                  <additionalClasspathElement>/opt/external/lib/foo.jar</additionalClasspathElement>
                </additionalClasspathElements>
                """);
        assertTrue(external.getSurefireUnsupportedReason() == null,
                "repo-external element must not disable the fork path");
        assertEquals(1, external.getSurefireAdditionalClasspathElements().size());

        IbModule internal = parse("""
                <additionalClasspathElements>
                  <additionalClasspathElement>REPO/nop-tools/nop-ib/sibling/target/classes</additionalClasspathElement>
                </additionalClasspathElements>
                """.replace("REPO", repoRoot()));
        assertTrue(internal.getSurefireUnsupportedReason() != null,
                "repo-internal element must route the module to surefire");
        assertTrue(internal.getSurefireAdditionalClasspathElements().isEmpty(),
                "internal element must not leak into the fork classpath");
        // roundtrip preserves the ruling
        IbModule back = IbModule.fromMap(internal.toMap());
        assertEquals(internal.getSurefireUnsupportedReason(), back.getSurefireUnsupportedReason());
    }

    /** wave6 6A-2 pin: non-default workingDirectory routes to surefire; the
     *  module's own basedir does not. */
    @Test
    void testWorkingDirectoryRuling() throws Exception {
        IbModule defaultCwd = parse("<workingDirectory>REPO/nop-tools/nop-ib</workingDirectory>"
                .replace("REPO", repoRoot()));
        assertNull(defaultCwd.getSurefireUnsupportedReason(),
                "workingDirectory == module basedir is the fork default");
        IbModule other = parse("<workingDirectory>REPO/nop-tools/other-module</workingDirectory>"
                .replace("REPO", repoRoot()));
        assertTrue(other.getSurefireUnsupportedReason() != null
                        && other.getSurefireUnsupportedReason().contains("workingDirectory"),
                "non-default workingDirectory must route to surefire");
    }

    /** wave6 6A-2 pin: additionalClasspathDependencies route to surefire. */
    @Test
    void testAdditionalClasspathDependenciesRouteToSurefire() throws Exception {
        IbModule mod = parse("""
                <additionalClasspathDependencies>
                  <dependency><groupId>g</groupId><artifactId>a</artifactId><version>1</version></dependency>
                </additionalClasspathDependencies>
                """);
        assertTrue(mod.getSurefireUnsupportedReason() != null
                && mod.getSurefireUnsupportedReason().contains("additionalClasspathDependencies"));
    }

    /** wave6 6A-2 pin: maven.test.skip property drives skipTests like defect 15. */
    @Test
    void testMavenTestSkipPropertyFallback() throws Exception {
        // the property fallback lives at project level, outside the surefire cfg:
        String fixture = """
                <?xml version="1.0" encoding="UTF-8"?>
                <projects xmlns="http://maven.apache.org/POM/4.0.0">
                  <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <groupId>g</groupId><artifactId>fixture</artifactId><version>1</version>
                    <properties><maven.test.skip>true</maven.test.skip></properties>
                    <build>
                      <outputDirectory>REPO/nop-tools/nop-ib/target/classes</outputDirectory>
                    </build>
                  </project>
                </projects>
                """.replace("REPO", repoRoot());
        File tmp = File.createTempFile("ib-effective-w6b", ".xml");
        tmp.deleteOnExit();
        Files.writeString(tmp.toPath(), fixture, StandardCharsets.UTF_8);
        IbModule mod = new EffectivePomParser(repoRoot() + "/").parse(tmp).get(0);
        assertEquals("true", mod.getSurefireSkipTests());
    }
}
