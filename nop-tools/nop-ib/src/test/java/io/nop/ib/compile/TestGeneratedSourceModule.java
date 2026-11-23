package io.nop.ib.compile;

import io.nop.ib.index.UnitIndexStore;
import io.nop.ib.model.IbModel;
import io.nop.ib.model.IbModule;
import io.nop.ib.scope.PlanResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Defect 3 (2026-11-22): Maven codegen source roots (antlr4 et al under
 * {@code target/generated-sources}) must participate in the fast path exactly
 * like src/main/java (mvn add-source-root parity):
 *
 * <ul>
 *   <li>an incremental compile of a handwritten source referencing generated
 *       symbols must include the generated .java in the javac source set when
 *       their products are missing;</li>
 *   <li>{@code productsFresh} must report the module stale when generated
 *       sources have no products (bootstrap rebuilds the full set).</li>
 * </ul>
 */
class TestGeneratedSourceModule {

    private static final String GEN_JAVA = """
            package a;

            public class Gen {
                public int value() {
                    return 1;
                }
            }
            """;

    private static final String USE_JAVA = """
            package a;

            public class Use {
                public Gen gen() {
                    return new Gen();
                }
            }
            """;

    private static final String POM_WITH_ANTLR = """
            <project>
              <build>
                <plugins>
                  <plugin>
                    <groupId>org.antlr</groupId>
                    <artifactId>antlr4-maven-plugin</artifactId>
                  </plugin>
                </plugins>
              </build>
            </project>
            """;

    private IbModel model(@TempDir Path repo) throws Exception {
        // handwritten source referencing the generated symbol
        Path src = repo.resolve("mod-a/src/main/java/a");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Use.java"), USE_JAVA);
        // generated source root (as antlr4-maven-plugin would leave it)
        Path gen = repo.resolve("mod-a/target/generated-sources/antlr4/a");
        Files.createDirectories(gen);
        Files.writeString(gen.resolve("Gen.java"), GEN_JAVA);
        // pom declares the codegen plugin (marker for the absent-generated case)
        Files.writeString(repo.resolve("mod-a/pom.xml"), POM_WITH_ANTLR);

        IbModule mod = new IbModule();
        mod.setGroupId("g");
        mod.setArtifactId("mod-a");
        mod.setVersion("1.0");
        mod.setPackaging("jar");
        mod.setBasedir("mod-a");
        mod.setSourceDir("mod-a/src/main/java");
        mod.setOutputDir("mod-a/target/classes");
        mod.setExternalArtifacts(List.of());

        IbModel model = new IbModel();
        model.setRepoRoot(repo.toString());
        model.setLocalRepo(repo.resolve("local-repo").toString());
        model.addModule(mod);
        return model;
    }

    @Test
    void incrementalCompileIncludesGeneratedSources(@TempDir Path repo) throws Exception {
        IbModel model = model(repo);
        IncrementalCompileExecutor executor = new IncrementalCompileExecutor(
                model, new UnitIndexStore(repo));

        PlanResult plan = new PlanResult();
        plan.setCompileModules(List.of("mod-a"));
        plan.getCompileFiles().put("mod-a",
                List.of("mod-a/src/main/java/a/Use.java"));

        IncrementalCompileExecutor.CompileOutcome outcome = executor.compile(plan);

        assertTrue(outcome.isSuccess(), "outcome must succeed, diagnostics="
                + executor.getDiagnostics());
        assertEquals(List.of("mod-a"), outcome.getCompiledModules());
        // handwritten AND generated products both exist
        assertTrue(Files.isRegularFile(
                repo.resolve("mod-a/target/classes/a/Use.class")), "Use.class missing");
        assertTrue(Files.isRegularFile(
                repo.resolve("mod-a/target/classes/a/Gen.class")), "Gen.class missing");
        // no ERROR-level diagnostics: every recorded diagnostic must be a NOTE
        // (javac notes like unchecked-ops are non-fatal), never a failure marker
        executor.getDiagnostics().forEach((m, diags) ->
                diags.forEach(d -> assertTrue(!d.contains("ERROR"), m + ": " + d)));
    }

    @Test
    void generatedProductsArePartOfFreshness(@TempDir Path repo) throws Exception {
        IbModel model = model(repo);
        IncrementalCompileExecutor executor = new IncrementalCompileExecutor(
                model, new UnitIndexStore(repo));
        IbModule mod = model.module("mod-a");

        // fully built (both handwritten and generated products + unit index):
        // fresh
        PlanResult plan = new PlanResult();
        plan.setCompileModules(List.of("mod-a"));
        plan.getCompileFiles().put("mod-a",
                List.of("mod-a/src/main/java/a/Use.java"));
        assertTrue(executor.compile(plan).isSuccess());
        assertTrue(executor.productsFresh(mod),
                "fully built module (generated products included) must be fresh");

        // deleting ONLY the generated product must invalidate freshness
        Files.delete(repo.resolve("mod-a/target/classes/a/Gen.class"));
        assertEquals(false, executor.productsFresh(mod),
                "missing generated product must count as stale");
    }
}
