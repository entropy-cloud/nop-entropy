package io.nop.job.local;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * CRA 8.46 R4-06 (plan 2539) W-09 / 1.4-C4-002 (driver 红⑨) + W-10 / 1.4-C4-005
 * (driver 红⑩) — structural documentation pins (honest structure-red discipline:
 * both work orders are declaration-face repairs, zero product diff). The owner doc
 * must carry the promotion/envelope contract sentences; the pre-fix evasive wording
 * must be gone. Mutation legs (restore the old wording → pin red → re-fix → green)
 * are recorded in the red-green ledger.
 */
public class TestJobCarrierDocPinsR406 {

    private static String ownerDoc() throws IOException {
        Path path = Paths.get("..", "..", "docs-for-ai", "03-modules", "nop-job.md");
        if (!Files.exists(path)) {
            path = Paths.get("docs-for-ai", "03-modules", "nop-job.md");
        }
        if (!Files.exists(path)) {
            fail("cannot locate the nop-job owner doc for the structure pin: " + path);
        }
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /**
     * 红⑨ (W-09): the local-mode non-blocking contract must be STATED, and the old
     * "not declaring it fixed" evasion sentence must be replaced by the explicit
     * contract (the evasion sentence WAS the defect face).
     */
    @Test
    public void localModeNonBlockingContractIsStated() throws IOException {
        String doc = ownerDoc();
        assertTrue(doc.contains("job 方法体必须非阻塞"),
                "owner doc §1.4 must carry the explicit non-blocking contract sentence (W-09)");
        assertTrue(doc.contains("要么真异步") || doc.contains("真异步（快速返回未完成 CompletionStage）"),
                "owner doc §3.1 must upgrade the async contract to a hard requirement (W-09)");
        assertFalse(doc.contains("此处不声明已修"),
                "the evasion wording '此处不声明已修' must be REPLACED by the contract sentence");
    }

    /**
     * 红⑩ (W-10): the worker max-concurrency semantics must be precise (DB claim-row
     * gate, NOT a JVM concurrency cap), and §七 must carry the worker execution
     * envelope declaration (non-blocking invoker on the globalWorker pool).
     */
    @Test
    public void workerEnvelopeDeclarationIsStated() throws IOException {
        String doc = ownerDoc();
        assertTrue(doc.contains("DB 在途认领行数上限"),
                "owner doc §五 max-concurrency must be precise: DB claim-row gate (W-10), "
                        + "actual doc still has the ambiguous wording");
        assertTrue(doc.contains("非 JVM 内执行并发上限"),
                "the precision must state it is NOT a JVM-side execution concurrency cap");
        assertTrue(doc.contains("invoker 实现必须非阻塞"),
                "owner doc §七 must carry the worker execution envelope declaration (W-10)");
    }
}
