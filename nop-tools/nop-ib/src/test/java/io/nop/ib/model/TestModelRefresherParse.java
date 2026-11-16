package io.nop.ib.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-C tail defect regression: dependency:list lines with " (optional)" markers,
 * classifiers, and pom packaging must all parse — dropping optional deps made
 * provided+optional artifacts (nop-message-debezium) vanish from fast-path
 * classpaths and their tests fail discovery with NoClassDefFoundError.
 */
class TestModelRefresherParse {

    private Map<String, List<String>> parse(String... lines) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines)
            sb.append(line).append('\n');
        ModelRefresher refresher = new ModelRefresher(java.nio.file.Path.of("."));
        return refresher.parseDependencyList(sb.toString());
    }

    @Test
    void optionalMarkerParsesInsteadOfDropping() {
        Map<String, List<String>> byModule = parse(
                "[INFO] --- dependency:3.11.0:list (default-cli) @ demo-api ---",
                "[INFO] The following files have been resolved:",
                "[INFO]    io.github.entropy-cloud:nop-message-debezium:jar:2.0.0-SNAPSHOT:provided (optional) -- module nop.message.debezium (auto)",
                "[INFO]    jakarta.inject:jakarta.inject-api:jar:2.0.1:compile -- module jakarta.inject");
        List<String> ea = byModule.get("demo-api");
        assertEquals(2, ea.size(), String.valueOf(ea));
        assertTrue(ea.contains("io.github.entropy-cloud:nop-message-debezium:jar:2.0.0-SNAPSHOT:provided"),
                String.valueOf(ea));
        assertTrue(ea.contains("jakarta.inject:jakarta.inject-api:jar:2.0.1:compile"), String.valueOf(ea));
    }

    @Test
    void pomPackagingSkippedAndClassifierPreserved() {
        Map<String, List<String>> byModule = parse(
                "[INFO] --- dependency:3.11.0:list (default-cli) @ demo-app ---",
                "[INFO] The following files have been resolved:",
                "[INFO]    org.graalvm.polyglot:js:pom:25.2.4:compile -- module org.graalvm.polyglot.js (auto)",
                "[INFO]    com.alibaba.nacos:nacos-client:jar:pure:2.3.2:compile -- module nacos.client",
                "[INFO]    io.netty:netty-resolver-dns-native-macos:jar:osx-aarch_64:4.2.18.Final:runtime -- module io.netty.resolver.dns");
        List<String> ea = byModule.get("demo-app");
        // pom-packaged aggregator: dependency descriptor, not a classpath entry
        assertFalse(ea.stream().anyMatch(a -> a.startsWith("org.graalvm.polyglot:js:")), String.valueOf(ea));
        assertTrue(ea.contains("com.alibaba.nacos:nacos-client:jar:pure:2.3.2:compile"), String.valueOf(ea));
        assertTrue(ea.contains("io.netty:netty-resolver-dns-native-macos:jar:osx-aarch_64:4.2.18.Final:runtime"),
                String.valueOf(ea));
    }

    @Test
    void plainLinesUnchanged() {
        Map<String, List<String>> byModule = parse(
                "[INFO] --- dependency:3.11.0:list (default-cli) @ demo-core ---",
                "[INFO] The following files have been resolved:",
                "[INFO]    ch.qos.logback:logback-core:jar:1.5.38:compile -- module ch.qos.logback.core",
                "[INFO]    org.junit.jupiter:junit-jupiter:jar:6.1.3:test -- module org.junit.jupiter");
        assertEquals(2, byModule.get("demo-core").size());
        assertTrue(byModule.get("demo-core").contains("ch.qos.logback:logback-core:jar:1.5.38:compile"));
        assertTrue(byModule.get("demo-core").contains("org.junit.jupiter:junit-jupiter:jar:6.1.3:test"));
    }

    /** Maven 3.9 reactor output: blocks print out of order relative to anchors
     *  (OpenPDF observation) — index pairing must attribute them correctly. */
    @Test
    void outOfOrderBlocksPairedByIndex() {
        Map<String, List<String>> byModule = parse(
                "[INFO] --- dependency:3.11.0:list (default-cli) @ mod-a ---",
                "[INFO] --- dependency:3.11.0:list (default-cli) @ mod-b ---",
                "[INFO] The following files have been resolved:",
                "[INFO]    org.b:only-b:jar:1.0:compile -- module org.b",
                "[INFO] The following files have been resolved:",
                "[INFO]    org.a:only-a:jar:2.0:test -- module org.a");
        // anchors: a, b; blocks in output order: B(mod-b content), B(mod-a content)
        // → index pairing: anchors[0]=a ↔ blocks[0], anchors[1]=b ↔ blocks[1]
        assertTrue(byModule.get("mod-a").contains("org.b:only-b:jar:1.0:compile"),
                String.valueOf(byModule.get("mod-a")));
        assertTrue(byModule.get("mod-b").contains("org.a:only-a:jar:2.0:test"),
                String.valueOf(byModule.get("mod-b")));
    }
}
