package io.nop.code.service.cluster;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.code.service.impl.CodeIndexService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.io.TempDir;
import jakarta.inject.Inject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;


import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * N6.5: per-index access policy enforcement — a deny-one policy must be enforced at both
 * read and write entry points of the shared ICodeIndexService instance (wiring evidence),
 * and the per-index allowedLocalRoot must override the global one.
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
@io.nop.api.core.annotations.autotest.NopTestProperty(name = "nop.search.index-dir",
        value = "./target/n65-access-policy-test-indices")
public class TestIndexAccessPolicyEnforcement extends JunitAutoTestCase {

    @TempDir
    Path tempDir;

    @Inject
    ICodeIndexService codeIndexService;

    static class DenyOnePolicy implements IndexAccessPolicy {
        private final String denied;

        DenyOnePolicy(String denied) {
            this.denied = denied;
        }

        @Override
        public void checkReadAccess(String indexId) {
            if (denied.equals(indexId)) {
                throw new SecurityException("read denied: " + indexId);
            }
        }

        @Override
        public void checkWriteAccess(String indexId) {
            if (denied.equals(indexId)) {
                throw new SecurityException("write denied: " + indexId);
            }
        }
    }

    private CodeIndexService impl() {
        return (CodeIndexService) codeIndexService;
    }

    @Test
    void testDeniedIndexIsRejectedAtReadAndWriteEntryPoints() throws Exception {
        ((CodeIndexService) codeIndexService).setAccessPolicy(new DenyOnePolicy("denied-idx"));
        try {
            // write entry point
            assertThrows(SecurityException.class,
                    () -> codeIndexService.indexDirectory("denied-idx",
                            tempDir.resolve("x").toString(), "**/*.java"));
            // read entry point (source-exposing surface)
            assertThrows(SecurityException.class,
                    () -> codeIndexService.getFiles("denied-idx"));
            assertThrows(SecurityException.class,
                    () -> codeIndexService.getFileSourceCode("denied-idx", "app/A.java"));
        } finally {
            ((CodeIndexService) codeIndexService).setAccessPolicy(null); // restore permissive
        }

        // permissive default restored: same calls must pass (index missing is fine, no policy error)
        assertTrue(codeIndexService.getFiles("denied-idx").isEmpty(),
                "permissive policy must restore access");
    }

    @Test
    void testPerIndexAllowedLocalRootOverridesGlobal() throws Exception {
        // allowedLocalRoot validation is reachable via triggerIncrementalIndex on a directory
        Path projectDir = tempDir.resolve("root-check/app");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("A.java"), "public class A { int x; }");
        // make mtime/size differ so the incremental path runs validation
        Thread.sleep(20);
        Files.writeString(projectDir.resolve("A.java"), "public class A { int y; }");

        ((CodeIndexService) codeIndexService).setAccessPolicy(new IndexAccessPolicy() {
            @Override
            public void checkReadAccess(String indexId) {
            }

            @Override
            public void checkWriteAccess(String indexId) {
            }

            @Override
            public String getAllowedLocalRoot(String indexId) {
                // per-index policy points at a directory that does NOT contain the project
                return tempDir.resolve("elsewhere").toString();
            }
        });
        try {
            assertThrows(Exception.class,
                    () -> codeIndexService.triggerIncrementalIndex("n65-root",
                            "file:" + tempDir.resolve("root-check").toAbsolutePath(), null),
                    "per-index root must override the global root and reject out-of-tree paths");
        } finally {
            ((CodeIndexService) codeIndexService).setAccessPolicy(null);
        }
    }

    @Test
    void testCredentialTravelsViaEnvironmentNeverCommandLine() {
        ProcessBuilder pb = new ProcessBuilder("git", "clone", "https://host/repo.git");
        GitCredentialResolver.GitCredential credential =
                new GitCredentialResolver.GitCredential("alice", "s3cret-value");
        ClusterWorkspaceManager.applyCredential(pb, credential);

        // command line must be credential-free
        String commandLine = String.join(" ", pb.command());
        assertTrue(!commandLine.contains("s3cret-value") && !commandLine.contains("alice"),
                "credentials must never appear in command-line arguments: " + commandLine);
        // environment channel carries them
        assertEquals("alice", pb.environment().get("NOP_GIT_USERNAME"));
        assertEquals("s3cret-value", pb.environment().get("NOP_GIT_PASSWORD"));
    }

    @Test
    void testPermissiveDefaultHasZeroBehaviorChange() throws Exception {
        Path projectDir = tempDir.resolve("permissive");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("B.java"), "public class B { int y; }");
        assertTrue(codeIndexService.indexDirectory("n65-permissive",
                projectDir.toAbsolutePath().toString(), "**/*.java") >= 1,
                "permissive default must not block indexing");
        codeIndexService.deleteIndex("n65-permissive");
    }
}
