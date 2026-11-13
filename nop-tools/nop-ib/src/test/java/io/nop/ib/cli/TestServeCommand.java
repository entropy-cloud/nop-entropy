package io.nop.ib.cli;

import io.nop.ib.engine.IbEngine;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestServeCommand {

    @Test
    void testHandleStatusAndShutdown() throws Exception {
        // walk up to the repo root from the surefire working dir
        Path dir = Paths.get(System.getProperty("user.dir"));
        while (dir != null && !Files.isRegularFile(dir.resolve("mvnw")))
            dir = dir.getParent();
        assertNotNull(dir, "must run inside the repo");
        IbEngine engine = new IbEngine(dir);

        var status = ServeCommand.handle(engine,
                "{\"id\":1,\"cmd\":\"status\",\"args\":[]}", System.err);
        assertEquals(1, ((Number) status.get("id")).intValue());
        assertEquals(0, ((Number) status.get("exit")).intValue());

        var unknown = ServeCommand.handle(engine, "{\"id\":2,\"cmd\":\"frobnicate\"}", System.err);
        assertEquals("error", unknown.get("event"));

        var shutdown = ServeCommand.handle(engine, "{\"id\":3,\"cmd\":\"shutdown\"}", System.err);
        assertEquals("shutdown", shutdown.get("event"));
    }
}
