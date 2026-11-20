package io.nop.git.cli;

import io.nop.git.api.IGitRepositoryService;

import java.nio.file.Path;

/**
 * Fixture helper for policy tests: repository init via the facade (no JGit
 * import needed; the io.nop.git.cli package may import JGit, but the facade
 * is enough here).
 */
final class IGitRepositorySmokeHelper {

    private IGitRepositorySmokeHelper() {
    }

    static void initRepo(Path dir) {
        IGitRepositoryService.init(dir.toFile(), "master").close();
    }
}
