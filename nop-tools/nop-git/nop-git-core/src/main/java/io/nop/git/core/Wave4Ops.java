package io.nop.git.core;

import io.nop.git.api.AddWorktreeOptions;
import io.nop.git.api.CleanOptions;
import io.nop.git.api.GitResetMode;
import io.nop.git.api.NopGitCommit;
import io.nop.git.api.NopGitException;
import io.nop.git.api.NopGitWorktree;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Implementation of the Wave 4 facade operations (P2 commands, worktree,
 * P3 local/network/history). Kept as a separate collaborator so
 * {@link JGitRepositoryService} stays the guarded entry: every call goes
 * through the single JGit touch-point class.
 */
final class Wave4Ops {

    private Wave4Ops() {
    }

    static void checkout(Repository repository, String branch, boolean create) {
        try (Git git = new Git(repository)) {
            if (create) {
                if (repository.resolve(branch) != null && branchExists(repository, branch))
                    throw new NopGitException("a branch named '" + branch + "' already exists");
                git.checkout().setCreateBranch(true).setName(branch)
                        .setStartPoint(Constants.HEAD).call();
            } else {
                if (repository.resolve(branch) == null)
                    throw new NopGitException("unknown revision: " + branch);
                git.checkout().setName(branch).call();
            }
        } catch (NopGitException e) {
            throw e;
        } catch (Exception e) {
            throw new NopGitException("checkout failed: " + e.getMessage(), e);
        }
    }

    static void reset(Repository repository, GitResetMode mode, String rev) {
        String target = rev == null ? Constants.HEAD : rev;
        try (Git git = new Git(repository)) {
            ObjectId id = repository.resolve(target);
            if (id == null)
                throw new NopGitException("ambiguous argument '" + target
                        + "': unknown revision or path not in the working tree.");
            ResetCommand.ResetType type = switch (mode) {
                case SOFT -> ResetCommand.ResetType.SOFT;
                case MIXED -> ResetCommand.ResetType.MIXED;
                case HARD -> ResetCommand.ResetType.HARD;
            };
            git.reset().setMode(type).setRef(id.getName()).call();
        } catch (NopGitException e) {
            throw e;
        } catch (Exception e) {
            throw new NopGitException("reset failed: " + e.getMessage(), e);
        }
    }

    static List<String> clean(Repository repository, CleanOptions options) {
        List<String> removed = new ArrayList<>();
        try (Git git = new Git(repository)) {
            var status = git.status().call();
            Set<String> candidates = new java.util.HashSet<>(status.getUntracked());
            var folders = status.getUntrackedFolders();
            if (options.directories()) {
                // native lists the collapsed folder, not the contained files
                folders.forEach(f -> candidates.add(f.endsWith("/") ? f : f + "/"));
                candidates.removeIf(path -> folders.stream()
                        .anyMatch(folder -> path.startsWith(folder + "/")));
            } else {
                // without -d, files inside fully-untracked directories are
                // invisible to clean
                candidates.removeIf(path -> folders.stream()
                        .anyMatch(folder -> path.startsWith(folder + "/")));
            }
            List<String> sorted = candidates.stream().sorted().toList();
            if (options.force() && !options.dryRun()) {
                org.eclipse.jgit.api.CleanCommand cmd = git.clean().setForce(true)
                        .setCleanDirectories(options.directories());
                Set<String> cleaned = cmd.call();
                removed.addAll(cleaned.stream().sorted().toList());
            } else {
                removed.addAll(sorted); // dry run: native "Would remove" listing
            }
        } catch (Exception e) {
            throw new NopGitException("clean failed: " + e.getMessage(), e);
        }
        return removed;
    }

    static void restore(Repository repository, String path) {
        try (Git git = new Git(repository)) {
            git.checkout().addPath(path).setAllPaths(false).call();
        } catch (Exception e) {
            throw new NopGitException("restore failed for '" + path + "': " + e.getMessage(), e);
        }
    }

