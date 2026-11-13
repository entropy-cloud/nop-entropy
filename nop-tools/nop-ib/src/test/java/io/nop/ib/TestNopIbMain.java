package io.nop.ib;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestNopIbMain {
    private String run(String... args) {
        ByteArrayOutputStream outB = new ByteArrayOutputStream();
        ByteArrayOutputStream errB = new ByteArrayOutputStream();
        int code = NopIbMain.run(args,
                new PrintStream(outB, true, StandardCharsets.UTF_8),
                new PrintStream(errB, true, StandardCharsets.UTF_8));
        return code + "|" + outB + "|" + errB;
    }

    @Test
    void testVersion() {
        String r = run("--version");
        assertTrue(r.startsWith("0|"), r);
        assertTrue(r.contains("nop-ib "), r);
    }

    @Test
    void testHelpExitZero() {
        assertTrue(run("--help").startsWith("0|"));
        assertTrue(run().startsWith("3|"));
    }

    @Test
    void testUnimplementedCommandFailsLoudlyWithUsageCode() {
        String r = run("status");
        assertTrue(r.startsWith("3|"), r);
        assertTrue(r.contains("not yet implemented"), r);
    }
}
