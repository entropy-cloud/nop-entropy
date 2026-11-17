package io.nop.ib.service;

import io.nop.ib.cli.MvnCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave 11 item 2 pin: the {@code mvn} passthrough runs on the SAME per-project
 * lane as test/check jobs — a mvn job and a test job submitted concurrently
 * for one project execute strictly serially (no target/ trampling).
 */
class TestMvnLane {

    @AfterEach
    void restoreSeam() {
        MvnCommand.runner = (root, args, out, err) ->
                new io.nop.ib.compile.MavenFallback(root).delegate(args, out, err);
    }

    private Path fixtureProject() throws Exception {
        Path repo = Files.createTempDirectory("ib-mvn-lane");
        Files.createDirectories(repo.resolve(".nop-ib"));
        Files.writeString(repo.resolve("pom.xml"), "<project/>");
        Map<String, Object> model = new java.util.LinkedHashMap<>();
        model.put("formatVersion", "1");
        model.put("repoRoot", repo.toString());
        model.put("pomHashes", new java.util.LinkedHashMap<>());
        model.put("modules", List.of());
        Files.writeString(repo.resolve(".nop-ib/model.json"),
                io.nop.ib.json.JsonOut.write(model));
        Process p = new ProcessBuilder("git", "init", "-q").directory(repo.toFile()).start();
        p.waitFor();
        new ProcessBuilder("git", "config", "user.email", "t@t").directory(repo.toFile()).start().waitFor();
        new ProcessBuilder("git", "config", "user.name", "t").directory(repo.toFile()).start().waitFor();
        new ProcessBuilder("git", "add", "-A").directory(repo.toFile()).start().waitFor();
        new ProcessBuilder("git", "commit", "-qm", "init").directory(repo.toFile()).start().waitFor();
        return repo;
    }

    @Test
    void mvnJobAndTestJobSerializeOnOneLane() throws Exception {
        Path repo = fixtureProject();
        // fake "maven" that takes ~300ms — proves overlap would be detected
        MvnCommand.runner = (root, args, out, err) -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try { out.append("fake mvn done\n"); } catch (java.io.IOException e) { throw new io.nop.ib.NopIbException("fake runner io error", e); }
            return 0;
        };
        ServiceCore core = new ServiceCore(4);
        BuildJob mvn = core.submit(repo.toString(), "mvn", List.of("install"), true);
        BuildJob test = core.submit(repo.toString(), "test", List.of(), true);
        core.await(mvn);
        core.await(test);
        assertEquals(BuildJob.State.DONE, mvn.state);
        assertEquals(BuildJob.State.DONE, test.state);
        // strict mutual exclusion: the windows [startedAt, finishedAt] must not overlap
        boolean mvnFirst = mvn.startedAt <= test.startedAt;
        long firstEnd = mvnFirst ? mvn.finishedAt : test.finishedAt;
        long secondStart = mvnFirst ? test.startedAt : mvn.startedAt;
        assertTrue(secondStart >= firstEnd,
                "mvn and test jobs on the same project must never execute concurrently");
    }

    @Test
    void mvnCmdIsServiceableAndLaneCountsIt() throws Exception {
        Path repo = fixtureProject();
        MvnCommand.runner = (root, args, out, err) -> {
            try { out.append("ok\n"); } catch (java.io.IOException e) { throw new io.nop.ib.NopIbException("fake runner io error", e); }
            return 0;
        };
        ServiceCore core = new ServiceCore(4);
        BuildJob job = core.submit(repo.toString(), "mvn", List.of("-pl", ":m", "package"), true);
        core.await(job);
        assertEquals(BuildJob.State.DONE, job.state);
        assertTrue(job.payload.contains("ok"), "delegate output reaches the job payload");
    }
}
