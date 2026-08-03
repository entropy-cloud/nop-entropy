/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.source;

import java.io.Serializable;

/**
 * Factory and contract for a FLIP-27 style split-based source. A {@code Source} is a
 * serializable description that the runtime uses to instantiate:
 *
 * <ul>
 *   <li>a single {@link SplitEnumerator} on the {@code JobCoordinator} side (coordinator-side,
 *       non-parallel), and</li>
 *   <li>one {@link SourceReader} per parallel subtask on the task side.</li>
 * </ul>
 *
 * <p>Stage 49 D1: this is the {@code Source}/{@code SplitEnumerator}/{@code SourceReader}/
 * {@code SourceSplit} four-contract entry point that supersedes the standalone concrete
 * {@code SourceEnumerator}/{@code SourceSplit} prototypes (Stage 49 D6). It coexists with
 * the legacy {@code SourceFunction} path: {@code env.addSource(Source)} routes through a
 * separate {@code SourceApiTransformation} + {@code SourceReaderOperator} (Stage 49 D5),
 * leaving the {@code SourceFunction} path untouched.
 *
 * <p>{@code Source} is serializable so it can travel through {@code StreamGraphGenerator},
 * {@code JobGraphGenerator}, and (in distributed mode) {@code TaskDeploymentDescriptor}.
 * The live enumerator / reader instances are created by the runtime from this descriptor;
 * they are not embedded in the descriptor (Stage 49 D3 — splits delivered post-deploy).
 *
 * @param <OUT>        the element type this source produces
 * @param <SplitT>     the split type this source produces and consumes
 * @param <EnumStateT> the enumerator-state type this source checkpoints
 */
public interface Source<OUT, SplitT extends SourceSplit, EnumStateT> extends Serializable {

    /**
     * Creates a fresh {@link SplitEnumerator} for an initial job start (no prior state).
     *
     * @return a new enumerator; non-null
     */
    SplitEnumerator<SplitT, EnumStateT> createEnumerator();

    /**
     * Restores a {@link SplitEnumerator} from the given checkpoint state.
     *
     * @param checkpointState the enumerator state recovered from
     *                        {@code EpochManifest.sourceEnumeratorSnapshots}
     */
    SplitEnumerator<SplitT, EnumStateT> restoreEnumerator(EnumStateT checkpointState);

    /**
     * Creates a {@link SourceReader} for one parallel subtask.
     *
     * @param readerContext the reader's identity / parallelism / assignment channel
     */
    SourceReader<OUT, SplitT> createReader(SourceReaderContext readerContext);

    /**
     * Returns the serializer used to round-trip {@link SplitEnumerator#snapshotState(long)}
     * output through {@code EpochManifest.sourceEnumeratorSnapshots}.
     */
    SimpleVersionedSerializer<EnumStateT> getEnumeratorStateSerializer();

    /**
     * Returns the serializer used to round-trip per-split cursor state (from
     * {@link SourceReader#snapshotState(long)}) through {@code TaskEpochSnapshot}.
     */
    SimpleVersionedSerializer<SplitT> getSplitSerializer();

    /**
     * Returns whether this source is bounded or continuous.
     */
    Boundedness getBoundedness();
}
