package io.nop.git.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs the real system git in an isolated environment (no user/system config,
 * no colors) for live-parity comparison.
 */
public final class GitCommandRunner {

    public record Result(int exit, String stdout, String stderr) {
    }

    private GitCommandRunner() {
    }

    public static Result run(Path dir, Map<String, String> extraEnv, String... args) {
        // AI tools always run with a configured identity; give the isolated
        // native git one so identity-absence doesn't mask output differences
        Path globalConfig;
        try {
            globalConfig = java.nio.file.Files.createTempFile("nopgit-global-cfg", null);
            java.nio.file.Files.writeString(globalConfig,
                    "[user]\n\tname = " + Fixture.NAME + "\n\temail = " + Fixture.EMAIL + "\n");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(dir.toFile());
        Map<String, String> env = pb.environment();
        env.put("GIT_CONFIG_NOSYSTEM", "1");
        env.put("GIT_CONFIG_GLOBAL", globalConfig.toString());
        env.put("GIT_CONFIG_SYSTEM", "/dev/null");
        env.put("NO_COLOR", "1");
        env.put("TERM", "dumb");
        env.put("LC_ALL", "C");
        env.putAll(extraEnv);
        try {
            Process process = pb.start();
            byte[] out;
            try (var stream = process.getInputStream()) {
                out = stream.readAllBytes();
            }
            byte[] err;
            try (var stream = process.getErrorStream()) {
                err = stream.readAllBytes();
            }
            int exit = process.waitFor();
            return new Result(exit,
                    new String(out, StandardCharsets.UTF_8),
                    new String(err, StandardCharsets.UTF_8));
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("git invocation failed", e);
        }
    }
}
