package io.nop.jpath;

import io.nop.commons.cache.LocalCache;

import java.util.List;

import static io.nop.commons.cache.CacheConfig.newConfig;

/**
 * Compiles JsonPath strings into Segment[] arrays with caching.
 */
public class JsonPathCompiler {
    private static final int DEFAULT_CACHE_SIZE = 1000;

    private final LocalCache<String, List<Segment>> cache;

    public JsonPathCompiler() {
        this(DEFAULT_CACHE_SIZE);
    }

    public JsonPathCompiler(int cacheSize) {
        this.cache = LocalCache.newCache("nop-jq-jsonpath-compile",
                newConfig(cacheSize), this::doCompile);
    }

    public List<Segment> compile(String path) {
        return cache.get(path);
    }

    private List<Segment> doCompile(String path) {
        JsonPathParser parser = new JsonPathParser(path);
        List<Segment> segments = parser.parse();
        return SegmentOptimizer.optimize(segments);
    }
}
