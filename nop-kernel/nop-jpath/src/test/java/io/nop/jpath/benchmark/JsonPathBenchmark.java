package io.nop.jpath.benchmark;

import io.nop.jpath.NopJsonPath;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks comparing nop-jq JsonPath performance against Jayway JsonPath.
 * Run with: mvn test-compile exec:java -Dexec.mainClass="io.nop.jpath.benchmark.JsonPathBenchmark" -pl nop-kernel/nop-jpath
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class JsonPathBenchmark {

    private Map<String, Object> data;
    private String dataJson;

    @Setup
    public void setup() {
        Map<String, Object> book1 = new LinkedHashMap<>();
        book1.put("title", "Sayings of the Century");
        book1.put("author", "Nigel Rees");
        book1.put("price", 8.95);
        book1.put("category", "reference");

        Map<String, Object> book2 = new LinkedHashMap<>();
        book2.put("title", "Sword of Honour");
        book2.put("author", "Evelyn Waugh");
        book2.put("price", 12.99);
        book2.put("category", "fiction");

        Map<String, Object> book3 = new LinkedHashMap<>();
        book3.put("title", "Moby Dick");
        book3.put("author", "Herman Melville");
        book3.put("price", 8.99);
        book3.put("category", "fiction");

        Map<String, Object> book4 = new LinkedHashMap<>();
        book4.put("title", "The Lord of the Rings");
        book4.put("author", "J. R. R. Tolkien");
        book4.put("price", 22.99);
        book4.put("category", "fiction");

        Map<String, Object> store = new LinkedHashMap<>();
        store.put("book", Arrays.asList(book1, book2, book3, book4));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("store", store);
        root.put("expensive", 10);

        this.data = root;
        this.dataJson = "{\"store\":{\"book\":[{\"title\":\"Sayings of the Century\",\"author\":\"Nigel Rees\",\"price\":8.95,\"category\":\"reference\"},{\"title\":\"Sword of Honour\",\"author\":\"Evelyn Waugh\",\"price\":12.99,\"category\":\"fiction\"},{\"title\":\"Moby Dick\",\"author\":\"Herman Melville\",\"price\":8.99,\"category\":\"fiction\"},{\"title\":\"The Lord of the Rings\",\"author\":\"J. R. R. Tolkien\",\"price\":22.99,\"category\":\"fiction\"}]},\"expensive\":10}";
    }

    // ========== nop-jq benchmarks ==========

    @Benchmark
    public Object nopjq_simpleProperty() {
        return NopJsonPath.eval(data, "$.expensive");
    }

    @Benchmark
    public Object nopjq_nestedProperty() {
        return NopJsonPath.eval(data, "$.store.book[0].title");
    }

    @Benchmark
    public Object nopjq_wildcardArray() {
        return NopJsonPath.eval(data, "$.store.book[*].title");
    }

    @Benchmark
    public Object nopjq_filterEquals() {
        return NopJsonPath.eval(data, "$.store.book[?(@.category == 'fiction')].title");
    }

    @Benchmark
    public Object nopjq_filterGreaterThan() {
        return NopJsonPath.eval(data, "$.store.book[?(@.price > 10)].title");
    }

    @Benchmark
    public Object nopjq_deepScan() {
        return NopJsonPath.eval(data, "$..author");
    }

    @Benchmark
    public Object nopjq_compiledPath() {
        return NopJsonPath.compile("$.store.book[0].title").eval(data);
    }

    @Benchmark
    public Object nopjq_readFromJson() {
        return NopJsonPath.read(dataJson, "$.store.book[0].title");
    }

    // ========== Jayway JsonPath benchmarks ==========

    @Benchmark
    public Object jayway_simpleProperty() {
        return com.jayway.jsonpath.JsonPath.read(data, "$.expensive");
    }

    @Benchmark
    public Object jayway_nestedProperty() {
        return com.jayway.jsonpath.JsonPath.read(data, "$.store.book[0].title");
    }

    @Benchmark
    public Object jayway_wildcardArray() {
        return com.jayway.jsonpath.JsonPath.read(data, "$.store.book[*].title");
    }

    @Benchmark
    public Object jayway_filterEquals() {
        return com.jayway.jsonpath.JsonPath.read(data, "$.store.book[?(@.category == 'fiction')].title");
    }

    @Benchmark
    public Object jayway_filterGreaterThan() {
        return com.jayway.jsonpath.JsonPath.read(data, "$.store.book[?(@.price > 10)].title");
    }

    @Benchmark
    public Object jayway_deepScan() {
        return com.jayway.jsonpath.JsonPath.read(data, "$..author");
    }

    @Benchmark
    public Object jayway_compiledPath() {
        return com.jayway.jsonpath.JsonPath.compile("$.store.book[0].title").read(data);
    }

    @Benchmark
    public Object jayway_readFromJson() {
        return com.jayway.jsonpath.JsonPath.read(dataJson, "$.store.book[0].title");
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(JsonPathBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
