package io.nop.bytecode.cli;

import io.nop.bytecode.NopBytecodeException;
import io.nop.bytecode.analysis.nullflow.NullflowAnalyzer;
import io.nop.bytecode.collect.CollectInput;
import io.nop.bytecode.collect.CollectManifest;
import io.nop.bytecode.collect.ClassArtifactCollector;
import io.nop.bytecode.collect.ClassArtifact;
import io.nop.bytecode.collect.CollectResult;
import io.nop.bytecode.collect.MissingInputException;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Discovery-channel CLI (report-only — exit code 0 even when findings exist, per roadmap HC6;
 * an upgrade to a hard gate needs its own plan plus false-positive data).
 *
 * <p>Pipeline (plan 04 adjudications): collect once with the full input list (per-input collect
 * would let the collector's removal sweep erase sibling inputs' manifest entries), then read
 * bytes back per input by re-enumerating and pairing with the flat artifact list; the read-back
 * bytes are verified against {@code artifact.sha256()} (TOCTOU closure); a relativePath found in
 * more than one input is a loud failure (multi-report correctness over silent merging); findings
 * are deduplicated by (className, methodName, insnIndex, opcode, ref).
 *
 * <p>Exit codes: 0 = run completed (findings or not); 2 = usage error / missing input / corrupt
 * artifact / analysis failure — fail-fast, no partial report is printed (deliberately different
 * from nop-lint's findings=1 philosophy; report-only channels keep 0 for signal).
 */
public final class NopBytecodeMain {

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /**
     * Default jar-opener factory (plan 2564 W-14, adjudication D / R1-B1): NopBytecodeMain is
     * public final with a full-static pipeline, so a package-private overridable factory method
     * is not implementable — the seam is instead an injected {@code Function<Path, JarFile>}
     * taken by the {@link #run(String[], PrintStream, PrintStream, Function)} overload; the
     * existing {@link #run(String[], PrintStream, PrintStream)} delegates to this default.
     */
    private static Function<Path, JarFile> defaultJarOpener() {
        return path -> {
            try {
                return new JarFile(path.toFile());
            } catch (IOException e) {
                throw new NopBytecodeException("Failed to open jar " + path + ": " + e.getMessage(), e);
            }
        };
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        return run(args, out, err, defaultJarOpener());
    }

    /**
     * Run with an injected jar-opener (test seam, plan 2564 W-14): the read-back phase opens
     * each jar input at most once through {@code jarOpener} (per-input single open, mirroring
     * {@code ClassArtifactCollector#collectJar}).
     */
    public static int run(String[] args, PrintStream out, PrintStream err,
                          Function<Path, JarFile> jarOpener) {
        List<Path> inputs = new ArrayList<>();
        Path manifestFile = null;
        boolean json = false;
        long maxRuntimeSeconds = 600;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--json" -> json = true;
                case "--manifest" -> {
                    if (i + 1 >= args.length) {
                        err.println("usage error: --manifest requires a path");
                        return 2;
                    }
                    manifestFile = Path.of(args[++i]);
                }
                case "--max-runtime-seconds" -> {
                    if (i + 1 >= args.length) {
                        err.println("usage error: --max-runtime-seconds requires a value");
                        return 2;
                    }
                    try {
                        maxRuntimeSeconds = Long.parseLong(args[++i]);
                    } catch (NumberFormatException e) {
                        err.println("usage error: --max-runtime-seconds expects an integer: " + args[i]);
                        return 2;
                    }
                    if (maxRuntimeSeconds < 0) {
                        err.println("usage error: --max-runtime-seconds must be >= 0");
                        return 2;
                    }
                }
                default -> {
                    if (a.startsWith("--")) {
                        err.println("usage error: unknown flag " + a);
                        return 2;
                    }
                    inputs.add(Path.of(a));
                }
            }
        }
        if (inputs.isEmpty()) {
            err.println("usage error: at least one input (class directory or jar) is required");
            return 2;
        }

        List<CollectInput> collectInputs = new ArrayList<>();
        for (Path p : inputs) {
            if (Files.isDirectory(p)) {
                collectInputs.add(CollectInput.directory(p));
            } else if (p.getFileName() != null && p.getFileName().toString().endsWith(".jar")) {
                collectInputs.add(CollectInput.jar(p));
            } else {
                err.println("usage error: input is neither a directory nor a .jar file: " + p);
                return 2;
            }
        }

        try {
            CollectManifest manifest = manifestFile == null ? new CollectManifest()
                    : CollectManifest.load(manifestFile);
            ClassArtifactCollector collector = new ClassArtifactCollector();
            CollectResult result = collector.collect(collectInputs, manifest);

            List<ClassArtifact> flat = result.artifacts();
            checkNoDuplicateRelativePaths(flat);
            List<ClassArtifact> effective = preferBaseOverMultiRelease(flat);

            long deadlineNanos = maxRuntimeSeconds == 0 ? System.nanoTime()
                    : (maxRuntimeSeconds == Long.MAX_VALUE ? 0
                        : System.nanoTime() + maxRuntimeSeconds * 1_000_000_000L);

            List<Finding> findings = new ArrayList<>();
            Set<String> findingKeys = new HashSet<>();
            for (CollectInput input : collectInputs) {
                Map<String, ClassArtifact> byRelative = artifactsOfInput(input, flat);
                // W-14: per-input single open — JAR inputs go through the injected opener once,
                // DIRECTORY inputs resolve per file (already minimal); closed after the
                // artifact inner loop via try-with-resources (R1-m5)
                try (ClassByteSource source = openByteSource(input, jarOpener)) {
                    for (Map.Entry<String, ClassArtifact> e : byRelative.entrySet()) {
                        byte[] bytes = readBytes(source, e.getKey(), e.getValue());
                    NullflowAnalyzer nullflow = new NullflowAnalyzer();
                    nullflow.setDeadlineNanos(deadlineNanos);
                    for (io.nop.bytecode.analysis.nullflow.DerefFinding d : nullflow.analyze(bytes)) {
                        Finding f = new Finding(RuleIds.MAY_NULL_DEREF, Finding.SEVERITY_WARNING,
                                "Value may be null on some path at this dereference",
                                d.className(), d.methodName(), d.insnIndex(), d.ref());
                        // dedup key per plan 04: same class re-reached through multiple inputs yields one finding
                        if (findingKeys.add(f.className() + "#" + f.methodName() + "@" + f.insnIndex()
                                + "|" + f.ref())) {
                            findings.add(f);
                        }
                    }
                    io.nop.bytecode.analysis.resources.ResourceLeakAnalyzer resourceAnalyzer =
                            new io.nop.bytecode.analysis.resources.ResourceLeakAnalyzer();
                    resourceAnalyzer.setDeadlineNanos(deadlineNanos);
                    for (io.nop.bytecode.analysis.resources.UnclosedResourceFinding d
                            : resourceAnalyzer.analyze(bytes)) {
                        Finding f = new Finding(RuleIds.UNCLOSED_RESOURCE, Finding.SEVERITY_WARNING,
                                "Resource acquired here is not closed on some method-exit path",
                                d.className(), d.methodName(), d.insnIndex(), d.ref());
                        if (findingKeys.add(f.className() + "#" + f.methodName() + "@" + f.insnIndex()
                                + "|" + f.ref())) {
                            findings.add(f);
                        }
                    }
                    }
                }
            }
            if (manifestFile != null) {
                manifest.save(manifestFile);
            }

            if (json) {
                FindingRenderer.renderJson(findings, out);
            } else {
                FindingRenderer.renderConsole(findings, out);
            }
            return 0;
        } catch (NopBytecodeException | IOException | java.io.UncheckedIOException e) {
            // MissingInputException is a NopBytecodeException subtype; both are loud failures (exit 2)
            err.println("error: " + e);
            e.printStackTrace(err);
            return 2;
        } catch (RuntimeException e) {
            // belt-and-suspenders: analysis-layer failures must be exit 2 ("analysis failure"),
            // never exit 1 (nop-lint's "violations" code — a different contract)
            err.println("error: analysis failed: " + e);
            e.printStackTrace(err);
            return 2;
        }
    }

    /** Group the flat artifact list by input; artifacts whose relativePath an input cannot produce are a loud failure. */
    private static Map<String, ClassArtifact> artifactsOfInput(CollectInput input, List<ClassArtifact> flat) {
        Map<String, ClassArtifact> out = new LinkedHashMap<>();
        if (input.kind() == CollectInput.Kind.DIRECTORY) {
            Set<String> present = new HashSet<>();
            listRelativePaths(input.path(), present);
            for (ClassArtifact a : flat) {
                if (!a.relativePath().contains("!") && present.contains(a.relativePath())) {
                    out.put(a.relativePath(), a);
                }
            }
        } else {
            String jarName = input.path().getFileName().toString();
            String prefix = jarName + "!";
            for (ClassArtifact a : flat) {
                if (a.relativePath().startsWith(prefix)) {
                    out.put(a.relativePath().substring(prefix.length()), a);
                }
            }
        }
        return out;
    }

    private static void listRelativePaths(Path root, Set<String> out) {
        try (var walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".class"))
                .forEach(p -> out.add(root.relativize(p).toString().replace('\\', '/')));
        } catch (IOException e) {
            throw new NopBytecodeException("Failed to walk " + root + " for byte read-back: " + e.getMessage(), e);
        }
    }

    /**
     * Multi-release jar adjudication (plan 04 / plan-02 routed decision): when the same class
     * appears both as a base entry and under META-INF/versions/, the base entry wins (non-
     * versions relativePath preferred); ties (both versions-style) keep the first sorted.
     */
    static List<ClassArtifact> preferBaseOverMultiRelease(List<ClassArtifact> flat) {
        Map<String, ClassArtifact> byClass = new LinkedHashMap<>();
        for (ClassArtifact a : flat) {
            byClass.merge(a.className(), a, (keep, cand) ->
                    cand.relativePath().startsWith("META-INF/versions/") ? keep : cand);
        }
        return List.copyOf(byClass.values());
    }

    private static void checkNoDuplicateRelativePaths(List<ClassArtifact> flat) {
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (ClassArtifact a : flat) {
            seen.merge(a.relativePath(), 1, Integer::sum);
        }
        List<String> conflicts = seen.entrySet().stream()
                .filter(e -> e.getValue() > 1).map(Map.Entry::getKey).toList();
        if (!conflicts.isEmpty()) {
            throw new NopBytecodeException("Duplicate relativePath across inputs (same class from multiple inputs is ambiguous): " + conflicts);
        }
    }

    /**
     * Per-input byte source (plan 2564 W-14): JAR inputs open the underlying JarFile exactly
     * once via the injected opener; DIRECTORY inputs resolve relative paths on the filesystem
     * (per-file single open is already minimal — the DIRECTORY branch is a straight port).
     */
    private static ClassByteSource openByteSource(CollectInput input, Function<Path, JarFile> jarOpener) {
        if (input.kind() == CollectInput.Kind.DIRECTORY) {
            Path root = input.path();
            return new ClassByteSource() {
                @Override
                public byte[] read(String relative) {
                    try {
                        return Files.readAllBytes(root.resolve(relative));
                    } catch (IOException e) {
                        throw new NopBytecodeException("Failed to read back " + relative + ": " + e.getMessage(), e);
                    }
                }

                @Override
                public void close() {
                    // no underlying container to release for directory inputs
                }
            };
        }
        JarFile jar = jarOpener.apply(input.path());
        return new ClassByteSource() {
            @Override
            public byte[] read(String relative) {
                JarEntry entry = jar.getJarEntry(relative);
                if (entry == null) {
                    throw new NopBytecodeException("Jar entry disappeared at read-back: " + relative);
                }
                try (InputStream in = jar.getInputStream(entry)) {
                    return in.readAllBytes();
                } catch (IOException e) {
                    throw new NopBytecodeException("Failed to read back jar entry " + relative + ": " + e.getMessage(), e);
                }
            }

            @Override
            public void close() {
                try {
                    jar.close();
                } catch (IOException e) {
                    throw new NopBytecodeException("Failed to close jar " + input.path() + ": " + e.getMessage(), e);
                }
            }
        };
    }

    private static byte[] readBytes(ClassByteSource source, String relative, ClassArtifact artifact) {
        byte[] bytes = source.read(relative);
        String actual = ClassArtifactCollector.sha256Hex(bytes);
        if (!actual.equals(artifact.sha256())) {
            throw new NopBytecodeException("Content hash changed between collect and read-back (TOCTOU): " + relative);
        }
        return bytes;
    }
}
