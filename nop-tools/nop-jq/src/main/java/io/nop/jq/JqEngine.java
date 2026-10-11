package io.nop.jq;

import io.nop.jq.ast.JqAstNode;
import io.nop.commons.cache.LocalCache;

import java.util.List;

import static io.nop.commons.cache.CacheConfig.newConfig;

/**
 * Facade for jq operations. Compiles jq expressions and returns executable queries.
 * Uses AST-based direct execution.
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
        List<JqToken> tokens = lexer.tokenize();
        JqParser parser = new JqParser(tokens);
        JqAstNode ast = parser.parse();
        return new JqDirectQuery(expr, ast);
    }
}
