package io.nop.ib.compile;

import io.nop.ib.model.IbModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestClasspathMaterializer {

    @Test
    void testExternalJarResolvesCommonRepoFirstThenWorktreeFallback(@TempDir Path tmp) throws Exception {
        Path common = tmp.resolve("common-repo");
        Path worktree = tmp.resolve(".wt-repo");
        String rel = "com/example/lib/1.0/lib-1.0.jar";
        Files.createDirectories(common.resolve(rel).getParent());
        Files.writeString(common.resolve(rel), "common");
        IbModel model = new IbModel();
        model.setLocalRepo(worktree.toString());

        ClasspathMaterializer m = new ClasspathMaterializer(model);
        // NOP_IB_COMMON_REPO override points at the temp common repo
        ProcessBuilder pb = new ProcessBuilder("true");
        pb.environment().put("NOP_IB_COMMON_REPO", common.toString());
        // env can't be set for the current process portably; invoke via reflection-free
        // path: temporarily rely on the default resolution with user.home override
        String oldHome = System.getProperty("user.home");
        System.setProperty("user.home", tmp.toString());
        try {
            Path jar = m.externalJar("com.example", "lib", "1.0", null);
            // ~/.m2/repository under overridden user.home does not contain the artifact
            // -> falls back to the worktree repo path (even though the file is absent)
            assertEquals(worktree.resolve(rel), jar);
            // now seed the "common" repo at user.home/.m2/repository and require it wins
            Path homeCommon = tmp.resolve(".m2/repository").resolve(rel);
            Files.createDirectories(homeCommon.getParent());
            Files.writeString(homeCommon, "common");
            jar = m.externalJar("com.example", "lib", "1.0", null);
            assertEquals(homeCommon, jar);
            assertTrue(Files.isRegularFile(jar));
        } finally {
            System.setProperty("user.home", oldHome);
        }
    }

    @Test
    void testClassifierShape(@TempDir Path tmp) {
        IbModel model = new IbModel();
        model.setLocalRepo(tmp.toString());
        ClasspathMaterializer m = new ClasspathMaterializer(model);
        Path jar = m.externalJar("g", "a", "1.0", "tests");
        assertEquals(tmp.resolve("g/a/1.0/a-1.0-tests.jar"), jar);
    }
}
