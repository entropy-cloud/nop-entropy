/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.batch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.impl.BatchTaskContextImpl;

import io.nop.stream.core.common.functions.source.ParallelismCheckable;
import io.nop.stream.core.connector.ConnectivityCheckable;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.source.Boundedness;
import io.nop.stream.core.source.SimpleVersionedSerializer;
import io.nop.stream.core.source.Source;
import io.nop.stream.core.source.SourceReader;
import io.nop.stream.core.source.SourceReaderContext;
import io.nop.stream.core.source.SplitEnumerator;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ACTUAL_VALUE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DECLARED_VALUE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_TYPE_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CONNECTOR_CAPABILITY_MISMATCH;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;

/**
 * WI2 (plan 2343 Phase 1): FLIP-27 form of the batch-loader source — the connector
 * factory now produces this class; the legacy {@code BatchLoaderSourceFunction} keeps
 * its programmatic API (direct constructions in tests / FILL_HISTORY harnesses) but is
 * no longer produced by the factory.
 *
 * <p>Semantics preserved from the legacy function (R3 baseline): AT_LEAST_ONCE seek
 * resume with the deterministic-traversal premise and the typed skip-shortfall
 * rejection; the single-writer constraint via {@link ParallelismCheckable} (checked by
 * both plan builders at deployment time and re-checked by the reader at start);
 * pre-submit connectivity via {@link ConnectivityCheckable}.
 */