    static void rm(Repository repository, String path, boolean cached, boolean force) {
        try (Git git = new Git(repository)) {
            git.rm().addFilepattern(path).setCached(cached).call();
        } catch (Exception e) {
            throw new NopGitException("rm failed for '" + path + "': " + e.getMessage(), e);
        }
    }

    static void mv(Repository repository, String oldPath, String newPath) {
        try (Git git = new Git(repository)) {
            // JGit has no move command: relocate the file, then update the
            // index like native git mv does (add new + drop old)
            Files.move(repository.getWorkTree().toPath().resolve(oldPath),
                    repository.getWorkTree().toPath().resolve(newPath));
            git.add().addFilepattern(newPath).call();
            git.rm().addFilepattern(oldPath).setCached(true).call();
        } catch (Exception e) {
            throw new NopGitException("mv failed: " + e.getMessage(), e);
        }
    }

    static String stashPush(Repository repository) {
        try (Git git = new Git(repository)) {
            if (git.status().call().isClean())
                return null; // native: "No local changes to save"
            org.eclipse.jgit.revwalk.RevCommit stash = git.stashCreate().call();
            if (stash == null)
                return null;
            return stash.getName();
        } catch (Exception e) {
            throw new NopGitException("stash failed: " + e.getMessage(), e);
        }
    }

    static List<String> stashList(Repository repository) {
        try (Git git = new Git(repository)) {
            List<String> lines = new ArrayList<>();
            for (org.eclipse.jgit.revwalk.RevCommit commit : git.stashList().call()) {
                // the stash commit message already is the native line body
                // ("WIP on <branch>: <sha> <subject>") — do not re-prefix it
                lines.add("stash@{" + lines.size() + "}: " + commit.getShortMessage());
            }
            return lines;
        } catch (Exception e) {
            throw new NopGitException("stash list failed: " + e.getMessage(), e);
        }
    }

    static void stashPop(Repository repository) {
        try (Git git = new Git(repository)) {
            if (!git.stashList().call().iterator().hasNext())
                throw new NopGitException("No stash entries found.");
            git.stashApply().call();
            // drop the applied stash (native pop semantics)
            git.stashDrop().setStashRef(0).call();
        } catch (NopGitException e) {
            throw e;
        } catch (Exception e) {
            throw new NopGitException("stash pop failed: " + e.getMessage(), e);
        }
    }

    static List<String> remoteList(Repository repository) {
        return repository.getRemoteNames().stream().sorted().toList();
    }

    static List<String> remoteListVerbose(Repository repository) {
        var config = repository.getConfig();
        List<String> lines = new ArrayList<>();
        for (String name : repository.getRemoteNames().stream().sorted().toList()) {
            List<String> urls = List.of(config.getStringList("remote", name, "url"));
            for (String url : urls) {
                lines.add(name + "\t" + url + " (fetch)");
                lines.add(name + "\t" + url + " (push)");
            }
        }
        return lines;
    }

    static void remoteAdd(Repository repository, String name, String url) {
        if (repository.getConfig().getString("remote", name, "url") != null)
            throw new NopGitException("remote " + name + " already exists.");
        try {
            repository.getConfig().setString("remote", name, "url", url);
            String fetch = "+refs/heads/*:refs/remotes/" + name + "/*";
            repository.getConfig().setString("remote", name, "fetch", fetch);
            repository.getConfig().save();
        } catch (IOException e) {
            throw new NopGitException("cannot write remote config: " + e.getMessage(), e);
        }
    }

    static void remoteSetUrl(Repository repository, String name, String url) {
        if (repository.getConfig().getString("remote", name, "url") == null)
            throw new NopGitException("No such remote: '" + name + "'");
        try {
            repository.getConfig().setString("remote", name, "url", url);
            repository.getConfig().save();
        } catch (IOException e) {
            throw new NopGitException("cannot write remote config: " + e.getMessage(), e);
        }
    }

