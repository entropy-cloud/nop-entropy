package io.nop.ib.tasks;

import io.nop.ib.model.IbModel;
import io.nop.ib.model.IbModule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave 12 Phase 2 pins: incremental three-state (fingerprint hit skip /
 * inputs change rerun / outputs disappearance rerun), fail-loud semantics
 * (exec failure, no outputs, incomplete classpath all carry a reason), and
 * the target/-located temp files (design 08 IB-T2/T4/T6).
 */
class TestTaskRunner {
    @TempDir
    Path repo;

    static final String HELPER = """
            import java.nio.file.Files;
            import java.nio.file.Path;
            public class GenHelper {
                public static void main(String[] args) throws Exception {
                    Path out = Path.of(args[0]);
                    Files.createDirectories(out.getParent());
                    Files.writeString(out, "generated:" + args.length);
                }
            }
            """;

    IbModel model;
    IbModule module;
    static Path helperClasses;

    @BeforeAll
    static void compileHelper(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("GenHelper.java");
        Files.writeString(src, HELPER);
        Path classes = Files.createDirectories(tmp.resolve("classes"));
        var compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null)) {
            Iterable<? extends JavaFileObject> objs = fm.getJavaFileObjectsFromStrings(List.of(src.toString()));
            var task = compiler.getTask(null, null, null, List.of("-d", classes.toString()), null, objs);
            assertTrue(task.call(), "helper compiled");
        }
        helperClasses = classes;
    }

    @BeforeEach
    void setup() throws Exception {
        module = new IbModule();
        module.setGroupId("g");
        module.setArtifactId("modA");
        module.setVersion("1");
        module.setPackaging("jar");
        module.setBasedir("modA");
        module.setOutputDir("modA/target/classes");
        module.setTestOutputDir("modA/target/test-classes");
        model = new IbModel();
        model.setRepoRoot(repo.toString());
        model.addModule(module);

        Path classes = repo.resolve("modA/target/classes");
        Files.createDirectories(classes);
        // real helper bytecode on the module classpath: the fork actually runs it
        Files.copy(helperClasses.resolve("GenHelper.class"), classes.resolve("GenHelper.class"));
        Files.createDirectories(repo.resolve("modA/model"));
        Files.writeString(repo.resolve("modA/model/gen.x"), "input-content-v1");
    }

    private TaskConfig genTask() {
        return new TaskConfig("gen", "before-compile", List.of("model/*.x"), List.of("out/**"),
                new TaskConfig.Exec("java", "GenHelper", null, List.of("out/gen.txt"), "test"));
    }

    @Test
    void executesThenSkipsOnFingerprintHit() {
        TaskRunner r = new TaskRunner(model, repo);
        TaskRunner.TaskResult first = r.run(module, genTask());
        assertTrue(first.ok(), first.reason());
        assertEquals("executed", first.kind());
        assertTrue(Files.exists(repo.resolve("modA/out/gen.txt")));

        TaskRunner.TaskResult second = r.run(module, genTask());
        assertTrue(second.ok(), second.reason());
        assertEquals("skipped", second.kind(), "fingerprint hit must skip the fork");
    }

    @Test
    void inputChangeReruns() throws Exception {
        TaskRunner r = new TaskRunner(model, repo);
        assertTrue(r.run(module, genTask()).ok());
        Files.writeString(repo.resolve("modA/model/gen.x"), "input-content-v2");
        TaskRunner.TaskResult third = r.run(module, genTask());
        assertTrue(third.ok());
        assertEquals("executed", third.kind(), "inputs hash change must invalidate the fingerprint");
    }

    @Test
    void outputDeletionReruns() throws Exception {
        TaskRunner r = new TaskRunner(model, repo);
        assertTrue(r.run(module, genTask()).ok());
        Files.delete(repo.resolve("modA/out/gen.txt"));
        TaskRunner.TaskResult rerun = r.run(module, genTask());
        assertTrue(rerun.ok());
        assertEquals("executed", rerun.kind(), "missing output must invalidate the fingerprint");
        assertTrue(Files.exists(repo.resolve("modA/out/gen.txt")));
    }

    @Test
    void execFailureCarriesReasonAndStderr() {
        TaskConfig bad = new TaskConfig("gen", "before-compile", List.of("model/*.x"), List.of("out/**"),
                new TaskConfig.Exec("java", "NoSuchClass", null, List.of(), "test"));
        TaskRunner r = new TaskRunner(model, repo);
        TaskRunner.TaskResult result = r.run(module, bad);
        assertFalse(result.ok());
        assertTrue(result.reason().contains("exited"), result.reason());
        assertTrue(result.reason().contains("gen"), "reason names the task");
        assertEquals("failed", r.getRecord().get(0).get("kind"));
    }

    @Test
    void noOutputsIsAFailure() {
        TaskConfig empty = new TaskConfig("gen", "before-compile", List.of("model/*.x"), List.of("nowhere/**"),
                new TaskConfig.Exec("java", "GenHelper", null, List.of("sub/untouched.txt"), "test"));
        TaskRunner r = new TaskRunner(model, repo);
        TaskRunner.TaskResult result = r.run(module, empty);
        assertFalse(result.ok());
        assertTrue(result.reason().contains("produced no outputs"), result.reason());
    }

    @Test
    void incompleteClasspathFailsLoud() throws Exception {
        // fabricate a missing external artifact via a copy of the model
        IbModule mod = new IbModule();
        mod.setGroupId("g");
        mod.setArtifactId("modA");
        mod.setVersion("1");
        mod.setPackaging("jar");
        mod.setBasedir("modA");
        mod.setOutputDir("modA/target/classes");
        mod.setTestOutputDir("modA/target/test-classes");
        mod.setExternalArtifacts(List.of("g:missing:jar:1:test"));
        IbModel m2 = new IbModel();
        m2.setRepoRoot(repo.toString());
        m2.setLocalRepo(repo.resolve(".m2-repo").toString());
        m2.addModule(mod);
        TaskRunner r = new TaskRunner(m2, repo);
        TaskRunner.TaskResult result = r.run(mod, genTask());
        assertFalse(result.ok());
        assertTrue(result.reason().contains("classpath incomplete"), "ACTUAL: " + result.reason());
        assertTrue(result.reason().contains("missing-1.jar"), result.reason());
    }

    @Test
    void fingerprintLivesUnderTarget() {
        assertTrue(TaskFingerprint.fileFor(repo.resolve("modA"), "gen").toString()
                .replace('\\', '/').endsWith("modA/target/nop-ib/tasks/gen.fp.json"));
    }

    @Test
    void corruptFingerprintIsIgnoredNotTrusted() throws Exception {
        Path fp = TaskFingerprint.fileFor(repo.resolve("modA"), "gen");
        Files.createDirectories(fp.getParent());
        Files.writeString(fp, "{corrupt");
        assertNull(TaskFingerprint.load(repo.resolve("modA"), "gen"));
    }
}
