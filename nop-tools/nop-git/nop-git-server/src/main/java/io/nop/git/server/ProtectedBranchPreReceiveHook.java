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
            if (!ref.startsWith(Constants.R_HEADS))
                continue;
            String branch = ref.substring(Constants.R_HEADS.length());
            if (!isProtected(branch))
                continue;
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