    static void remoteRemove(Repository repository, String name) {
        if (repository.getConfig().getString("remote", name, "url") == null)
            throw new NopGitException("No such remote: '" + name + "'");
        try {
            repository.getConfig().unsetSection("remote", name);
            repository.getConfig().save();
        } catch (IOException e) {
            throw new NopGitException("cannot write remote config: " + e.getMessage(), e);
        }
    }

    static void push(Repository repository, String remote, String refspec, boolean setUpstream) {
        try (Git git = new Git(repository)) {
            final org.eclipse.jgit.api.PushCommand cmd0 = git.push().setRemote(remote);
            org.eclipse.jgit.api.PushCommand cmd = cmd0;
            if (refspec != null && !refspec.isEmpty()) {
                // "branch" shorthand means branch:branch
                if (!refspec.contains(":") && !refspec.startsWith("refs/"))
                    cmd = cmd.add(refspec + ":" + refspec);
                else
                    cmd = cmd.add(refspec);
            }
            if (setUpstream) {
                // plan 2582: no refspec means the current branch ("push -u
                // origin" is a legal operation, never an NPE)
                String branch = refspec == null || refspec.isEmpty()
                        ? currentBranch(repository) : refspec.split(":")[0];
                // upstream mapping recorded below (JGit PushCommand has no setSetUpstream)
                // remember upstream mapping for pull convenience
                if (refspec == null || refspec.isEmpty() || !refspec.contains(":")) {
                    repository.getConfig().setString("branch", branch, "remote", remote);
                    repository.getConfig().setString("branch", branch, "merge",
                            "refs/heads/" + branch);
                    repository.getConfig().save();
                }
            }
            for (org.eclipse.jgit.transport.PushResult result : cmd.call()) {
                for (org.eclipse.jgit.transport.RemoteRefUpdate update : result.getRemoteUpdates()) {
                    var status = update.getStatus();
                    if (status == org.eclipse.jgit.transport.RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD
                            || status == org.eclipse.jgit.transport.RemoteRefUpdate.Status.REJECTED_NODELETE
                            || status == org.eclipse.jgit.transport.RemoteRefUpdate.Status.REJECTED_REMOTE_CHANGED
                            || status == org.eclipse.jgit.transport.RemoteRefUpdate.Status.REJECTED_OTHER_REASON) {
                        throw new NopGitException("failed to push some refs to '" + remote
                                + "': " + update.getMessage());
                    }
                }
            }
        } catch (NopGitException e) {
            throw e;
        } catch (Exception e) {
            throw new NopGitException("push failed: " + e.getMessage(), e);
        }
    }

    static void fetch(Repository repository, String remote) {
        try (Git git = new Git(repository)) {
            git.fetch().setRemote(remote).call();
        } catch (Exception e) {
            throw new NopGitException("fetch failed: " + e.getMessage(), e);
        }
    }

    static void pull(Repository repository, String remote, String branch) {
        try (Git git = new Git(repository)) {
            fetch(repository, remote);
            String mergeBranch = branch != null ? branch : currentBranch(repository);
            String remoteRef = "refs/remotes/" + remote + "/" + mergeBranch;
            ObjectId remoteTip = repository.resolve(remoteRef);
            if (remoteTip == null)
                throw new NopGitException("cannot pull: remote branch '" + mergeBranch
                        + "' not found on '" + remote + "'");
            mergeIntoHead(repository, remoteTip.getName(), false);
        } catch (NopGitException e) {
            throw e;
        } catch (Exception e) {
            throw new NopGitException("pull failed: " + e.getMessage(), e);
        }
    }

    static void merge(Repository repository, String ref, boolean noFf) {
        try {
            ObjectId id = repository.resolve(ref);
            if (id == null)
                throw new NopGitException("merge: " + ref
                        + " - not something we can merge");
            mergeIntoHead(repository, id.getName(), noFf);
        } catch (NopGitException e) {
            throw e;
        } catch (Exception e) {
            throw new NopGitException("merge failed: " + e.getMessage(), e);
        }
    }

