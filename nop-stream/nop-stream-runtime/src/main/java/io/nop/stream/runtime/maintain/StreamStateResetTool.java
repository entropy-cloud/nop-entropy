/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.maintain;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.CoordinatorInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;

/**
 * Item 16 (P-REQ-10): job state reset tool. Clears the job's local checkpoint
 * state (the {@code LocalFileCheckpointStorage} directory layout
 * {@code <base>/<jobId>/} — durable checkpoints, epoch manifests and the
 * source cursors they carry, so the input position resets WITH the state) so
 * the job can be re-run from scratch with the same jobId.
 *
 * <p>Fail-fast preconditions (no silent wipe):
 * <ul>
 *   <li>blank jobId / missing state directory → explicit error (a typo must
 *       not "succeed" silently);</li>
 *   <li>an active coordinator for the job in the cluster registry → explicit
 *       error (stop the job first);</li>
 *   <li>caller-declared non-replayable source → explicit error (a reset makes
 *       the job re-read from the start; with a non-replayable source that
 *       would silently lose data — the reset is refused, not
 *       best-effort-cleared).</li>
 * </ul>
 */
public class StreamStateResetTool {

    private static final Logger LOG = LoggerFactory.getLogger(StreamStateResetTool.class);

    /**
     * F-08 (plan 2026-09-04-1326-3): the SAME safe-id discipline as the sister class
     * {@code LocalFileCheckpointStorage} — a reset is a destructive recursive delete, so
     * the jobId must match the storage-side id charset before it is ever joined onto
     * the base directory (a typo like {@code ../other-job} must fail typed, never
     * silently delete a SIBLING job's entire state).
     */
    private static final Pattern SAFE_ID_PATTERN = Pattern.compile("[a-zA-Z0-9_-]+");

    /** Result of a successful reset (repo-observable facts for the runbook/e2e). */
    public static final class ResetResult {
        private final String jobId;
        private final Path deletedPath;
        private final int deletedCheckpoints;

        ResetResult(String jobId, Path deletedPath, int deletedCheckpoints) {
            this.jobId = jobId;
            this.deletedPath = deletedPath;
            this.deletedCheckpoints = deletedCheckpoints;
        }

        public String getJobId() {
            return jobId;
        }

        public Path getDeletedPath() {
            return deletedPath;
        }

        public int getDeletedCheckpoints() {
            return deletedCheckpoints;
        }
    }

    /**
     * Resets the job's local state.
     *
     * @param jobId             the job identity (checkpoint storage key)
     * @param checkpointBaseDir the LocalFileCheckpointStorage base directory
     * @param sourceReplayable  MUST be true — a reset re-reads the source from
     *                          its start; declaring a non-replayable source
     *                          fails fast instead of wiping state that cannot
     *                          be replayed
     * @param clusterRegistry   optional registry consulted for an active
     *                          coordinator (running jobs refuse reset)
     */
    public static ResetResult reset(String jobId, String checkpointBaseDir,
                                    boolean sourceReplayable, ClusterRegistry clusterRegistry) {
        if (jobId == null || jobId.isBlank()) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "jobId is required for state reset");
        }
        // F-08: storage-side two-stage validation BEFORE the destructive delete:
        // (1) charset guard — legal storage jobIds are [a-zA-Z0-9_-]+ by construction
        // (LocalFileCheckpointStorage writes them under the same pattern), so anything
        // else can never be THIS caller's own state but CAN resolve onto a sibling
        // path (../other-job, encoded variants, absolute paths);
        if (!SAFE_ID_PATTERN.matcher(jobId).matches()) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ARG_NAME, "jobId")
                    .param(ARG_DETAIL, "must match [a-zA-Z0-9_-]+ for state reset, got: " + jobId);
        }
        if (checkpointBaseDir == null || checkpointBaseDir.isBlank()) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "checkpointBaseDir is required for state reset");
        }
        if (!sourceReplayable) {
            throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL, "Refusing to reset job '" + jobId
                    + "': the source is declared NON-REPLAYABLE — after a reset the job "
                    + "re-reads from its start, which would silently lose the "
                    + "non-replayable input position. Reset is only valid for "
                    + "replayable sources (file/replayable-CDC/collection sources).");
        }
        if (clusterRegistry != null) {
            CoordinatorInfo active = clusterRegistry.getActiveCoordinator(jobId);
            if (active != null) {
                throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL, "Refusing to reset job '" + jobId
                        + "': an active coordinator is registered (coordinatorId="
                        + active.getCoordinatorId() + ", fencingEpoch=" + active.getFencingEpoch()
                        + "). Stop the job before resetting its state.");
            }
        }

        // F-08: (2) canonical containment — even a pattern-passing input is checked
        // against the canonicalized base dir (defense in depth, mirroring
        // LocalFileCheckpointStorage.validatePath): the resolved job directory must
        // stay under the declared checkpoint base directory.
        Path baseCanonical = Path.of(checkpointBaseDir).toAbsolutePath().normalize();
        Path jobDir = Path.of(checkpointBaseDir, jobId).toAbsolutePath().normalize();
        if (!jobDir.startsWith(baseCanonical)) {
            throw new StreamException(ERR_STREAM_INVALID_STATE)
                    .param(ARG_DETAIL, "Path traversal detected: " + jobId + " resolves to "
                            + jobDir + " which is outside checkpointBaseDir " + baseCanonical);
        }
        if (!Files.exists(jobDir)) {
            throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL, "Refusing to reset job '" + jobId
                    + "': no local state directory exists at " + jobDir.toAbsolutePath()
                    + " (wrong jobId or wrong checkpointBaseDir would otherwise "
                    + "'succeed' silently).");
        }

        int deletedCheckpoints = countCheckpoints(jobDir);
        try {
            deleteRecursively(jobDir);
        } catch (IOException e) {
            throw new StreamException(ERR_STREAM_INVALID_STATE, e).param(ARG_DETAIL, "Failed to delete state directory "
                    + jobDir.toAbsolutePath() + " for job " + jobId + ": " + e);
        }
        LOG.info("Reset job state: jobId={} deletedPath={} deletedCheckpoints={}",
                jobId, jobDir.toAbsolutePath(), deletedCheckpoints);
        return new ResetResult(jobId, jobDir, deletedCheckpoints);
    }

    private static int countCheckpoints(Path jobDir) {
        // LocalFileCheckpointStorage layout: <id>.checkpoint artifacts plus
        // <id>.epoch manifests under the job directory tree
        try (Stream<Path> walk = Files.walk(jobDir)) {
            return (int) walk.filter(p -> {
                String name = p.getFileName().toString();
                return name.endsWith(".checkpoint") || name.endsWith(".epoch");
            }).count();
        } catch (IOException e) {
            LOG.warn("Failed to count checkpoint artifacts under {} — resetting anyway: {}",
                    jobDir, e.toString());
            return 0;
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new io.nop.stream.core.exceptions.StreamException(
                            io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE, e)
                            .param(io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL,
                                    "Failed to delete " + p);
                }
            });
        }
        // DirectoryStream sanity: the walk above removed everything; verify
        if (Files.exists(dir)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
                if (ds.iterator().hasNext()) {
                    throw new IOException("State directory not empty after reset: " + dir);
                }
            }
        }
    }
}
