package io.nop.bytecode.cli;

import io.nop.bytecode.TestCompiler;
import io.nop.bytecode.NopBytecodeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end channel tests: from directory/jar inputs to rendered diagnostics, through the
 * real collect → read-back → analyze pipeline (no mocks).
 */
class NopBytecodeMainTest {

    private static final String TOY = """
            public class ToyNull {
                static String derefWithoutCheck(String s) {
                    return s.trim();
                }
                static String derefGuarded(String s) {
                    if (s == null) return "x";
                    return s.trim();
                }
            }
            """;

    private Path toyClasses(Path tmp) throws IOException {
        Path classes = tmp.resolve("toy-classes");
        TestCompiler.compile("17", classes, "ToyNull.java", TOY);
        return classes;
    }

    private record RunResult(int exitCode, String out, String err) { }

    private RunResult run(String[] args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = NopBytecodeMain.run(args, new PrintStream(out), new PrintStream(err));
        return new RunResult(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void endToEndConsoleOutputAndExitZero(@TempDir Path tmp) throws IOException {
        Path classes = toyClasses(tmp);
        RunResult r = run(new String[]{classes.toString()});
        assertEquals(0, r.exitCode(), r.err());
        assertTrue(r.out().contains("[warning] nullflow/may-null-deref ToyNull#derefWithoutCheck@"), r.out());
        assertFalse(r.out().contains("derefGuarded"), "guarded method must not appear");
    }

    @Test
    void jsonFormParsesAndEscapes(@TempDir Path tmp) throws IOException {
        Path classes = toyClasses(tmp);
        RunResult r = run(new String[]{classes.toString(), "--json"});
        assertEquals(0, r.exitCode(), r.err());
        // parse-back via naive structural assertions (no JSON dependency in main code)
        assertTrue(r.out().startsWith("["));
        assertTrue(r.out().contains("\"ruleId\": \"nullflow/may-null-deref\""));
        assertTrue(r.out().contains("\"className\": \"ToyNull\""));
        assertTrue(r.out().contains("\"insnIndex\": "));
        // escaping unit checks through the renderer API
        assertEquals("a\\\"b\\\\c\\nd\\u0001e", FindingRenderer.escape("a\"b\\c\nd\u0001e"));
        assertEquals("<init>", FindingRenderer.escape("<init>"));
        for (String ref : List.of("[array]", "Owner#field", "owner.method")) {
            assertEquals(ref, FindingRenderer.escape(ref));
        }
    }

    /** Minimal JSON parse-back for the flat object-array shape the renderer emits. */
    private static List<Map<String, Object>> parseFindingsJson(String json) {
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        int i = 0;
        expect(json, i, '[');
        i = 1;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == ' ') { i++; continue; }
            if (c == ']') break;
            if (c == ',') { i++; continue; }
            if (c != '{') throw new IllegalStateException("expected object at " + i);
            int end = json.indexOf('}', i);
            String body = json.substring(i + 1, end);
            Map<String, Object> obj = new java.util.LinkedHashMap<>();
            for (String field : splitFields(body)) {
                String[] kv = field.split(":", 2);
                String key = unquote(kv[0].trim());
                Object value = kv[1].trim().startsWith("\"")
                        ? unquote(kv[1].trim())
                        : Integer.parseInt(kv[1].trim());
                obj.put(key, value);
            }
            out.add(obj);
            i = end + 1;
        }
        return out;
    }