public class BatchLoaderSource<S> implements Source<S, BatchLoaderSplit, BatchLoaderEnumeratorState>,
        ConnectivityCheckable, ParallelismCheckable {

    private static final long serialVersionUID = 1L;

    private final IBatchLoaderProvider<S> loaderProvider;
    private final int batchSize;

    public BatchLoaderSource(IBatchLoaderProvider<S> loaderProvider) {
        this(loaderProvider, 1);
    }

    public BatchLoaderSource(IBatchLoaderProvider<S> loaderProvider, int batchSize) {
        if (loaderProvider == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG)
                    .param(ARG_ARG_NAME, "loaderProvider");
        }
        if (batchSize < 1) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL,
                    "batchSize must be at least 1, got: " + batchSize);
        }
        this.loaderProvider = loaderProvider;
        this.batchSize = batchSize;
    }

    @Override
    public SplitEnumerator<BatchLoaderSplit, BatchLoaderEnumeratorState> createEnumerator() {
        return new BatchLoaderSplitEnumerator();
    }

    @Override
    public SplitEnumerator<BatchLoaderSplit, BatchLoaderEnumeratorState> restoreEnumerator(
            BatchLoaderEnumeratorState state) {
        BatchLoaderSplitEnumerator enumerator = new BatchLoaderSplitEnumerator();
        enumerator.restoreState(state);
        return enumerator;
    }

    @Override
    public SourceReader<S, BatchLoaderSplit> createReader(SourceReaderContext readerContext) {
        return new BatchLoaderSourceReader<>(loaderProvider, batchSize);
    }

    @Override
    public SimpleVersionedSerializer<BatchLoaderEnumeratorState> getEnumeratorStateSerializer() {
        return new EnumeratorStateSerializer();
    }

    @Override
    public SimpleVersionedSerializer<BatchLoaderSplit> getSplitSerializer() {
        return new SplitSerializer();
    }

    @Override
    public Boundedness getBoundedness() {
        return Boundedness.BOUNDED;
    }

    /**
     * P-REQ-13 / D3: pre-submit connectivity probe — {@code loaderProvider.setup()}
     * exercises the provider's connectivity without consuming any batch record; the
     * loader is closed when {@code AutoCloseable} (D3-⑥ cleanup semantics).
     */
    @Override
    public void checkConnection() throws Exception {
        IBatchTaskContext taskContext = new BatchTaskContextImpl();
        IBatchLoaderProvider.IBatchLoader<S> loader = loaderProvider.setup(taskContext);
        if (loader == null) {
            throw new StreamException(ERR_STREAM_STATE_ERROR)
                    .param(ARG_DETAIL, "loaderProvider.setup() returned null loader; provider connectivity "
                            + "cannot be verified");
        }
        if (loader instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }

    /**
     * R5-CON-05 single-writer gate (deployment-time, idempotent — no template-state
     * mutation, unlike the legacy hash-sharded form). Called by both plan builders.
     */
    @Override
    public void validateParallelism(int parallelism) {
        if (parallelism > 1) {
            throw singleWriterError(parallelism);
        }
    }

    static StreamException singleWriterError(int parallelism) {
        return (StreamException) new StreamException(ERR_STREAM_CONNECTOR_CAPABILITY_MISMATCH)
                .param(ARG_TYPE_NAME, "batch-loader")
                .param(ARG_DECLARED_VALUE, "parallelism=" + parallelism)
                .param(ARG_ACTUAL_VALUE,
                        "single-writer implementation without per-subtask sharding — every "
                                + "subtask copy would emit the full loader dataset (N-times "
                                + "duplication); deploy with parallelism=1");
    }

    static StreamException skipShortfallError(long skipped, long offset) {
        return (StreamException) new StreamException(ERR_STREAM_STATE_ERROR)
                .param(ARG_DETAIL, "BatchLoaderSource skip shortfall: loader exhausted after "
                        + skipped + " of " + offset + " records to skip — loader traversal order is "
                        + "not deterministic across restarts, or the dataset shrank since the checkpoint");
    }

    static StreamException secondAssignmentError(int splitCount) {
        return (StreamException) new StreamException(ERR_STREAM_INVALID_ARG)
                .param(ARG_DETAIL, "BatchLoaderSourceReader already adopted the single batch split; "
                        + "a post-start assignment of " + splitCount + " additional split(s) would restart "
                        + "the traversal and is refused instead of silently re-reading");
    }

    static StreamException postStartRestoreError() {
        return (StreamException) new StreamException(ERR_STREAM_INVALID_ARG)
                .param(ARG_DETAIL, "BatchLoaderSourceReader restoreState arrived after the traversal "
                        + "started — restoring now would silently rewind the live cursor");
    }

    // ====================== Serializers ======================

    /** Encodes the split cursor ({@code offset}) as decimal text. Versioned at 1. */
    static final class SplitSerializer implements SimpleVersionedSerializer<BatchLoaderSplit> {

        private static final long serialVersionUID = 1L;
        private static final int VERSION = 1;

        @Override
        public byte[] serialize(BatchLoaderSplit split) throws IOException {
            return String.valueOf(split.getOffset()).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public BatchLoaderSplit deserialize(int version, byte[] bytes) throws IOException {
            if (version != VERSION) {
                throw new IOException("Unsupported BatchLoaderSplit serializer version: " + version);
            }
            try {
                return new BatchLoaderSplit(Long.parseLong(new String(bytes, StandardCharsets.UTF_8)));
            } catch (NumberFormatException e) {
                throw new IOException("Malformed BatchLoaderSplit offset payload", e);
            }
        }

        @Override
        public int getVersion() {
            return VERSION;
        }
    }

    /** Encodes the enumerator state ({@code assigned}) as decimal text. Versioned at 1. */
    static final class EnumeratorStateSerializer
            implements SimpleVersionedSerializer<BatchLoaderEnumeratorState> {

        private static final long serialVersionUID = 1L;
        private static final int VERSION = 1;

        @Override
        public byte[] serialize(BatchLoaderEnumeratorState state) throws IOException {
            return (state.isAssigned() ? "1" : "0").getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public BatchLoaderEnumeratorState deserialize(int version, byte[] bytes) throws IOException {
            if (version != VERSION) {
                throw new IOException("Unsupported BatchLoaderEnumeratorState serializer version: " + version);
            }
            String s = new String(bytes, StandardCharsets.UTF_8);
            if ("1".equals(s)) {
                return new BatchLoaderEnumeratorState(true);
            }
            if ("0".equals(s)) {
                return new BatchLoaderEnumeratorState(false);
            }
            throw new IOException("Malformed BatchLoaderEnumeratorState payload: " + s);
        }

        @Override
        public int getVersion() {
            return VERSION;
        }
    }
}
