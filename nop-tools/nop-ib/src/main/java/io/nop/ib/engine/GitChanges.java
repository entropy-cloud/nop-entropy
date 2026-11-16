package io.nop.ib.engine;

import io.nop.ib.NopIbException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Working-tree change detection for {@code --changed} (uncommitted edits vs HEAD,
 * the AI loop's default baseline since commits land per task).
 */
public final class GitChanges {
    private GitChanges() {
    }

    public static List<String> changedPaths(Path repoRoot) {
        List<String> paths = new ArrayList<>();
        paths.addAll(runGit(repoRoot, "diff", "--name-only", "HEAD"));
        paths.addAll(runGit(repoRoot, "ls-files", "--others", "--exclude-standard"));
        return paths.stream().distinct().sorted().toList();
    }

    private static List<String> runGit(Path repoRoot, String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        for (String arg : args)
            cmd.add(arg);
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(repoRoot.toFile());
            pb.redirectErrorStream(false);
            Process process = pb.start();
            List<String> out = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank())
                        out.add(line.trim());
                }
            }
            int code = process.waitFor();
            if (code != 0)
                throw new NopIbException("git " + String.join(" ", args) + " failed with exit " + code);
            return out;
        } catch (IOException e) {
            throw new NopIbException("failed to run git", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NopIbException("interrupted while running git");
        }
    }
}
