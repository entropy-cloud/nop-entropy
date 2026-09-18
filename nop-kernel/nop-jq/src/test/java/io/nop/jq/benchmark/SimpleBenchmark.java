package io.nop.jq.benchmark;

import io.nop.jq.jsonpath.NopJsonPath;

import java.util.*;

/**
 * Simple performance comparison between nop-jq and Jayway JsonPath.
 * Run with: java -cp ... io.nop.jq.benchmark.SimpleBenchmark
 */
public class SimpleBenchmark {

    private static final int WARMUP = 5000;
    private static final int ITERATIONS = 10000;

    public static void main(String[] args) {
        Map<String, Object> data = buildData();
        String json = "{\"store\":{\"book\":[{\"title\":\"Book1\",\"price\":10},{\"title\":\"Book2\",\"price\":20},{\"title\":\"Book3\",\"price\":30},{\"title\":\"Book4\",\"price\":15}]}}";

        System.out.println("=== nop-jq vs Jayway JsonPath Performance Comparison ===");
        System.out.println("Warmup: " + WARMUP + " iterations, Measurement: " + ITERATIONS + " iterations\n");

        // Benchmark 1: Simple property access
        benchmark("Simple property: $.expensive",
                () -> NopJsonPath.eval(data, "$.expensive"),
                () -> com.jayway.jsonpath.JsonPath.read(data, "$.expensive"));

        // Benchmark 2: Nested property access
        benchmark("Nested property: $.store.book[0].title",
                () -> NopJsonPath.eval(data, "$.store.book[0].title"),
                () -> com.jayway.jsonpath.JsonPath.read(data, "$.store.book[0].title"));

        // Benchmark 3: Wildcard array
        benchmark("Wildcard: $.store.book[*].title",
                () -> NopJsonPath.eval(data, "$.store.book[*].title"),
                () -> com.jayway.jsonpath.JsonPath.read(data, "$.store.book[*].title"));

        // Benchmark 4: Filter
        benchmark("Filter: $.store.book[?(@.price > 15)].title",
                () -> NopJsonPath.eval(data, "$.store.book[?(@.price > 15)].title"),
                () -> com.jayway.jsonpath.JsonPath.read(data, "$.store.book[?(@.price > 15)].title"));

        // Benchmark 5: Compiled path
        benchmark("Compiled: $.store.book[0].title",
                () -> { var c = NopJsonPath.compile("$.store.book[0].title"); c.eval(data); },
                () -> { var c = com.jayway.jsonpath.JsonPath.compile("$.store.book[0].title"); c.read(data); });

        // Benchmark 6: Parse from JSON string
        benchmark("Parse+Query: $.store.book[0].title",
                () -> NopJsonPath.read(json, "$.store.book[0].title"),
                () -> com.jayway.jsonpath.JsonPath.read(json, "$.store.book[0].title"));

        System.out.println("\n=== Done ===");
    }

    private static void benchmark(String name, Runnable nopjq, Runnable jayway) {
        // Warmup
        for (int i = 0; i < WARMUP; i++) {
            nopjq.run();
            jayway.run();
        }

        // Measure nop-jq
        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            nopjq.run();
        }
        long nopjqTime = System.nanoTime() - start;

        // Measure Jayway
        start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            jayway.run();
        }
        long jaywayTime = System.nanoTime() - start;

        double nopjqAvg = (double) nopjqTime / ITERATIONS;
        double jaywayAvg = (double) jaywayTime / ITERATIONS;
        double ratio = jaywayAvg / nopjqAvg;

        System.out.printf("%-50s nop-jq: %7.0f ns/op  Jayway: %7.0f ns/op  ratio: %.2fx%n",
                name, nopjqAvg, jaywayAvg, ratio);
    }

    private static Map<String, Object> buildData() {
        Map<String, Object> book1 = new LinkedHashMap<>();
        book1.put("title", "Sayings of the Century");
        book1.put("author", "Nigel Rees");
        book1.put("price", 8.95);

        Map<String, Object> book2 = new LinkedHashMap<>();
        book2.put("title", "Sword of Honour");
        book2.put("author", "Evelyn Waugh");
        book2.put("price", 12.99);

        Map<String, Object> book3 = new LinkedHashMap<>();
        book3.put("title", "Moby Dick");
        book3.put("author", "Herman Melville");
        book3.put("price", 8.99);

        Map<String, Object> book4 = new LinkedHashMap<>();
        book4.put("title", "The Lord of the Rings");
        book4.put("author", "J. R. R. Tolkien");
        book4.put("price", 22.99);

        Map<String, Object> store = new LinkedHashMap<>();
        store.put("book", Arrays.asList(book1, book2, book3, book4));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("store", store);
        root.put("expensive", 10);
        return root;
    }
}
