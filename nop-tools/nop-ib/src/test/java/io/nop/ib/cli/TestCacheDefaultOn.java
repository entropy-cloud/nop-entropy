package io.nop.ib.cli;

import io.nop.ib.NopIbMain;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 2026-11-20 owner adjudication pins: the green-result cache is ON by default
 * (test/check), {@code --no-cache} is a recognized escape hatch (run-always),
 * and {@code --cache} stays accepted as an explicit no-op. Flag-level pins:
 * default flip is one line in {@link NopIbMain} — here we pin that no flag and
 * {@code --no-cache} both dispatch normally (exit != 3 usage error), on a repo
 * with nothing changed ("compile skipped" path).
 */
class TestCacheDefaultOn {

    private Path repo() throws Exception {
        Path repo = Files.createTempDirectory("ib-cache-def");
        Files.createDirectories(repo.resolve(".nop-ib"));
        Files.writeString(repo.resolve("pom.xml"), "<project/>");
        java.util.Map<String, Object> model = new java.util.LinkedHashMap<>();
        model.put("formatVersion", "1");
        model.put("repoRoot", repo.toString());
        model.put("pomHashes", new java.util.LinkedHashMap<>());
        java.util.Map<String, Object> mod = new java.util.LinkedHashMap<>();
        mod.put("groupId", "g");
        mod.put("artifactId", "fixture");
        mod.put("version", "1");
        mod.put("packaging", "jar");
        mod.put("basedir", "fixture");
        mod.put("dependencies", List.of());
        mod.put("compilerExecutions", List.of());
        mod.put("execExecutionIds", List.of());
        mod.put("surefire", new java.util.LinkedHashMap<>());
        mod.put("externalArtifacts", List.of());
        mod.put("resources", List.of());
        mod.put("testResources", List.of());
        model.put("modules", List.of(mod));
        Files.writeString(repo.resolve(".nop-ib/model.json"),
                io.nop.ib.json.JsonOut.write(model));
        // change-set 探测走 git diff --name-only HEAD —— fixture 须为 git 仓库且有 HEAD 提交
        new ProcessBuilder("git", "init", "-q", repo.toString()).start().waitFor();
        var envVar = new ProcessBuilder("git", "commit", "--allow-empty", "-q", "-m", "init");
        envVar.directory(repo.toFile());
        envVar.environment().putIfAbsent("GIT_AUTHOR_NAME", "t");
        envVar.environment().putIfAbsent("GIT_AUTHOR_EMAIL", "t@t");
        envVar.environment().putIfAbsent("GIT_COMMITTER_NAME", "t");
        envVar.environment().putIfAbsent("GIT_COMMITTER_EMAIL", "t@t");
        envVar.start().waitFor();
        return repo;
    }

    private int run(Path repo, String... flags) {
        java.util.List<String> args = new java.util.ArrayList<>(List.of("test"));
        for (String f : flags)
            args.add(f);
        return NopIbMain.run(args.toArray(new String[0]), repo,
                System.out, System.err);
    }

    /** default (no flag): cache-on path dispatches normally, not a usage error. */
    @Test
    void defaultDispatchesWithoutFlag() throws Exception {
        int code = run(repo());
        assertNotEquals(3, code, "default (cache on) must be accepted");
    }

    /** --no-cache: recognized escape hatch, dispatches normally. */
    @Test
    void noCacheFlagAccepted() throws Exception {
        int code = run(repo(), "--no-cache");
        assertNotEquals(3, code, "--no-cache must be recognized (not fall to positional)");
    }

    /** --cache: kept for backward compatibility, still accepted. */
    @Test
    void cacheFlagStillAccepted() throws Exception {
        int code = run(repo(), "--cache");
        assertNotEquals(3, code, "--cache must stay accepted (explicit no-op)");
    }

    /** both flags together: last wins is not contracted; must still parse (exit != 3). */
    @Test
    void combinedFlagsParse() throws Exception {
        int code = run(repo(), "--no-cache", "--force");
        assertNotEquals(3, code, "combined flags must parse");
    }
}
