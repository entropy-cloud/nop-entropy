package io.nop.git.api;

import java.time.Instant;
import java.util.List;

/**
 * A single commit, carrying every field the CLI formatter needs (the CLI and
 * the API share one data path; formatting only forks at the output boundary).
 *
 * @param id            full commit SHA-1
 * @param abbrevId      abbreviated commit SHA-1 (at least 7 hex chars)
 * @param authorName    author name
 * @param authorEmail   author email
 * @param commitTime    author/commit time
 * @param shortMessage  first line of the commit message
 * @param fullMessage   complete commit message
 * @param parentIds     full SHAs of parent commits (empty for the root commit)
 */
public record NopGitCommit(
        String id,
        String abbrevId,
        String authorName,
        String authorEmail,
        Instant commitTime,
        String shortMessage,
        String fullMessage,
        List<String> parentIds) {
}
