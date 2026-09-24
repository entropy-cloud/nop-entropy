package io.nop.lint.core.lsp;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.json.JsonTool;
import io.nop.lint.core.engine.LanguageRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The stdio transport of the LSP server (roadmap item 41): reads
 * {@code Content-Length}-framed JSON-RPC messages from stdin, dispatches
 * them to {@link NopLintLanguageServer}, writes responses and
 * {@code textDocument/publishDiagnostics} notifications framed to stdout.
 * Lifecycle logs go to stderr — stdout carries only framed LSP traffic.
 *
 * <p>Framing is hand-rolled deliberately: lsp4j is not on the platform
 * dependency tree, and the v1 surface (one request/response shape plus one
 * notification shape) is small enough that the framing is the only protocol
 * code (plan Phase 1 spike adjudication). JSON parse/serialize rides the
 * platform {@link JsonTool}.</p>
 */
public final class NopLintLspLauncher {

    private final NopLintLanguageServer server;
    private final InputStream in;
    private final PrintStream out;
    private final PrintStream err;

    public NopLintLspLauncher(NopLintLanguageServer server, InputStream in, PrintStream out,
                              PrintStream err) {
        this.server = server;
        this.in = in;
        this.out = out;
        this.err = err;
    }

    public static void main(String[] args) throws IOException {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
        NopLintLanguageServer server = NopLintLanguageServer.create();
        new NopLintLspLauncher(server, System.in, System.out, System.err).run();
    }

    /**
     * The frame loop: returns when stdin ends or the client sends {@code
     * exit}.
     */
    public void run() throws IOException {
        boolean exit = false;
        while (!exit) {
            String body = readFrame();
            if (body == null) {
                return;
            }
            Map<String, Object> message = JsonTool.parseMap(body);
            Map<String, Object> response = server.onMessage(message, this::notify);

            String method = (String) message.get("method");
            if ("exit".equals(method)) {
                exit = true;
            }
            if (response != null) {
                writeFrame(JsonTool.serialize(response, false));
            }
        }
    }

    private void notify(String uri, java.util.List<Map<String, Object>> diagnostics) {
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("jsonrpc", "2.0");
        notification.put("method", "textDocument/publishDiagnostics");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("uri", uri);
        params.put("diagnostics", diagnostics);
        notification.put("params", params);
        writeFrame(JsonTool.serialize(notification, false));
    }

    /**
     * Reads one framed body (headers end at the first CRLF CRLF); null on
     * clean EOF between frames.
     */
    String readFrame() throws IOException {
        StringBuilder header = new StringBuilder();
        int b;
        while ((b = in.read()) >= 0) {
            header.append((char) b);
            if (header.length() >= 4 && header.substring(header.length() - 4)
                    .equals("\r\n\r\n")) {
                break;
            }
        }
        if (b < 0 && header.isEmpty()) {
            return null;
        }
        String lower = header.toString().toLowerCase(Locale.ROOT);
        int at = lower.indexOf("content-length:");
        if (at < 0) {
            throw new IOException("LSP frame without Content-Length header: " + header);
        }
        int from = at + "content-length:".length();
        int end = lower.indexOf('\n', from);
        int contentLength = Integer.parseInt(header.substring(from, end).trim());
        byte[] body = in.readNBytes(contentLength);
        if (body.length < contentLength) {
            return null;
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    private void writeFrame(String body) {
        out.print("Content-Length: ");
        out.print(body.getBytes(StandardCharsets.UTF_8).length);
        out.print("\r\n\r\n");
        out.print(body);
        out.flush();
    }
}
