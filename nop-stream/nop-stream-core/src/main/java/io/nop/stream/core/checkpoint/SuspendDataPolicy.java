/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

/**
 * WI9 (plan 2333): explicit contract for how a SUSPEND → resubmit cycle treats
 * the data that arrived in the underlying source while the job was stopped
 * (the "downtime gap").
 *
 * <p>The policy is carried on the {@link CheckpointConfig} of the RESUBMITTED
 * execution (the submission that performs the recovery): it is an
 * recovery-time action, so semantically it belongs to the submission that
 * initiates the recovery. It is <b>not</b> persisted in the
 * {@code EpochManifest} — every resubmission must carry it explicitly.
 *
 * <ul>
 *   <li>{@link #REPLAY_GAP} (default) — current behavior: a replayable source
 *       restores the offset from the latest durable checkpoint and re-emits
 *       everything from that offset, so downtime data is replayed (recompute).
 *       The default guarantees zero behavioral change for existing jobs.</li>
 *   <li>{@link #SKIP_GAP} — after restore completes, each supported replayable
 *       source is seeked to its latest available position, so data that arrived
 *       during the downtime window is NOT replayed. Only sources with a
 *       deterministic "latest" position are supported; currently that is
 *       {@code CollectionReplayableSource} (seek to its size). Any other
 *       {@code ReplayableSourceFunction} is rejected with a typed error at
 *       startup — a loud failure beats a wrong guess about what "latest" means
 *       for that source. A source that is not replayable at all has no offset
 *       to seek: SKIP_GAP has no effect on it and its own recovery semantics
 *       apply.</li>
 * </ul>
 */
public enum SuspendDataPolicy {

    /**
     * Recompute the downtime gap: replay from the restored offset (default,
     * identical to the behavior before this contract was introduced).
     */
    REPLAY_GAP,

    /**
     * Skip the downtime gap: seek each supported replayable source to its
     * latest available position after restore, so downtime data is not
     * re-emitted.
     */
    SKIP_GAP
}
