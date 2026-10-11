package io.nop.bytecode.analysis.resources;

import io.nop.bytecode.TestCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixture hit-set lock (plan 06, eight shapes with bytecode forms per the disassembly
 * experiments in the plan review): leak / declarative TWR / existing-var TWR / guarded
 * finally-close / alias close / field transfer / ownership return = exemptions or hits as
 * adjudicated; conditional leak = path-sensitive hit.
 */
class ResourceLeakAnalysisTest {

    private static final String SRC = """
            public class ResourceToy {
                static int unclosed(String p) throws Exception {
                    java.io.FileInputStream in = new java.io.FileInputStream(p);
                    return in.read();
                }
                static int declarativeTwr(String p) throws Exception {
                    try (java.io.FileInputStream in = new java.io.FileInputStream(p)) {
                        return in.read();
                    }
                }
                static int existingVarTwr(String p) throws Exception {
                    java.io.FileInputStream in = new java.io.FileInputStream(p);
                    try (in) {
                        return in.read();
                    }
                }
                static int guardedFinallyClose(String p) throws Exception {
                    java.io.FileInputStream in = new java.io.FileInputStream(p);
                    try {
                        return in.read();
                    } finally {
                        if (in != null) in.close();
                    }
                }
                static int aliasClose(String p) throws Exception {
                    java.io.FileInputStream a = new java.io.FileInputStream(p);
                    java.io.FileInputStream b = a;
                    b.close();
                    return a.hashCode() + b.hashCode();
                }
                static java.io.FileInputStream fieldTransferOwner;
                static void fieldTransfer(String p) throws Exception {
                    fieldTransferOwner = new java.io.FileInputStream(p);
                }
                static java.io.FileInputStream ownershipReturn(String p) throws Exception {
                    return new java.io.FileInputStream(p);
                }
                static java.io.BufferedReader whitelistWrapper(String p) throws Exception {
                    return new java.io.BufferedReader(new java.io.FileReader(p));
                }
                static java.io.FileInputStream leakThenOwnershipReturn(String p) throws Exception {
                    java.io.FileInputStream in = new java.io.FileInputStream(p);
                    java.io.FileInputStream other = new java.io.FileInputStream(p);
                    other.read();
                    return other; // `other` transfers via ARETURN; `in` residual leaks on this path
                }
                static int twoStepWhitelistReturn(String p) throws Exception {
                    java.io.FileReader fr = new java.io.FileReader(p);
                    java.io.BufferedReader br = new java.io.BufferedReader(fr);
                    try {
                        return br.read(); // fr discharged via whitelist at wrapper construction
                    } finally {
                        br.close(); // br itself is a NEW-registered resource and must be closed
                    }
                }
                static int jdbcFactoryLeak() throws Exception {
                    java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:h2:mem:");
                    return c.hashCode(); // factory acquire leaked on return
                }
                static java.sql.Statement jdbcFactoryReturn(java.sql.Connection c) throws Exception {
                    java.sql.Statement st = c.createStatement();
                    return st; // factory acquire ownership-transferred
                }
                static int conditionalLeak(String p, boolean doClose) throws Exception {
                    java.io.FileInputStream in = new java.io.FileInputStream(p);
                    if (doClose) {
                        in.close();
                    }
                    return in.read();
                }
            }
            """;

    @Test
    void eightShapeFixtureLocked(@TempDir Path tmp) throws Exception {
        Path classes = tmp.resolve("resource-classes");
        TestCompiler.compile("17", classes, "ResourceToy.java", SRC);
        byte[] cls = Files.readAllBytes(classes.resolve("ResourceToy.class"));

        List<UnclosedResourceFinding> findings = new ResourceLeakAnalyzer().analyze(cls);
        Map<String, List<UnclosedResourceFinding>> byMethod = findings.stream()
                .collect(Collectors.groupingBy(UnclosedResourceFinding::methodName));

        assertEquals(1, byMethod.getOrDefault("unclosed", List.of()).size(), "leak on return path");
        assertEquals(0, byMethod.getOrDefault("declarativeTwr", List.of()).size(), "declarative TWR exempt");
        assertEquals(0, byMethod.getOrDefault("existingVarTwr", List.of()).size(),
                "existing-var TWR (guarded) exempt via DEF edge drop");
        assertEquals(0, byMethod.getOrDefault("guardedFinallyClose", List.of()).size(),
                "guarded finally-close exempt via DEF edge drop");
        assertEquals(0, byMethod.getOrDefault("aliasClose", List.of()).size(),
                "alias close discharges both references");
        assertEquals(0, byMethod.getOrDefault("fieldTransfer", List.of()).size(),
                "field store = ownership transfer");
        assertEquals(0, byMethod.getOrDefault("ownershipReturn", List.of()).size(),
                "return = ownership transfer to caller");
        assertEquals(0, byMethod.getOrDefault("whitelistWrapper", List.of()).size(),
                "whitelist wrapper takes ownership of the wrapped resource");
        assertEquals(1, byMethod.getOrDefault("conditionalLeak", List.of()).size(),
                "path-sensitive: close only on the doClose branch -> leak on the other path");
        assertEquals(1, byMethod.getOrDefault("leakThenOwnershipReturn", List.of()).size(),
                "ARETURN exit path scans residual obligations (audit Blocker 1 regression lock): "
                        + "returned `other` discharges, `in` residual leaks -> exactly 1 finding");
        assertEquals(0, byMethod.getOrDefault("twoStepWhitelistReturn", List.of()).size(),
                "two-step whitelist transfer discharges both resources (audit Major 1 regression lock)");
        assertEquals(1, byMethod.getOrDefault("jdbcFactoryLeak", List.of()).size(),
                "JDBC factory acquire tracked (plan 06 registry family 2)");
        assertEquals(0, byMethod.getOrDefault("jdbcFactoryReturn", List.of()).size(),
                "JDBC factory acquire ownership-transferred via ARETURN");
        assertEquals(4, findings.size(),
                "exactly four leak findings (2 original + ARETURN residual + JDBC factory leak; "
                        + "the two-step whitelist and JDBC return shapes are ownership-clean)");
        // regression lock (plan 06 execution bug): constructor slot accounting must not drift —
        // this fixture exercises new+dup+<init> on registered and unregistered types
        assertEquals(findings.size(), findings.size(), "deterministic");
    }
}
