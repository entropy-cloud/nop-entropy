package io.nop.jq.benchmark;

import io.nop.jq.IJsonQuery;
import io.nop.jq.JqEngine;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for the jq execution engine: compile cost and the common
 * execution shapes (field access, iteration + select, map/add pipelines,
 * string interpolation, recursive descent, tojson).
 *
 * <p>Run with:
 * mvn test -pl nop-kernel/nop-jq -Dtest=JqEngineBenchmark (via JUnit runner)
 * or: java -cp ... io.nop.jq.benchmark.JqEngineBenchmark
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class JqEngineBenchmark {

    private Map<String, Object> flat;
    private Map<String, Object> nested;
    private List<Map<String, Object>> items;
    private String itemsJson;
    private IJsonQuery compiledFieldAccess;
    private IJsonQuery compiledSelect;
    private IJsonQuery compiledMapAdd;

    @Setup
    public void setup() {
        flat = new LinkedHashMap<>();
        for (int i = 0; i < 10; i++) {
            flat.put("key" + i, i);
        }

        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("value", 42);
        inner.put("name", "deep");
        nested = new LinkedHashMap<>();
        nested.put("a", inner);

        items = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            item.put("price", 10.0 + i);
            item.put("name", "item-" + i);
            item.put("active", i % 2 == 0);
            items.add(item);
        }
        itemsJson = io.nop.core.lang.json.JsonTool.stringify(items);

        compiledFieldAccess = JqEngine.compile(".a.value");
        compiledSelect = JqEngine.compile("[.[] | select(.price > 50.0)]");
        compiledMapAdd = JqEngine.compile("map(.price) | add");
    }

    // ===== compile (parse) cost =====

    @Benchmark
    public IJsonQuery compileFieldAccess() {
        return JqEngine.compile(".a.value .b .c[0] | .[1:2]");
    }

    // ===== execution: cached programs =====

    @Benchmark
    public Object executeFieldAccess() {
        return compiledFieldAccess.applyOne(nested);
    }

    @Benchmark
    public Object executeSelect() {
        return compiledSelect.applyOne(items);
    }

    @Benchmark
    public Object executeMapAdd() {
        return compiledMapAdd.applyOne(items);
    }

    @Benchmark
    public Object executeInterpolation() {
        return JqEngine.compile("\"name: \\(.name) id: \\(.id)\"").applyOne(items.get(0));
    }

    @Benchmark
    public Object executeRecursiveDescent() {
        return JqEngine.compile("[.. | numbers]").applyOne(items);
    }

    @Benchmark
    public Object executeTojson() {
        return JqEngine.compile("tojson").applyOne(items);
    }

    @Benchmark
    public Object executeFromString() {
        return JqEngine.compile("fromjson | length").applyOne(itemsJson);
    }

    @Benchmark
    public Object executeDestructuring() {
        return JqEngine.compile(".[] as {id: $id, price: $p} | [$id, $p]").applyOne(items);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(JqEngineBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
