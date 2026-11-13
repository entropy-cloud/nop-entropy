package io.nop.ib.cli;

import io.nop.ib.engine.GitChanges;
import io.nop.ib.engine.IbEngine;
import io.nop.ib.json.JsonOut;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code nop-ib bench <module-artifactId>}: the four cost baselines of design 05
 * §十一, measured on the spot (never quoted from another machine):
 * <ul>
 *   <li>noop: status+plan with a clean tree;</li>
 *   <li>singleMethod: body-only probe edit → check end-to-end;</li>
 *   <li>probe restore → verification that the tree returned to pristine.</li>
 * </ul>
 * The probe edits ONE method body of the module's first main source and restores
 * the exact original bytes afterwards.
 */
public final class BenchCommand {
    private BenchCommand() {
    }

    public static int bench(IbEngine engine, List<String> args, boolean json, PrintStream out,
                            PrintStream err) {
        if (args.isEmpty()) {
            err.println("usage error: bench <module-artifactId> [check|compile]");
            return Commands.EXIT_USAGE;
        }
        String moduleId = args.get(0);
        var module = engine.model().module(moduleId);
        if (module == null) {
            err.println("usage error: module not in reactor: " + moduleId);
            return Commands.EXIT_USAGE;
        }
        Map<String, Object> result = new LinkedHashMap<>();

        long t0 = System.currentTimeMillis();
        Commands.status(engine, new PrintStream(new java.io.ByteArrayOutputStream()), true);
        Commands.plan(engine, List.of(), false, new PrintStream(new java.io.ByteArrayOutputStream()), true);
        result.put("noopMs", System.currentTimeMillis() - t0);

        // singleMethod probe
        Path probeFile = firstMainSource(engine, module);
        if (probeFile == null) {
            err.println("module has no main sources to probe: " + moduleId);
            return Commands.EXIT_USAGE;
        }
        byte[] original;
        try {
            original = Files.readAllBytes(probeFile);
            String text = new String(original, StandardCharsets.UTF_8);
            String marker = "    public String formatJavaSource";
            String anchor = findMethodAnchor(text);
            if (anchor == null) {
                err.println("cannot find a safe method anchor in " + probeFile);
                return Commands.EXIT_USAGE;
            }
            String edited = text.replaceFirst("(?m)^(" + anchor.replaceAll("([{}()\\[\\].*+?^$|\\\\])", "\\\\$1") + ")$",
                    anchor + "\n        // nop-ib bench probe");
            if (edited.equals(text)) {
                err.println("probe edit did not apply (anchor not on its own line)");
                return Commands.EXIT_USAGE;
            }
            Files.write(probeFile, edited.getBytes(StandardCharsets.UTF_8));

            long t1 = System.currentTimeMillis();
            // design 05 §十一 singleMethod: body-only edit -> compile complete
            int code = io.nop.ib.NopIbMain.run(new String[]{"compile", "--paths",
                    engine.getRepoRoot().relativize(probeFile).toString()}, out, err);
            result.put("singleMethodMs", System.currentTimeMillis() - t1);
            result.put("compileExit", code);

            // restore pristine bytes and verify
            Files.write(probeFile, original);
            result.put("restored", java.util.Arrays.equals(original, Files.readAllBytes(probeFile)));
            // fast-path recompile back to pristine products
            io.nop.ib.NopIbMain.run(new String[]{"compile", "--paths",
                    engine.getRepoRoot().relativize(probeFile).toString()}, out, err);
        } catch (Exception e) {
            err.println("bench failed: " + String.valueOf(e));
            return Commands.EXIT_FAILED;
        }

        out.println(json ? JsonOut.write(result) : result.toString());
        return Commands.EXIT_OK;
    }

    private static Path firstMainSource(IbEngine engine, io.nop.ib.model.IbModule module) {
        if (module.getSourceDir() == null)
            return null;
        Path src = engine.getRepoRoot().resolve(module.getSourceDir());
        if (!Files.isDirectory(src))
            return null;
        List<Path> files = new ArrayList<>();
        try (var stream = Files.walk(src)) {
            stream.filter(p -> p.toString().endsWith(".java")).forEach(files::add);
        } catch (Exception e) {
            return null;
        }
        return files.isEmpty() ? null : files.get(0);
    }

    /** first single-line method/constructor declaration usable as an edit anchor */
    private static String findMethodAnchor(String text) {
        for (String line : text.split("\n")) {
            String t = line.stripTrailing();
            if (t.equals(line) && !line.isBlank() && line.stripLeading().startsWith("public ")
                    && line.stripLeading().endsWith("{") && !line.contains("class ")
                    && !line.contains("interface "))
                return line;
        }
        return null;
    }
}