    private static String[] splitFields(String body) {
        // split top-level commas outside quoted strings
        List<String> fields = new java.util.ArrayList<>();
        boolean inStr = false;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (inStr) {
                if (c == '\\') { cur.append(c).append(body.charAt(++i)); continue; }
                if (c == '"') inStr = false;
            } else if (c == '"') inStr = true;
            else if (c == ',') { fields.add(cur.toString()); cur.setLength(0); continue; }
            cur.append(c);
        }
        fields.add(cur.toString());
        return fields.toArray(new String[0]);
    }

    private static void expect(String s, int i, char c) {
        if (s.charAt(i) != c) throw new IllegalStateException("expected " + c + " at " + i);
    }

    private static String unquote(String s) {
        String body = s.substring(1, s.length() - 1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '\\') {
                char n = body.charAt(++i);
                switch (n) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    default -> sb.append(n);
                }
            } else sb.append(c);
        }
        return sb.toString();
    }

    @Test
    void sameContentViaDifferentlyNamedContainersIsDeduplicated(@TempDir Path tmp) throws IOException {
        Path dir = toyClasses(tmp);
        Path jar = tmp.resolve("copy.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            Files.walk(dir).filter(p -> p.toString().endsWith(".class")).forEach(p -> {
                try {
                    out.putNextEntry(new JarEntry(dir.relativize(p).toString().replace(java.io.File.separatorChar, '/')));
                    out.write(Files.readAllBytes(p));
                } catch (IOException e) {
                    throw new NopBytecodeException("fixture jar write failed", e);
                }
            });
        }
        RunResult single = run(new String[]{dir.toString()});
        // dir + same-content jar: relativePaths differ (jar entries are prefixed), finding keys
        // (class#method@insn|ref) are identical -> channel-level dedup reports one finding set
        RunResult both = run(new String[]{dir.toString(), jar.toString()});
        assertEquals(0, both.exitCode(), both.err());
        assertEquals(single.out(), both.out(), "same content via differently named containers yields one report");
    }

    @Test
    void tamperedSha256BetweenCollectAndReadFailsLoudly(@TempDir Path tmp) throws IOException {
        Path classes = toyClasses(tmp);
        Path manifestFile = tmp.resolve("m.properties");
        assertEquals(0, run(new String[]{classes.toString(), "--manifest", manifestFile.toString()}).exitCode());
        // tamper: overwrite the recorded sha256 column with a wrong value (size/mtime stay valid,
        // so collect takes the reuse fast path; read-back then detects the mismatch)
        List<String> lines = Files.readAllLines(manifestFile);
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            if (!l.startsWith("#") && l.contains(";")) {
                String[] parts = l.split("=", 2);
                String[] cols = parts[1].split(";", -1);
                cols[2] = "0".repeat(64);
                lines.set(i, parts[0] + "=" + String.join(";", cols));
            }
        }
        Files.write(manifestFile, lines);
        RunResult r = run(new String[]{classes.toString(), "--manifest", manifestFile.toString()});
        assertEquals(2, r.exitCode());
        assertTrue(r.err().contains("TOCTOU") || r.err().contains("hash"), r.err());
    }

    @Test
    void corruptManifestFailsLoudly(@TempDir Path tmp) throws IOException {
        Path classes = toyClasses(tmp);
        Path manifestFile = tmp.resolve("broken.properties");
        Files.write(manifestFile, "somepath=only;three;columns\n".getBytes(StandardCharsets.UTF_8));
        RunResult r = run(new String[]{classes.toString(), "--manifest", manifestFile.toString()});
        assertEquals(2, r.exitCode());
        assertTrue(r.err().contains("Malformed manifest"), r.err());
    }

    @Test
    void multiReleaseBaseEntryWinsOverVersionsEntry(@TempDir Path tmp) throws IOException {
        // base entry: unguarded deref (finding); versions entry: guarded variant (no finding).
        // className dedup must keep the base entry -> the finding IS reported.
        String unguarded = "package p;\npublic class A { static int go(String s) { return s.length(); } }\n";
        String guarded = "package p;\npublic class A { static int go(String s) {"
                + " if (s == null) return -1; return s.length(); } }\n";
        Path base = tmp.resolve("base-classes");
        Path versions = tmp.resolve("versions-classes");
        TestCompiler.compile("17", base, "A.java", unguarded);
        TestCompiler.compile("17", versions, "A.java", guarded);
        Path jar = tmp.resolve("mr.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            for (Path p2 : Files.walk(base).filter(f -> f.toString().endsWith(".class")).toList()) {
                out.putNextEntry(new JarEntry(base.relativize(p2).toString().replace(java.io.File.separatorChar, '/')));
                out.write(Files.readAllBytes(p2));
            }
            for (Path p2 : Files.walk(versions).filter(f -> f.toString().endsWith(".class")).toList()) {
                out.putNextEntry(new JarEntry("META-INF/versions/17/" + versions.relativize(p2).toString().replace(java.io.File.separatorChar, '/')));
                out.write(Files.readAllBytes(p2));
            }
        }
        RunResult r = run(new String[]{jar.toString(), "--json"});
        assertEquals(0, r.exitCode(), r.err());
        assertTrue(r.out().contains("\"className\": \"p.A\"") && r.out().contains("\"insnIndex\": 3"),
                "base (unguarded) entry analyzed: " + r.out());
    }

    @Test
    void resourceLeakFindingsFlowThroughChannel(@TempDir Path tmp) throws IOException {
        String leak = """
                public class ResourceLeakToy {
                    static int unclosed(String p) throws Exception {
                        java.io.FileInputStream in = new java.io.FileInputStream(p);
                        return in.read();
                    }
                }
                """;
        Path classes = tmp.resolve("leak-classes");
        TestCompiler.compile("17", classes, "ResourceLeakToy.java", leak);
        RunResult r = run(new String[]{classes.toString(), "--json"});
        assertEquals(0, r.exitCode(), r.err());
        assertTrue(r.out().contains("resources/unclosed-resource"), r.out());
        assertTrue(r.out().contains("\"className\": \"ResourceLeakToy\""), r.out());
    }

    @Test
    void samePathTwiceFailsLoudly(@TempDir Path tmp) throws IOException {
        Path dir = toyClasses(tmp);
        RunResult r = run(new String[]{dir.toString(), dir.toString()});
        assertEquals(2, r.exitCode());
        assertTrue(r.err().contains("Duplicate relativePath"), r.err());
    }

    @Test
    void missingInputExitsTwoWithErrorOnStderr(@TempDir Path tmp) {
        Path missing = tmp.resolve("does-not-exist");
        RunResult r = run(new String[]{missing.toString()});
        assertEquals(2, r.exitCode());
        assertTrue(r.err().contains("does-not-exist"), r.err());
    }

    @Test
    void usageErrorsExitTwo(@TempDir Path tmp) {
        assertEquals(2, run(new String[0]).exitCode());
        assertEquals(2, run(new String[]{"--unknown-flag", tmp.toString()}).exitCode());
        assertEquals(2, run(new String[]{"--manifest"}).exitCode());
        Path notJar = tmp.resolve("plain.txt");
        try {
            Files.write(notJar, "x".getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new NopBytecodeException("fixture jar write failed", e);
        }
        assertEquals(2, run(new String[]{notJar.toString()}).exitCode());
    }

    @Test
    void corruptClassFileExitsTwoWithoutPartialReport(@TempDir Path tmp) throws IOException {
        Path classes = toyClasses(tmp);
        Files.write(classes.resolve("Broken.class"), "garbage".getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = NopBytecodeMain.run(new String[]{classes.toString()},
                new PrintStream(out), new PrintStream(err));
        assertEquals(2, code);
        assertTrue(err.toString().contains("Broken.class"), err.toString());
        // fail-fast: no partial report printed to stdout
        assertEquals("", out.toString(StandardCharsets.UTF_8), "no partial report on failure");
    }


}
