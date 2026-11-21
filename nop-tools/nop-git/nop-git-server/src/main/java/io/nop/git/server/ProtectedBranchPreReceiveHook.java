package io.nop.git.server;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PreReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceiveCommand.Result;
import org.eclipse.jgit.transport.ReceivePack;

import java.util.Collection;
import java.io.IOException;
import java.util.List;

/**
 * Server-side push protection (design 04 section 4): the CLI/API policy
 * engine protects local AI operations; the HTTP endpoint is protected by this
 * hook, which rejects (1) direct pushes to protected branches, (2) non-fast
 * forward (force) updates and (3) deletions of protected branches.
 */
public class ProtectedBranchPreReceiveHook implements PreReceiveHook {

    /**
     * Default protected branch patterns (aligned with the nop-git policy
     * engine builtin defaults).
     */
    public static final List<String> DEFAULT_PROTECTED = List.of("main", "master", "release/*");

    private final List<String> patterns;

    public ProtectedBranchPreReceiveHook() {
        this(DEFAULT_PROTECTED);
    }

    public ProtectedBranchPreReceiveHook(List<String> patterns) {
        this.patterns = patterns;
    }

    @Override
    public void onPreReceive(ReceivePack receivePack, Collection<ReceiveCommand> commands) {
        for (ReceiveCommand command : commands) {
            String ref = command.getRefName();
            if (ref.startsWith(Constants.R_HEADS)) {
                guardBranch(receivePack, command, ref,
                        ref.substring(Constants.R_HEADS.length()));
            } else if (ref.startsWith(Constants.R_TAGS)) {
                // plan 2582: non-heads refs are protected too (delete tags etc.)
                guardTag(command, ref, ref.substring(Constants.R_TAGS.length()));
            }
            // other namespaces (refs/notes, custom refs) stay unregulated
        }
    }

    private void guardBranch(ReceivePack receivePack, ReceiveCommand command,
                             String ref, String branch) {
        if (!isProtected(branch))
            return;
        switch (command.getType()) {
            case DELETE -> reject(command, "deletion of protected branch '" + branch
                    + "' is not allowed");
            case UPDATE_NONFASTFORWARD -> reject(command, "force push to protected branch '"
                    + branch + "' is not allowed");
            case UPDATE -> {
                // allow fast-forward updates only when the branch is new;
                // direct pushes to existing protected branches are denied
                if (branchHasCommits(receivePack.getRepository(), ref)) {
                    reject(command, "direct push to protected branch '" + branch
                            + "' is not allowed");
                }
            }
            default -> {
            }
        }
    }

    /**
     * Plan 2582: protected tag names reject deletion outright; a forced
     * (non-fast-forward) rewrite of an existing protected tag is rejected as
     * well. Creation and fast-forward updates pass.
     */
    private void guardTag(ReceiveCommand command, String ref, String tag) {
        if (!isProtected(tag))
            return;
        switch (command.getType()) {
            case DELETE -> reject(command, "deletion of protected tag '" + tag
                    + "' is not allowed");
            case UPDATE_NONFASTFORWARD -> reject(command, "rewrite of protected tag '"
                    + tag + "' is not allowed");
            default -> {
            }
        }
    }

    private void reject(ReceiveCommand command, String message) {
        command.setResult(Result.REJECTED_OTHER_REASON, message);
    }

    private boolean branchHasCommits(Repository repository, String ref) {
        try {
            return repository.resolve(ref) != null;
        } catch (IOException e) {
            return true; // fail closed when the ref cannot be inspected
        }
    }

    private boolean isProtected(String branch) {
        for (String pattern : patterns) {
            if (pattern.endsWith("/*")) {
                if (branch.startsWith(pattern.substring(0, pattern.length() - 1)))
                    return true;
            } else if (pattern.equals(branch)) {
                return true;
            }
        }
        return false;
    }
}
