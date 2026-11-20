package io.nop.git.policy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared builder for normalized {@link IGitOperationRequest}s (design 03
 * section 3: normalization happens before evaluation; a single implementation
 * serves both the CLI and the API facade so the alias table cannot drift).
 */
public final class GitRequests {

    private GitRequests() {
    }

    public static IGitOperationRequest of(String operation, String repoPath, String rawCommand) {
        return builder(operation, repoPath, rawCommand).build();
    }

    public static Builder builder(String operation, String repoPath, String rawCommand) {
        return new Builder(operation, repoPath, rawCommand);
    }

    public static final class Builder {
        private final String operation;
        private final String repoPath;
        private final String rawCommand;
        private final List<String> flags = new ArrayList<>();
        private final List<String> paths = new ArrayList<>();
        private String targetBranch;
        private String remote;
        private String remoteUrl;

        Builder(String operation, String repoPath, String rawCommand) {
            this.operation = operation;
            this.repoPath = repoPath;
            this.rawCommand = rawCommand;
        }

        /**
         * Add a raw flag; normalized to its canonical long form per the
         * design 03 alias table (command-dependent for ambiguous shorts).
         */
        public Builder flag(String raw) {
            flags.addAll(normalizeFlag(operation, raw));
            return this;
        }

        /**
         * Add an already-canonical flag.
         */
        public Builder canonicalFlag(String flag) {
            flags.add(flag);
            return this;
        }

        /**
         * Add a user-supplied path; canonicalized (absolute + normalized,
         * relative forms resolved against the repository root) and stored
         * repo-relative so path-rule globs match the rule author's view.
         */
        public Builder path(String rawPath) {
            paths.add(canonicalize(rawPath, repoPath));
            return this;
        }

        public Builder targetBranch(String branch) {
            this.targetBranch = branch;
            return this;
        }

        public Builder remote(String name, String url) {
            this.remote = name;
            this.remoteUrl = url;
            return this;
        }

        public IGitOperationRequest build() {
            var flagsCopy = List.copyOf(flags);
            var pathsCopy = List.copyOf(paths);
            String branchCopy = targetBranch;
            String remoteCopy = remote;
            String remoteUrlCopy = remoteUrl;
            return new IGitOperationRequest() {
                @Override
                public String operation() {
                    return operation;
                }

                @Override
                public List<String> flags() {
                    return flagsCopy;
                }

                @Override
                public List<String> paths() {
                    return pathsCopy;
                }

                @Override
                public String targetBranch() {
                    return branchCopy;
                }

                @Override
                public String remote() {
                    return remoteCopy;
                }

                @Override
                public String remoteUrl() {
                    return remoteUrlCopy;
                }

                @Override
                public String repoPath() {
                    return repoPath;
                }

                @Override
                public String rawCommand() {
                    return rawCommand;
                }
            };
        }
    }

    /**
     * Canonical absolute path of a file/directory (symlinks resolved).
     */
    public static String canonicalPath(File file) {
        try {
            return file.toPath().toRealPath().toString();
        } catch (IOException e) {
            return file.getAbsolutePath();
        }
    }

    /**
     * Canonicalize a path for rule matching: absolute, normalized (resolves
     * {@code ..} segments); symlinks are not resolved because rule authors
     * write repository-relative globs against the logical path.
     */
    static String canonicalize(String rawPath, String baseDir) {
        if (rawPath == null || rawPath.isEmpty())
            return rawPath;
        Path base = Path.of(baseDir == null || baseDir.isEmpty()
                ? System.getProperty("user.dir") : baseDir);
        Path path = Path.of(rawPath);
        // resolve + normalize kills ../ traversal before matching
        Path absolute = base.resolve(path).normalize();
        // store repo-relative when the path lives under the repository
        if (absolute.startsWith(base))
            return base.relativize(absolute).toString();
        return absolute.toString();
    }

    /**
     * Design 03 alias table. Ambiguous shorts resolve per command: {@code -d}
     * means {@code --delete} for branch/push but {@code --directories} for
     * clean; combined shorts are expanded letter by letter.
     *
     * @return the canonical flags the raw form stands for (possibly several)
     */
    static List<String> normalizeFlag(String operation, String raw) {
        // combined short flags (-fd) expand first
        if (raw.matches("-[a-zA-Z]{2,}")) {
            var result = new ArrayList<String>();
            for (char c : raw.substring(1).toCharArray()) {
                result.addAll(normalizeFlag(operation, "-" + c));
            }
            return result;
        }

        // --force-with-lease[=<ref>] folds to the bare form
        if (raw.startsWith("--force-with-lease")) {
            return List.of("--force-with-lease");
        }

        return switch (raw) {
            case "-f", "--force" -> List.of("--force");
            case "-D" -> {
                if ("branch".equals(operation))
                    yield List.of("--delete", "--force");
                yield List.of(raw);
            }
            case "-d" -> {
                if ("branch".equals(operation) || "push".equals(operation))
                    yield List.of("--delete");
                if ("clean".equals(operation))
                    yield List.of("--directories");
                yield List.of(raw);
            }
            default -> List.of(raw);
        };
    }
}
