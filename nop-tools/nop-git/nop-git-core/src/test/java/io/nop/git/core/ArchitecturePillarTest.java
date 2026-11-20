package io.nop.git.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Architecture pillar (design 00-vision section 5): no class outside the
 * {@code io.nop.git} package may import {@code org.eclipse.jgit}, and the
 * non-core modules may not touch JGit at all. Scans the source trees of the
 * nop-git modules (core and cli) directly from disk, so the cli placeholder
 * module is covered without a classpath dependency.
 */
class ArchitecturePillarTest {

    private static final List<String> MODULE_SRC_DIRS = List.of(
            "src/main/java",
            "src/test/java");

    @Test
    void jgitIsImportedOnlyInsideIoNopGit() throws IOException {
        Path parent = Path.of(System.getProperty("user.dir")).getParent();
        List<Path> moduleRoots = List.of(
                parent.resolve("nop-git-core"),
                parent.resolve("nop-git-cli"));

        List<String> violations = new ArrayList<>();
        int[] scanned = new int[1];
        for (Path moduleRoot : moduleRoots) {
            // only nop-git-core may touch JGit at all (design 01 section 1)
            boolean isCore = moduleRoot.getFileName().toString().equals("nop-git-core");
            for (String srcDir : MODULE_SRC_DIRS) {
                Path src = moduleRoot.resolve(srcDir);
                if (!Files.isDirectory(src))
                    continue;
                try (Stream<Path> files = Files.walk(src)) {
                    files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                        scanned[0]++;
                        String content = readQuietly(p);
                        boolean inNopGitPackage = p.toString().replace('\\', '/')
                                .contains("/io/nop/git/");
                        boolean forbidden = !isCore || !inNopGitPackage;
                        if (forbidden && content.contains("org.eclipse.jgit")) {
                            violations.add(p + " imports org.eclipse.jgit outside the core adapter");
                        }
                    });
                }
            }
        }

        // sanity: the scan must actually have seen the adapter
        assertTrue(scanned[0] > 0, "no sources scanned - module layout changed?");
        if (!violations.isEmpty()) {
            fail(String.join("\n", violations));
        }
    }

    private static String readQuietly(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            return "";
        }
    }
}