    private static void mergeIntoHead(Repository repository, String commit, boolean noFf) {
        try (Git git = new Git(repository)) {
            MergeResult result = git.merge()
                    .include(ObjectId.fromString(commit))
                    .setFastForward(noFf
                            ? MergeCommand.FastForwardMode.NO_FF
                            : MergeCommand.FastForwardMode.FF)
                    .setStrategy(MergeStrategy.RESOLVE)
                    .call();
            if (result.getMergeStatus().isSuccessful())
                return;
            if (result.getMergeStatus() == MergeResult.MergeStatus.CONFLICTING) {
                throw new NopGitException("Automatic merge failed; fix conflicts and then "
                        + "commit the result.");
            }
            throw new NopGitException("merge failed: " + result.getMergeStatus());
        } catch (NopGitException e) {
            throw e;
        } catch (Exception e) {
            throw new NopGitException("merge failed: " + e.getMessage(), e);
        }
    }

    static void rebase(Repository repository, String upstream) {
        try (Git git = new Git(repository)) {
            ObjectId id = repository.resolve(upstream);
            if (id == null)
                throw new NopGitException("unknown revision: " + upstream);
            var result = git.rebase().setUpstream(id.getName()).call();
            if (!result.getStatus().isSuccessful()) {
                throw new NopGitException("rebase failed: " + result.getStatus());
            }
        } catch (NopGitException e) {
            throw e;
        } catch (Exception e) {
            throw new NopGitException("rebase failed: " + e.getMessage(), e);
        }
    }

    static NopGitCommit cherryPick(Repository repository, String commit, Consumer<NopGitCommit> unused) {
        try (Git git = new Git(repository)) {
            ObjectId id = repository.resolve(commit);
            if (id == null)
                throw new NopGitException("bad revision '" + commit + "'");
            org.eclipse.jgit.api.CherryPickResult result = git.cherryPick().include(id).call();
            if (result.getStatus() == org.eclipse.jgit.api.CherryPickResult.CherryPickStatus.CONFLICTING) {
                throw new NopGitException("cherry-pick resulted in conflicts; fix and commit");
            }
            org.eclipse.jgit.revwalk.RevCommit newHead = result.getNewHead();
            if (newHead == null)
                return null;
            return new NopGitCommit(newHead.getName(),
                    newHead.abbreviate(7).name(),
                    newHead.getAuthorIdent().getName(),
                    newHead.getAuthorIdent().getEmailAddress(),
                    java.time.OffsetDateTime.ofInstant(
                            java.time.Instant.ofEpochSecond(newHead.getCommitTime()),
                            java.time.ZoneOffset.ofTotalSeconds(
                                    newHead.getAuthorIdent().getTimeZoneOffset() * 60)),
                    newHead.getShortMessage(),
                    newHead.getFullMessage(),
                    List.of());
        } catch (NopGitException e) {
            throw e;
        } catch (Exception e) {
            throw new NopGitException("cherry-pick failed: " + e.getMessage(), e);
        }
    }

    static void cloneRepository(File from, File to) {
        try (Git git = Git.cloneRepository().setURI(from.toURI().toString())
                .setDirectory(to).call()) {
            // try-with-resources closes the repository
        } catch (Exception e) {
            throw new NopGitException("clone failed: " + e.getMessage(), e);
        }
    }

    private static String currentBranch(Repository repository) {
        try {
            return repository.getBranch();
        } catch (IOException e) {
            throw new NopGitException("cannot determine current branch", e);
        }
    }

    static String currentBranchOrNull(Repository repository) {
        try {
            return repository.getBranch();
        } catch (IOException e) {
            return null;
        }
    }

    static boolean branchExists(Repository repository, String name) {
        try {
            return repository.getRefDatabase().getRefsByPrefix(Constants.R_HEADS).stream()
                    .anyMatch(r -> Repository.shortenRefName(r.getName()).equals(name));
        } catch (IOException e) {
            return false;
        }
    }

    static boolean hasRef(Repository repository, String name) {
        try {
            return repository.resolve(name) != null;
        } catch (IOException e) {
            return false;
        }
    }
}
