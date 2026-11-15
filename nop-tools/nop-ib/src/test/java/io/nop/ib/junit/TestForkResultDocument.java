package io.nop.ib.junit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression (flink observation, defect 17): the fork's result file is
 * PRE-CREATED empty before the fork starts. When the fork JVM cannot start
 * (argLine option rejected by the JDK, e.g. -Djava.security.manager=allow on
 * JDK 26), the file still exists but stays EMPTY — the old existence-only
 * check produced run=0/failed=0/exit-OK fake green while Maven fails loudly.
 * An empty file must be treated as a crashed fork: failures=1 + crash marker.
 */
class TestForkResultDocument {

    @Test
    void emptyResultFileIsCrashedForkNotFakeGreen(@TempDir Path tmp) throws Exception {
        Path resultFile = tmp.resolve("result.json");
        Files.writeString(resultFile, "");
        List<String> warnings = new ArrayList<>();

        Map<String, Object> summary = JUnitForkExecutor.readResultSummary(
                resultFile, 1, "Error occurred during initialization of VM", "", warnings);

        assertEquals(1, ((Number) summary.get("failures")).intValue(),
                "crashed fork must count as a failure, got " + summary);
        assertEquals(Boolean.TRUE, summary.get("crashed"));
        assertTrue(summary.get("forkError").toString().contains("initialization of VM"));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("no result document")),
                "warning must be emitted, got " + warnings);
    }

    @Test
    void missingResultFileIsCrashedFork(@TempDir Path tmp) throws Exception {
        Path resultFile = tmp.resolve("absent.json");
        List<String> warnings = new ArrayList<>();

        Map<String, Object> summary = JUnitForkExecutor.readResultSummary(
                resultFile, 1, "", "stdout tail kept for diagnosis", warnings);

        assertEquals(1, ((Number) summary.get("failures")).intValue());
        assertTrue(summary.get("forkStdoutTail").toString().contains("stdout tail"));
    }

    @Test
    void validResultDocumentStillParses(@TempDir Path tmp) throws Exception {
        Path resultFile = tmp.resolve("result.json");
        Files.writeString(resultFile, "{\"tests\":3,\"failures\":0,\"skipped\":0}");
        List<String> warnings = new ArrayList<>();

        Map<String, Object> summary = JUnitForkExecutor.readResultSummary(
                resultFile, 0, "", "", warnings);

        assertEquals(3, ((Number) summary.get("tests")).intValue());
        assertEquals(0, ((Number) summary.get("failures")).intValue());
        assertTrue(warnings.isEmpty());
    }
}
