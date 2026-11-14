package io.nop.ib.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Run-log durability (mvn-output equivalence): every test/check/compile must
 * leave a human-readable .nop-ib/logs/<cmd>-<ts>.log and a machine-readable
 * last-run.json — an AI session or post-hoc audit must read the full evidence
 * (failure detail, diagnostics) after the process is gone.
 */
class TestRunLog {

    @Test
    void moduleReportAndFailureDetailLandInLogAndState(@TempDir Path tmp) throws Exception {
        RunLog log = RunLog.start(tmp, "test");
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("ok", false);
        report.put("module", "demo-mod");
        report.put("run", 3);
        report.put("failed", 1);
        report.put("failedDetail", "{class=demo.Cls, method=m(), type=AssertionFailedError, message=boom}");
        log.module(report, "[nop-ib] demo-mod (junit-fork) tests: run=3 failed=1");
        log.exitCode(1);
        log.close();

        List<String> lines = Files.readAllLines(log.getLogFile(), StandardCharsets.UTF_8);
        assertTrue(lines.get(0).startsWith("# nop-ib test log"), String.valueOf(lines));
        assertTrue(lines.stream().anyMatch(l -> l.contains("run=3 failed=1")), String.valueOf(lines));
        // the failure detail is indented into the log: readable like a surefire report
        assertTrue(lines.stream().anyMatch(l -> l.contains("demo.Cls") && l.contains("boom")),
                String.valueOf(lines));

        Map<String, Object> state = io.nop.ib.json.JsonIn.asMapOrEmpty(io.nop.ib.json.JsonIn.silentParse(
                Files.readString(tmp.resolve(".nop-ib/last-run.json"))));
        assertEquals("test", state.get("cmd"));
        // JSON roundtrip widens numbers to Long
        assertEquals(1L, ((Number) state.get("exitCode")).longValue());
        assertEquals(1, ((List<?>) state.get("modules")).size());
        assertEquals(log.getLogFile().toString(), state.get("log"));
    }

    @Test
    void compileDiagnosticsGoThroughLine(@TempDir Path tmp) throws Exception {
        RunLog log = RunLog.start(tmp, "compile");
        log.line("compiled: []");
        log.line("--- demo-mod ---\nERROR javac A.java:1: cannot find symbol");
        log.exitCode(1);
        log.close();

        String text = Files.readString(log.getLogFile(), StandardCharsets.UTF_8);
        assertTrue(text.contains("cannot find symbol"), text);
        Map<String, Object> state = io.nop.ib.json.JsonIn.asMapOrEmpty(io.nop.ib.json.JsonIn.silentParse(
                Files.readString(tmp.resolve(".nop-ib/last-run.json"))));
        List<?> notes = (List<?>) state.get("notes");
        assertTrue(notes.stream().anyMatch(n -> String.valueOf(n).contains("cannot find symbol")),
                String.valueOf(notes));
    }

    @Test
    void concurrentModuleReportsAllRecorded(@TempDir Path tmp) throws Exception {
        RunLog log = RunLog.start(tmp, "test");
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        List<Thread> threads = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            final int idx = i;
            Thread t = new Thread(() -> {
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                Map<String, Object> report = new LinkedHashMap<>();
                report.put("module", "mod-" + idx);
                report.put("ok", true);
                log.module(report, "[nop-ib] mod-" + idx + " ok");
            });
            t.start();
            threads.add(t);
        }
        go.countDown();
        for (Thread t : threads)
            t.join();
        log.exitCode(0);
        log.close();
        Map<String, Object> state = io.nop.ib.json.JsonIn.asMapOrEmpty(io.nop.ib.json.JsonIn.silentParse(
                Files.readString(tmp.resolve(".nop-ib/last-run.json"))));
        assertEquals(8, ((List<?>) state.get("modules")).size(), "every concurrent module report kept");
    }
}
