/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.maintain;

import io.nop.message.core.local.LocalMessageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI4 (plan 2329): end-to-end wiring of the {@code sample} command — CLI entry →
 * reflective connector-registry container assembly → SPI factory resolution →
 * param-rule validation → sampler dispatch → real output. The supported path samples a
 * real file directory; the SKIP path drives the real message-family endpoint (its
 * params construct against a local IMessageService) and must answer an explicit SKIP,
 * not an empty record list.
 */
public class TestStreamSampleCommand {

    @TempDir
    Path dir;

    @Test
    public void fileSourceSamplingEndToEnd() throws Exception {
        Files.write(dir.resolve("a.txt"), List.of("l1", "l2", "l3"));
        int exit = StreamSampleCommand.run("file",
                Map.of("directoryPath", dir.toString()), 2);
        assertEquals(0, exit);
    }

    @Test
    public void messageFamilyAnswersExplicitSkipWithExitZero() {
        int exit = StreamSampleCommand.run("message",
                Map.of("topic", "sample-t", "messageService", new LocalMessageService()), 5);
        assertEquals(0, exit);
    }

    @Test
    public void missingRequiredParamFailsValidationWithExitOne() {
        int exit = StreamSampleCommand.run("file", Map.of(), 5);
        assertEquals(1, exit);
    }

    @Test
    public void unknownTypeFailsWithExitOne() {
        // D7 contract: resolve failures become a structured report issue
        // (connector-type-not-found), not a thrown exception — exit 1.
        int exit = StreamSampleCommand.run("no-such-type", Map.of(), 5);
        assertEquals(1, exit);
    }

    @Test
    public void sinkTypeFailsTypedWithExitOne() {
        // WI4 closure adjudication: sample is source-only. A SINK type name is a typed
        // direction mismatch at connector-validation (exit 1) — sinks are never routed
        // to the sampler, so their "not sampleable" surface is FAIL, not SKIP. The SKIP
        // closed set is the three SOURCE-family endpoints without a sampling contract.
        int exit = StreamSampleCommand.run("jdbc-2pc", Map.of(), 5);
        assertEquals(1, exit);
    }

    @Test
    public void usageErrorsReturnExitTwo() {
        assertEquals(2, StreamSampleCommand.run(List.of("limit=5")));
        assertEquals(2, StreamSampleCommand.run(List.of("type=file", "malformed")));
    }
}
