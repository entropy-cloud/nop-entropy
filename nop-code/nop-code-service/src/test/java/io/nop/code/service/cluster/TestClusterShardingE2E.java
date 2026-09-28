package io.nop.code.service.cluster;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.core.cluster.IndexShard;
import io.nop.code.core.cluster.IndexShardPlanner;
import io.nop.code.dao.entity.NopCodeSymbol;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.code.api.dto.IndexFileSetResult;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * N6.3 end-to-end: local git repo → workspace prepare (clone/reuse/re-checkout) → shard
 * plan → per-shard indexFileSet → DB symbol set equals the full indexDirectory result
 * (union equivalence). Failure paths (unconfigured root, invalid revision) are explicit.
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestClusterShardingE2E extends JunitAutoTestCase {

    @Inject
    ICodeIndexService codeIndexService;

    @Inject
    IDaoProvider daoProvider;

    @TempDir
    Path tempDir;

    private Path originRepo;

    private static final String ALICE = """
            package app;

            public class Alice {
                public String greet() { return "hi"; }
            }
            """;

    private static final String BOB = """
            package app;

            public class Bob {
                public String echo(String s) { return s; }
            }
            """;

    private static final String CAROL = """
            package app;

            public class Carol {
                public int add(int a, int b) { return a + b; }
            }
            """;

    private Path createGitRepo(String headRevision) throws Exception {
        originRepo = tempDir.resolve("origin/app-repo");
        Files.createDirectories(originRepo);
        git(originRepo, "init");
        git(originRepo, "config", "user.email", "t@t");
        git(originRepo, "config", "user.name", "t");
        writeAndCommit(originRepo, "src/app/Alice.java", ALICE, "init");
        writeAndCommit(originRepo, "src/app/Bob.java", BOB, "add bob");
        writeAndCommit(originRepo, "src/app/Carol.java", CAROL, "add carol");
        return originRepo;
    }

    private void writeAndCommit(Path repo, String file, String content, String message) throws Exception {
        Path target = repo.resolve(file);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
        git(repo, "add", ".");
        git(repo, "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-m", message);
    }

    private static void git(Path dir, String... args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder();
        List<String> command = new ArrayList<>();
        command.add("git");
        for (String arg : args) {
            command.add(arg);
        }
        pb.command(command);
        pb.directory(dir.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        process.waitFor();
        assertTrue(process.exitValue() == 0, "git failed: " + output);
    }

    private Set<String> indexedQualifiedNames(String indexId) {
        IEntityDao<NopCodeSymbol> dao = daoProvider.daoFor(NopCodeSymbol.class);
        return dao.findAllByQuery(new QueryBean().addFilter(FilterBeans.eq("indexId", indexId)))
                .stream()
                .map(NopCodeSymbol::getQualifiedName)
                .filter(qn -> qn != null && qn.startsWith("app."))
                .collect(Collectors.toSet());
    }

    @Test
    void testWorkspaceLifecycle() throws Exception {
        Path repo = createGitRepo(null);
        String revision = git0(repo, "rev-parse", "HEAD");

        String root = tempDir.resolve("workspaces").toString();
        ClusterWorkspaceManager manager = new ClusterWorkspaceManager(root);

        ClusterWorkspaceManager.WorkspaceInfo first = manager.prepare(repo.toString(), revision);
        assertFalse(first.isReused(), "first prepare must clone");
        assertTrue(Files.isDirectory(Path.of(first.getWorkspacePath(), ".git")),
                "workspace must be a real git checkout");

        ClusterWorkspaceManager.WorkspaceInfo second = manager.prepare(repo.toString(), revision);
        assertTrue(second.isReused(), "same revision must reuse the workspace");
        assertEquals(first.getWorkspacePath(), second.getWorkspacePath());

        // new commit on origin → re-checkout branch-backed revision is out of scope for a
        // detached sha; assert invalid revision fails explicitly
        assertThrows(IllegalArgumentException.class, () -> manager.prepare(repo.toString(), "../escape"),
                "path traversal revision must fail explicitly");
        assertThrows(IllegalArgumentException.class,
                () -> new ClusterWorkspaceManager(null),
                "unconfigured workspace root must fail explicitly");
    }

    private static String git0(Path dir, String... args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder();
        List<String> command = new ArrayList<>();
        command.add("git");
        for (String arg : args) {
            command.add(arg);
        }
        pb.command(command);
        pb.directory(dir.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        process.waitFor();
        assertTrue(process.exitValue() == 0, "git failed: " + output);
        return output.trim();
    }

    @Test
    void testShardExecutionUnionEqualsFullIndex() throws Exception {
        createGitRepo(null);
        String revision = git0(originRepo, "rev-parse", "HEAD");
        String root = tempDir.resolve("workspaces2").toString();
        ClusterWorkspaceManager manager = new ClusterWorkspaceManager(root);
        ClusterWorkspaceManager.WorkspaceInfo workspace = manager.prepare(originRepo.toString(), revision);

        List<String> files = List.of("src/app/Alice.java", "src/app/Bob.java", "src/app/Carol.java");
        List<IndexShard> shards = IndexShardPlanner.plan(files, 2);

        String shardIndexId = "n63_shards";
        Set<String> shardPaths = new HashSet<>();
        for (IndexShard shard : shards) {
            if (shard.getFilePaths().isEmpty()) {
                continue;
            }
            IndexFileSetResult result = codeIndexService.indexFileSet(shardIndexId,
                    workspace.getWorkspacePath(), shard.getFilePaths());
            assertEquals(shard.getFilePaths().size(), result.getIndexedCount(),
                    "all shard files must index cleanly");
            assertTrue(result.getSkippedPaths().isEmpty());
            shardPaths.addAll(shard.getFilePaths());
        }
        assertEquals(new HashSet<>(files), shardPaths, "shards must cover the full file set");

        // union equivalence: shard-built index equals a full indexDirectory over the workspace
        String fullIndexId = "n63_full";
        codeIndexService.indexDirectory(fullIndexId, workspace.getWorkspacePath(), "**/*.java");

        assertEquals(indexedQualifiedNames(fullIndexId), indexedQualifiedNames(shardIndexId),
                "shard union must equal the full index on app.* symbols");

        // unknown analyzer → explicit skip
        IndexFileSetResult skipped = codeIndexService.indexFileSet(shardIndexId,
                workspace.getWorkspacePath(), List.of("src/app/notes.unknownext"));
        assertEquals(0, skipped.getIndexedCount());
        assertEquals(List.of("src/app/notes.unknownext"), skipped.getSkippedPaths(),
                "unanalyzable files must be reported, not silently dropped");

        codeIndexService.deleteIndex(fullIndexId);
        codeIndexService.deleteIndex(shardIndexId);
    }
}
