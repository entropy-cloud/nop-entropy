/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.stream.core.connector.ConnectivityProbeOutcome;
import io.nop.stream.core.connector.StreamConnectivityProber;
import io.nop.stream.connector.file.FileSource;
import io.nop.stream.connector.file.FileTwoPhaseCommitSink;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 20 (P-REQ-13) per-family probe tests for the base connector module:
 * file source (H-1: enumerator start = directory reachability), file 2PC sink
 * (H-5: begin+rollback, no residue), message source/sink (adjudicated explicit
 * skip — no probe contract). Misconfigured endpoints return an explicit error
 * code, never a silent pass.
 */
public class TestPreSubmitConnectivityProbe {

    @TempDir
    Path tempDir;

    // ------------------------------------------------------------------
    // H-1: file source family (FLIP-27 enumerator probe)
    // ------------------------------------------------------------------

    @Test
    public void fileSourceProbePassesOnReachableDirectory() throws Exception {
        Path inputDir = tempDir.resolve("in");
        Files.createDirectories(inputDir);
        Files.writeString(inputDir.resolve("a.txt"), "line");
        ConnectivityProbeOutcome outcome = StreamConnectivityProber.probe(
                "file-source", new FileSource(inputDir.toString()));
        assertEquals(ConnectivityProbeOutcome.Status.PASS, outcome.getStatus(), () -> String.valueOf(outcome));
        // Red line: probe only scanned; the input directory content is untouched and
        // no split was assigned (no-op delivery service).
        assertEquals(1, Files.list(inputDir).count());
    }

    @Test
    public void fileSourceProbeFailsExplicitlyOnMissingDirectory() {
        ConnectivityProbeOutcome outcome = StreamConnectivityProber.probe(
                "file-source", new FileSource(tempDir.resolve("no-such-dir").toString()));
        assertEquals(ConnectivityProbeOutcome.Status.FAIL, outcome.getStatus());
        assertEquals("nop.err.stream.connectivity-check-failed", outcome.getErrorCode());
        assertTrue(outcome.getDetail().contains("not found or not a directory"),
                () -> outcome.getDetail());
    }

    // ------------------------------------------------------------------
    // H-5: file 2PC sink family
    // ------------------------------------------------------------------

    @Test
    public void fileTwoPhaseCommitSinkProbePassesAndLeavesNoResidue() throws Exception {
        Path outputDir = tempDir.resolve("out");
        FileTwoPhaseCommitSink<String> sink = new FileTwoPhaseCommitSink<>(outputDir.toString());
        ConnectivityProbeOutcome outcome = StreamConnectivityProber.probe("file-2pc-sink", sink);
        assertEquals(ConnectivityProbeOutcome.Status.PASS, outcome.getStatus(), () -> String.valueOf(outcome));
        // Red line (D3-⑥): output dir is the exempt idempotent object; NO epoch file,
        // NO manifest, NO temp file may exist after the probe.
        assertTrue(Files.isDirectory(outputDir), "output dir is the exempt idempotent object");
        assertEquals(0, Files.list(outputDir).count(),
                "probe must leave no files (residue red line)");
    }

    // ------------------------------------------------------------------
    // Message family: adjudicated explicit skip (D3-③)
    // ------------------------------------------------------------------

    @Test
    public void messageSourceIsExplicitSkip() {
        MessageSourceFunction<String> source = new MessageSourceFunction<>(
                new NoopMessageService(), "topic-x", String.class);
        ConnectivityProbeOutcome outcome = StreamConnectivityProber.probe("message-source", source);
        assertEquals(ConnectivityProbeOutcome.Status.SKIP, outcome.getStatus(), () -> String.valueOf(outcome));
        assertEquals("nop.err.stream.connectivity-not-supported", outcome.getErrorCode());
    }

    @Test
    public void messageSinkIsExplicitSkip() {
        MessageSinkFunction<String> sink = new MessageSinkFunction<>(new NoopMessageService(), "topic-x");
        ConnectivityProbeOutcome outcome = StreamConnectivityProber.probe("message-sink", sink);
        assertEquals(ConnectivityProbeOutcome.Status.SKIP, outcome.getStatus(), () -> String.valueOf(outcome));
        assertEquals("nop.err.stream.connectivity-not-supported", outcome.getErrorCode());
    }

    private static final class NoopMessageService implements IMessageService {
        @Override
        public IMessageSubscription subscribe(String topic, IMessageConsumer consumer,
                                               MessageSubscribeOptions options) {
            throw new UnsupportedOperationException("not part of the skip-probe path");
        }

        @Override
        public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
