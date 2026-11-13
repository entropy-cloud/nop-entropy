package io.nop.ib.cli;

import io.nop.ib.NopIbMain;
import io.nop.ib.engine.IbEngine;
import io.nop.ib.json.JsonIn;
import io.nop.ib.json.JsonOut;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code nop-ib serve}: JSONL request loop (design 05 §九). One request per input
 * line; one reply per line on stdout. Crash safety is structural: all state lives
 * on disk (.nop-ib), never in process memory, so a restarted serve picks up where
 * the previous process died. Events: hello (version handshake) on start; replies
 * carry the caller's id.
 */
public final class ServeCommand {
    private ServeCommand() {
    }

    public static int serve(IbEngine engine, java.io.PrintStream out, java.io.PrintStream err) {
        Map<String, Object> hello = new LinkedHashMap<>();
        hello.put("event", "hello");
        hello.put("version", NopIbMain.VERSION);
        hello.put("commands", List.of("status", "plan", "compile", "test", "check"));
        out.println(JsonOut.write(hello));

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank())
                    continue;
                Map<String, Object> reply = handle(engine, line, err);
                out.println(JsonOut.write(reply));
                if ("shutdown".equals(reply.get("event")))
                    break;
            }
            return Commands.EXIT_OK;
        } catch (Exception e) {
            err.println("serve terminated: " + String.valueOf(e));
            return Commands.EXIT_FAILED;
        }
    }

    static Map<String, Object> handle(IbEngine engine, String line, java.io.PrintStream err) {
        Map<String, Object> req;
        try {
            req = JsonIn.readAsMap(line);
        } catch (Exception e) {
            return Map.of("event", "error", "error", "unparseable request line");
        }
        Object id = req.get("id");
        String cmd = String.valueOf(req.get("cmd"));
        @SuppressWarnings("unchecked")
        List<String> args = (List<String>) (req.get("args") != null ? req.get("args") : List.of());

        Map<String, Object> reply = new LinkedHashMap<>();
        reply.put("id", id);
        reply.put("cmd", cmd);
        if ("shutdown".equals(cmd)) {
            reply.put("event", "shutdown");
            return reply;
        }
        try {
            List<String> allArgs = new ArrayList<>(args);
            allArgs.add("--json");
            ByteArrayOutputStream2 buf = new ByteArrayOutputStream2();
            int code;
            switch (cmd) {
                case "status" -> code = Commands.status(engine, buf.printStream(), true);
                case "plan" -> code = Commands.plan(engine, allArgs, false, buf.printStream(), true);
                case "compile", "test", "check" -> {
                    engine.refreshIndexesIfModelChanged();
                    code = io.nop.ib.NopIbMain.run(
                            prepend(cmd, allArgs), buf.printStream(), buf.printStream());
                }
                default -> {
                    reply.put("event", "error");
                    reply.put("error", "unsupported serve cmd: " + cmd);
                    return reply;
                }
            }
            reply.put("exit", code);
            Object payload = JsonIn.silentParse(buf.toStringUtf8());
            if (payload != null)
                reply.put("payload", payload);
        } catch (Exception e) {
            reply.put("exit", Commands.EXIT_FAILED);
            reply.put("error", String.valueOf(e));
        }
        return reply;
    }

    private static String[] prepend(String cmd, List<String> args) {
        String[] all = new String[args.size() + 1];
        all[0] = cmd;
        for (int i = 0; i < args.size(); i++)
            all[i + 1] = args.get(i);
        return all;
    }

    static final class ByteArrayOutputStream2 extends java.io.ByteArrayOutputStream {
        java.io.PrintStream printStream() {
            return new java.io.PrintStream(this, true, StandardCharsets.UTF_8);
        }

        String toStringUtf8() {
            return new String(toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
