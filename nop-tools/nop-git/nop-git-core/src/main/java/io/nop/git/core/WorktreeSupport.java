package io.nop.git.core;

import io.nop.git.api.AddWorktreeOptions;
import io.nop.git.api.NopGitException;
import io.nop.git.api.NopGitWorktree;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Linked-worktree add/remove/list built directly on the git official
 * metadata format (design 05-worktree-design): JGit 7.x only reads linked
 * worktrees, so add/remove write {@code $GIT_COMMON_DIR/worktrees/<id>/}
 * (gitdir/commondir/HEAD) plus the working-directory {@code .git} pointer
 * file, then materialize index + files by opening the self-built worktree
 * with JGit and running {@code reset --hard} (validated by the plan 2579
 * spike and the bidirectional compatibility tests against real git).
 */
final class WorktreeSupport {

    private WorktreeSupport() {
    }

    static Path adminDir(Repository repository) {
        // common dir: .git/worktrees for a normal repository layout
        File gitDir = repository.getDirectory();
        if (gitDir == null)
            throw new NopGitException("cannot determine git common directory (bare?)");
        return gitDir.toPath().resolve("worktrees");
    }

    static List<NopGitWorktree> list(Repository repository) {
        try {
            return doList(repository);
        } catch (IOException e) {
            throw new NopGitException("cannot list worktrees: " + e.getMessage(), e);
        }
    }

    private static List<NopGitWorktree> doList(Repository repository) throws IOException {
        List<NopGitWorktree> result = new ArrayList<>();
        // main worktree first (native porcelain order)
        result.add(mainWorktreeEntry(repository));
        result.addAll(listEntries(repository));
        return result;
    }

    private static List<NopGitWorktree> listEntries(Repository repository) throws IOException {
        List<NopGitWorktree> result = new ArrayList<>();
        Path admin = adminDir(repository);
        if (!Files.isDirectory(admin))
            return result;
        try (Stream<Path> entries = Files.list(admin)) {
            List<Path> ids = entries
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
            for (Path idDir : ids) {
                NopGitWorktree entry = readEntry(repository, idDir);
                if (entry != null)
                    result.add(entry);
            }
        }
        return result;
    }

    private static NopGitWorktree mainWorktreeEntry(Repository repository) throws IOException {
        String path = realPath(repository.getWorkTree().toPath());
        ObjectId head = repository.resolve(Constants.HEAD);
        String headSha = head == null ? null : head.name();
        String branch = currentBranchOrNull(repository);
        boolean detached = branch == null;
        return new NopGitWorktree(path, headSha, detached ? null : branch,
                false, detached, false, null, false, null);
    }

    private static NopGitWorktree readEntry(Repository repository, Path idDir) throws IOException {
        try {
            Path gitdirFile = idDir.resolve("gitdir");
            if (!Files.isRegularFile(gitdirFile))
                return null; // not a worktree admin entry
            String worktreeGitFile = Files.readString(gitdirFile, StandardCharsets.UTF_8).trim();
            Path worktreePath = Paths.get(worktreeGitFile).getParent();
            if (worktreePath == null)
                return null;

            boolean prunable = !Files.isDirectory(worktreePath);
            String prunableReason = prunable ? "gitdir file points to non-existent location" : null;

            String head = null;
            String branch = null;
            boolean detached = false;
            Path headFile = idDir.resolve("HEAD");
            if (Files.isRegularFile(headFile)) {
                String headLine = Files.readString(headFile, StandardCharsets.UTF_8).trim();
                if (headLine.startsWith("ref: ")) {
                    branch = Repository.shortenRefName(headLine.substring(5).trim());
                    ObjectId sha = repository.resolve(headLine.substring(5).trim());
                    head = sha == null ? null : sha.name();
                } else {
                    detached = true;
                    head = headLine;
                }
            }

            boolean locked = Files.exists(idDir.resolve("locked"));
            String lockedReason = "";
            if (locked) {
                lockedReason = Files.readString(idDir.resolve("locked"),
                        StandardCharsets.UTF_8).trim();
            }

            return new NopGitWorktree(realPath(worktreePath), head, branch,
                    false, detached, locked, lockedReason, prunable, prunableReason);
        } catch (IOException e) {
            throw new NopGitException("cannot read worktree entry " + idDir
                    + ": " + e.getMessage(), e);
        }
    }

