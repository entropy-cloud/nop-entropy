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
                "[INFO]    ch.qos.logback:logback-core:jar:1.5.38:compile -- module ch.qos.logback.core",
                "[INFO]    org.junit.jupiter:junit-jupiter:jar:6.1.3:test -- module org.junit.jupiter");
        assertEquals(2, byModule.get("demo-core").size());
        assertTrue(byModule.get("demo-core").contains("ch.qos.logback:logback-core:jar:1.5.38:compile"));
        assertTrue(byModule.get("demo-core").contains("org.junit.jupiter:junit-jupiter:jar:6.1.3:test"));
    }
}
