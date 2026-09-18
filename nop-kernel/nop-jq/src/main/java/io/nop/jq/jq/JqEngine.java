package io.nop.jq.jq;

import io.nop.commons.cache.LocalCache;

import static io.nop.commons.cache.CacheConfig.newConfig;

/**
 * Facade for jq operations. Provides compile and apply methods.
 * <p>
 * Usage:
 * <pre>
 *   IJsonQuery query = JqEngine.compile(".foo | select(. > 10)");
 *   List&lt;Object&gt; results = query.apply(data);
 * </pre>
 */
public class JqEngine {
    private static final int DEFAULT_CACHE_SIZE = 1000;
    private static final LocalCache<String, IJsonQuery> cache =
            LocalCache.newCache("nop-jq-compile", newConfig(DEFAULT_CACHE_SIZE), JqEngine::doCompile);

    /**
     * Compile a jq expression into a reusable query object.
     */
    public static IJsonQuery compile(String expr) {
        return cache.get(expr);
    }

    private static IJsonQuery doCompile(String expr) {
        JqLexer lexer = new JqLexer(expr);
        java.util.List<JqToken> tokens = lexer.tokenize();
        JqParser parser = new JqParser(tokens);
        String xlangExpr = parser.parse();
        return new JqCompiledQuery(expr, xlangExpr);
    }
}