    static NopGitWorktree add(Repository repository, AddWorktreeOptions options) {
        try {
            Path target = options.path().toAbsolutePath();
            if (Files.exists(target) && Files.list(target).findAny().isPresent())
                throw new NopGitException("'" + target + "' already exists and is not empty");

            String branch = options.newBranch();
            String commitish = options.commitish();
            ObjectId commitId;
            try (Git git = new Git(repository)) {
                if (branch != null) {
                    String start = commitish != null ? commitish : Constants.HEAD;
                    ObjectId startId = repository.resolve(start);
                    if (startId == null)
                        throw new NopGitException("unknown revision: " + start);
                    git.branchCreate().setName(branch).setStartPoint(startId.getName())
                            .setForce(false).call();
                    commitId = startId;
                } else if (commitish != null) {
                    ObjectId id = repository.resolve(commitish);
                    if (id == null)
                        throw new NopGitException("unknown revision: " + commitish);
                    commitId = id;
                } else {
                    ObjectId head = repository.resolve(Constants.HEAD);
                    if (head == null)
                        throw new NopGitException("repository has no commit yet");
                    commitId = head;
                }
            }

            // admin dir id: sanitized directory name with -N suffix on conflict
            String id = sanitizeId(target);
            Path admin = adminDir(repository).resolve(id);
            int suffix = 1;
            while (Files.exists(admin)) {
                admin = adminDir(repository).resolve(id + "-" + suffix++);
            }
            Files.createDirectories(admin);

            String checkoutBranch = branch;
            if (checkoutBranch == null && !options.detach()
                    && commitish != null && branchNameExists(repository, commitish)) {
                checkoutBranch = Repository.shortenRefName(commitish);
            }
            String headLine = options.detach()
                    ? commitId.name()
                    : "ref: refs/heads/" + (checkoutBranch != null ? checkoutBranch
                    : currentBranchOrMaster(repository));
            // create the working directory first so gitdir records the
            // real path (symlink-resolved), like native git
            Files.createDirectories(target);
            Files.writeString(admin.resolve("gitdir"),
                    realPath(target) + "/.git\n", StandardCharsets.UTF_8);
            Files.writeString(admin.resolve("commondir"), "../..\n", StandardCharsets.UTF_8);
            Files.writeString(admin.resolve("HEAD"), headLine + "\n", StandardCharsets.UTF_8);
            Files.writeString(target.resolve(".git"),
                    "gitdir: " + admin.toRealPath() + "\n", StandardCharsets.UTF_8);

            // materialize index + working files from HEAD of the self-built
            // worktree (JGit reads the .git pointer + commondir; plan 2579 spike)
            try (Git worktreeGit = Git.open(target.toFile())) {
                worktreeGit.reset().setMode(ResetCommand.ResetType.HARD).call();
            }

            return new NopGitWorktree(realPath(target), commitId.name(),
                    options.detach() ? null : readBranchOfAdmin(admin),
                    false, options.detach(), false, null, false, null);
        } catch (IOException | org.eclipse.jgit.api.errors.GitAPIException e) {
            throw new NopGitException("cannot add worktree: " + e.getMessage(), e);
        }
    }

    private static String readBranchOfAdmin(Path admin) {
        try {
            String line = Files.readString(admin.resolve("HEAD"), StandardCharsets.UTF_8).trim();
            return line.startsWith("ref: ")
                    ? Repository.shortenRefName(line.substring(5).trim()) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static String currentBranchOrMaster(Repository repository) {
        String branch = currentBranchOrNull(repository);
        return branch != null ? branch : "master";
    }

    private static boolean branchNameExists(Repository repository, String name) {
        if (name == null)
            return false;
        try {
            return repository.getRefDatabase().getRefsByPrefix(Constants.R_HEADS).stream()
                    .anyMatch(r -> Repository.shortenRefName(r.getName()).equals(name));
        } catch (IOException e) {
            return false;
        }
    }

    static void remove(Repository repository, String path, boolean force) {
        Path admin = adminDir(repository);
        Path target = Paths.get(path).toAbsolutePath().normalize();
        boolean found = false;
        try (Stream<Path> entries = Files.list(admin)) {
            for (Path idDir : entries.toList()) {
                Path gitdirFile = idDir.resolve("gitdir");
                if (!Files.isRegularFile(gitdirFile))
                    continue;
                String pointer = Files.readString(gitdirFile, StandardCharsets.UTF_8).trim();
                Path worktreeGit = Paths.get(pointer);
                // compare real paths: the registry stores symlink-resolved
                // paths while callers may pass symlinked ones
                if (!realPath(worktreeGit.getParent()).equals(realPath(target)))
                    continue;
                found = true;

                // safety: the working directory's .git must point back at this
                // admin dir, otherwise the gitdir file was tampered with and we
                // refuse to delete the directory tree
                Path backPointer = target.resolve(".git");
                if (!Files.isRegularFile(backPointer))
                    throw new NopGitException("cannot remove worktree: missing .git pointer in "
                            + target);
                String back = Files.readString(backPointer, StandardCharsets.UTF_8).trim();
                if (!back.equals("gitdir: " + idDir.toRealPath()))
                    throw new NopGitException("cannot remove worktree: .git pointer of " + target
                            + " does not match the worktree registry");

                if (Files.exists(idDir.resolve("locked")) && !force)
                    throw new NopGitException("cannot remove locked worktree '" + target
                            + "' (use --force)");

                deleteRecursively(target);
                deleteRecursively(idDir);
            }
        } catch (IOException e) {
            throw new NopGitException("cannot remove worktree: " + e.getMessage(), e);
        }
        if (!found)
            throw new NopGitException("'" + path + "' is not a working tree");
    }

    static int prune(Repository repository) {
        int pruned = 0;
        Path admin = adminDir(repository);
        if (!Files.isDirectory(admin))
            return 0;
        try (Stream<Path> entries = Files.list(admin)) {
            for (Path idDir : entries.toList()) {
                Path gitdirFile = idDir.resolve("gitdir");
                if (!Files.isRegularFile(gitdirFile))
                    continue;
                String pointer = Files.readString(gitdirFile, StandardCharsets.UTF_8).trim();
                Path worktreePath = Paths.get(pointer).getParent();
                if (worktreePath != null && !Files.isDirectory(worktreePath)) {
                    deleteRecursively(idDir);
                    pruned++;
                }
            }
        } catch (IOException e) {
            throw new NopGitException("cannot prune worktrees: " + e.getMessage(), e);
        }
        return pruned;
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root))
            return;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                    throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static String realPath(Path path) {
        try {
            return path.toRealPath().toString();
        } catch (IOException e) {
            return path.toAbsolutePath().toString();
        }
    }

    private static String currentBranchOrNull(Repository repository) {
        try {
            if (repository.resolve(Constants.HEAD) == null)
                return null;
            return repository.getBranch();
        } catch (IOException e) {
            return null;
        }
    }

    private static String sanitizeId(Path target) {
        String name = target.getFileName().toString();
        String sanitized = name.replaceAll("[^\\w.-]", "-");
        return sanitized.isEmpty() ? "worktree" : sanitized;
    }
}
