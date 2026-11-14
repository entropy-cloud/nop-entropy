package io.nop.ib.junit;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fork-side JUnit Platform runner for nop-ib (design 02-test-execution: per-module
 * forked JVM, cwd = module basedir, snapshot recording explicitly off).
 *
 * <p>Runs ONLY on the module's own JUnit artifacts plus the matching
 * junit-platform-launcher jar chosen by the parent — this class has zero
 * dependencies on nop-ib internals so the fork classpath stays clean.
 *
 * <p>Usage: java IbJUnitRunner &lt;resultJsonPath&gt; &lt;testClassName...&gt;
 * Writes a JSON result document and exits 0 (green) / 1 (failures) / 2 (a
 * requested class could not be discovered).
 */
public class IbJUnitRunner {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: IbJUnitRunner <resultJsonPath> <testClass...>");
            System.exit(3);
        }
        Path resultFile = Path.of(args[0]);
        List<String> classes = new ArrayList<>();
        for (int i = 1; i < args.length; i++)
            classes.add(args[i]);

        LauncherDiscoveryRequestBuilder builder = LauncherDiscoveryRequestBuilder.request();
        for (String cls : classes)
            builder.selectors(DiscoverySelectors.selectClass(cls));

        Launcher launcher = LauncherFactory.create();
        TestPlan plan = launcher.discover(builder.build());

        List<String> discovered = new ArrayList<>();
        plan.getRoots().forEach(root -> collectClasses(plan, root, discovered));
        List<String> missing = new ArrayList<>();
        for (String cls : classes) {
            boolean found = discovered.stream()
                    .anyMatch(d -> d.equals(cls) || d.startsWith(cls + "$") || cls.startsWith(d + "$"));
            if (!found)
                missing.add(cls);
        }

        long[] counts = new long[3]; // tests, failures, skipped
        List<Map<String, String>> failures = new ArrayList<>();
        long start = System.currentTimeMillis();
        launcher.execute(builder.build(), new TestExecutionListener() {
            @Override
            public void executionFinished(TestIdentifier id, TestExecutionResult r) {
                if (!id.isTest())
                    return;
                synchronized (counts) {
                    counts[0]++;
                    if (r.getStatus() == TestExecutionResult.Status.FAILED) {
                        counts[1]++;
                        Throwable t = r.getThrowable().orElse(null);
                        Map<String, String> f = new LinkedHashMap<>();
                        f.put("class", parentClassOf(id));
                        f.put("method", id.getDisplayName());
                        f.put("type", t == null ? "java.lang.AssertionError" : t.getClass().getName());
                        f.put("message", t == null ? "" : String.valueOf(t.getMessage()));
                        failures.add(f);
                    } else if (r.getStatus() == TestExecutionResult.Status.ABORTED) {
                        counts[2]++;
                    }
                }
            }
        });
        long elapsed = System.currentTimeMillis() - start;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tests", counts[0]);
        result.put("failures", counts[1]);
        result.put("skipped", counts[2]);
        result.put("timeMs", elapsed);
        result.put("failed", failures);
        result.put("missingClasses", missing);

        Files.writeString(resultFile, toJson(result), StandardCharsets.UTF_8);
        System.exit(!missing.isEmpty() ? 2 : (counts[1] > 0 ? 1 : 0));
    }

    private static void collectClasses(TestPlan plan, TestIdentifier root, List<String> out) {
        String uniqueId = root.getUniqueId().toString();
        if (uniqueId.contains("[class:")) {
            String name = classFromUniqueId(uniqueId);
            if (!name.isBlank() && !out.contains(name))
                out.add(name);
        }
        for (TestIdentifier child : plan.getChildren(root))
            collectClasses(plan, child, out);
    }

    /** class name parsed from the test identifier's own unique id
     *  ([engine:junit-jupiter]/[class:pkg.Cls]/[method:...]) */
    private static String parentClassOf(TestIdentifier id) {
        return classFromUniqueId(id.getUniqueId().toString());
    }

    private static String classFromUniqueId(String uid) {
        int idx = uid.lastIndexOf("[class:");
        if (idx < 0)
            return "";
        String name = uid.substring(idx + 7);
        int close = name.indexOf(']');
        if (close >= 0)
            name = name.substring(0, close);
        // drop parameter/instantiation suffixes after a '/' separator
        int slash = name.indexOf('/');
        if (slash >= 0)
            name = name.substring(0, slash);
        return name;
    }

    static String toJson(Map<String, Object> m) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            if (!first)
                sb.append(',');
            first = false;
            esc(sb, e.getKey()).append(':');
            writeVal(sb, e.getValue());
        }
        return sb.append('}').toString();
    }

    private static void writeVal(StringBuilder sb, Object v) {
        if (v instanceof Number || v instanceof Boolean) {
            sb.append(v);
        } else if (v instanceof Map<?, ?> map) {
            sb.append('{');
            boolean f = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!f)
                    sb.append(',');
                f = false;
                esc(sb, String.valueOf(e.getKey())).append(':');
                writeVal(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof Iterable<?> iter) {
            sb.append('[');
            boolean f = true;
            for (Object o : iter) {
                if (!f)
                    sb.append(',');
                f = false;
                writeVal(sb, o);
            }
            sb.append(']');
        } else {
            esc(sb, String.valueOf(v));
        }
    }

    private static StringBuilder esc(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20)
                        sb.append(String.format("\\u%04x", (int) c));
                    else
                        sb.append(c);
                }
            }
        }
        return sb.append('"');
    }
}
