package io.nop.lint.js.tsc;

import io.nop.lint.core.semantic.TypeResolutionException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The real-environment round-trip of the tsc bridge (roadmap item 20 Phase
 * 3, Minimum Rules #22): a temporary TypeScript project with cross-file
 * type references goes initProject → type query → assignability query
 * through a real Node process running the real typescript compiler API
 * (the {@code NodeTscBridge} over the shared helper script), and a tsconfig
 * change provably rebuilds the program (design 11 §4). The resolver adapter
 * on top of this bridge is covered by {@link TscTypeResolverTest} and the
 * L2 demo suite; this test proves the environment itself.
 *
 * <p>Environment contract (adjudicated, plan Phase 3): when Node or the
 * typescript package is missing, this test reports <em>skipped</em> —
 * counted as skipped in every summary, never silently green — and the skip
 * reason names the missing piece. A present-but-broken environment (the
 * bridge fails to answer) still fails the test.</p>
 */
public class TestTscBridgeReal {

    @TempDir
    Path projectDir;

    @BeforeAll
    static void requireRealEnvironment() {
        TscBridgeConfig config;
        try {
            config = TscBridgeConfig.defaultEnvironment();
        } catch (TscBridgeUnavailableException e) {
            Assumptions.abort("tsc bridge e2e skipped (environment contract): " + e);
            return;
        }
        if (!config.isEnvironmentUsable()) {
            Assumptions.abort("tsc bridge e2e skipped: node/helper script not usable on this machine");
        }
    }

    @Test
    public void crossFileProjectRoundTrip() throws IOException {
        Path typesFile = write("types.ts", """
                export interface Money {
                    amount: number;
                    currency: string;
                }
                """);
        Path usageFile = write("usage.ts", """
                import { Money } from "./types";

                export function total(m: Money): number {
                    return m.amount;
                }
                """);
        Path assignFile = write("assign.ts", """
                const value = 42;
                const name: string = "n";
                """);
        writeTsconfig("{\"compilerOptions\":{\"target\":\"es2020\",\"strict\":true,\"noEmit\":true},"
                + "\"include\":[\"*.ts\"]}");

        try (NodeTscBridge bridge = new NodeTscBridge(TscBridgeConfig.defaultEnvironment())) {
            NodeTscBridge.ProjectInfo info = bridge.initProject(projectDir.resolve("tsconfig.json"));
            assertTrue(info.rebuilt(), "the first init builds the program");
            assertTrue(info.fileCount() > 2, "lib + all three project files are in the program");

            // cross-file rendered type: the parameter's type names the
            // imported interface, resolved in the project's own checker
            // (`m` sits at usage.ts line 2, column 22)
            String rendered = bridge.getTypeAtLocation(abs(usageFile), 2, 22);
            assertTrue(rendered.contains("Money"),
                    "the type of `m` must resolve across files, got: " + rendered);

            // assignability, expectedType form: the const inferred as the
            // literal 42 is assignable to number; a string is not
            assertTrue(bridge.isTypeAssignableTo(abs(assignFile), 0, 6, "number"),
                    "the inferred const is assignable to number");
            assertEquals(false, bridge.isTypeAssignableTo(abs(assignFile), 1, 6, "number"),
                    "a string is not assignable to number");

            // assignability, node-reference form: the literal 42 assigns to
            // the declared number (Money.amount), the declared number does
            // not assign back to the literal
            NodeTscBridge.NodeRef value = new NodeTscBridge.NodeRef(abs(assignFile), 0, 6);
            NodeTscBridge.NodeRef amount = new NodeTscBridge.NodeRef(abs(typesFile), 1, 4);
            assertTrue(bridge.isTypeAssignableTo(value, amount),
                    "literal 42 → number across files");
            assertEquals(false, bridge.isTypeAssignableTo(amount, value),
                    "number → literal 42 must not assign");
        }
    }

    @Test
    public void tsconfigChangeRebuildsTheProgram() throws IOException {
        write("a.ts", "export const a = 1;\n");
        writeTsconfig("{\"compilerOptions\":{\"target\":\"es2020\",\"strict\":true,\"noEmit\":true},"
                + "\"include\":[\"*.ts\"]}");

        try (NodeTscBridge bridge = new NodeTscBridge(TscBridgeConfig.defaultEnvironment())) {
            NodeTscBridge.ProjectInfo first = bridge.initProject(projectDir.resolve("tsconfig.json"));
            assertTrue(first.rebuilt());

            NodeTscBridge.ProjectInfo unchanged = bridge.initProject(projectDir.resolve("tsconfig.json"));
            assertEquals(first.cacheKey(), unchanged.cacheKey(), "unchanged config keeps the cache key");

            writeTsconfig("{\"compilerOptions\":{\"target\":\"es2020\",\"strict\":false,\"noEmit\":true},"
                    + "\"include\":[\"*.ts\"]}");
            NodeTscBridge.ProjectInfo rebuilt = bridge.initProject(projectDir.resolve("tsconfig.json"));
            assertNotEquals(first.cacheKey(), rebuilt.cacheKey(),
                    "a tsconfig change must rebuild the program (design 11 §4)");
        }
    }

    @Test
    public void queryOutsideTheProjectFailsVisibly() throws IOException {
        write("a.ts", "export const a = 1;\n");
        writeTsconfig("{\"compilerOptions\":{\"target\":\"es2020\",\"strict\":true,\"noEmit\":true},"
                + "\"include\":[\"*.ts\"]}");

        try (NodeTscBridge bridge = new NodeTscBridge(TscBridgeConfig.defaultEnvironment())) {
            bridge.initProject(projectDir.resolve("tsconfig.json"));
            assertThrows(TscQueryException.class,
                    () -> bridge.getTypeAtLocation(projectDir.resolve("not-in-project.ts").toString(), 0, 0),
                    "a query outside the initialized project must fail visibly, never answer silently");
        }
    }

    // ==================== helpers ====================

    private Path write(String name, String content) throws IOException {
        Path file = projectDir.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    private void writeTsconfig(String content) throws IOException {
        write("tsconfig.json", content);
    }

    private String abs(Path file) {
        return file.toAbsolutePath().normalize().toString();
    }
}
