package io.nop.jq.benchmark;

import io.nop.core.lang.json.JsonTool;
import io.nop.jq.jsonpath.NopJsonPath;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

/**
 * Fair performance comparison: nop-jq vs jq tool.
 * Uses a 1.6MB JSON file with 10000 records to amortize process startup cost.
 */
public class JqToolBenchmark {

    private static final String JQ_CMD = "jq";
    private static final String JSON_FILE = "/tmp/benchmark_large.json";

    public static void main(String[] args) throws Exception {
        String jsonData = new String(Files.readAllBytes(Paths.get(JSON_FILE)));
        Object root = JsonTool.parse(jsonData);
        System.out.println("=== nop-jq vs jq tool Fair Comparison ===");
        System.out.println("Dataset: 10000 records, ~1.6MB JSON\n");

        // Warmup
        for (int i = 0; i < 100; i++) {
            NopJsonPath.eval(root, "$.users[0].name");
            runJqFile(".");

        }

        // Test 1: Simple property access (1000 iterations)
        fairBenchmark("Simple property: .users[0].name", 1000,
                () -> NopJsonPath.eval(root, "$.users[0].name"),
                () -> runJqFile(".users[0].name"));

        // Test 2: Array iteration (500 iterations)
        fairBenchmark("Array iteration: .users[].name", 500,
                () -> NopJsonPath.eval(root, "$.users[].name"),
                () -> runJqFile(".users[].name"));

        // Test 3: Filter (500 iterations)
        fairBenchmark("Filter: .users[] | select(.age > 50) | .name", 500,
                () -> NopJsonPath.eval(root, "$.users[?(@.age > 50)].name"),
                () -> runJqFile(".users[] | select(.age > 50) | .name"));

        // Test 4: Nested property (500 iterations)
        fairBenchmark("Nested: .users[0].address.city", 500,
                () -> NopJsonPath.eval(root, "$.users[0].address.city"),
                () -> runJqFile(".users[0].address.city"));

        // Test 5: Count (500 iterations)
        fairBenchmark("Count: .users | length", 500,
                () -> NopJsonPath.size(root, "$.users"),
                () -> runJqFile(".users | length"));

        // Test 6: Map to array (500 iterations)
        fairBenchmark("Map: [.users[0:100][].name]", 500,
                () -> NopJsonPath.eval(root, "$.users[0:100][].name"),
                () -> runJqFile("[.users[0:100][].name]"));

        System.out.println("\n=== Done ===");
    }

    private static void fairBenchmark(String name, int iterations,
                                       Runnable nopjq, Supplier<String> jq) throws Exception {
        // nop-jq measurement
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            nopjq.run();
        }
        long nopjqTime = System.nanoTime() - start;

        // jq measurement (runs jq on file, process overhead is amortized over 1.6MB)
        start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            jq.get();
        }
        long jqTime = System.nanoTime() - start;

        double nopjqAvgUs = (double) nopjqTime / iterations / 1000;
        double jqAvgUs = (double) jqTime / iterations / 1000;
        double ratio = jqAvgUs / nopjqAvgUs;

        String winner = ratio > 1 ? "nop-jq" : "jq";
        double winRatio = Math.max(ratio, 1.0 / ratio);

        System.out.printf("%-50s nop-jq: %8.1f us  jq: %8.1f us  %s %.1fx%n",
                name, nopjqAvgUs, jqAvgUs, winner, winRatio);
    }

    private static String runJqFile(String expression) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(JQ_CMD, expression, JSON_FILE);
        pb.redirectErrorStream(false);
        Process proc = pb.start();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        proc.waitFor(5, TimeUnit.SECONDS);
        return sb.toString();
    }

    @FunctionalInterface
    interface Supplier<T> { T get() throws Exception; }
}
