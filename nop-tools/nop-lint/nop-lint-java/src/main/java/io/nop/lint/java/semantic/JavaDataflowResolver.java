package io.nop.lint.java.semantic;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import io.nop.lint.core.NopLintException;
import io.nop.lint.core.semantic.DataflowResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The Java {@link DataflowResolver} (roadmap item 34): parses the named file
 * (LRU-cached, plain parse — no symbol solver needed) and answers through
 * the {@link DataflowQueries} position adapter. The query position must sit
 * on a local/parameter declaration's name; fields and method-outside
 * declarations throw (the v1 not-answered surface, plan Decision 3).
 * Registered through ServiceLoader.
 */
public class JavaDataflowResolver implements DataflowResolver {

    private static final int CACHE_SIZE = 32;

    private final JavaParser parser = new JavaParser(new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17));
    // keyed by FILE PATH (plan 12 audit M7): the former `filePath + "@" + line`
    // key re-read and re-parsed the same file for every queried position; one
    // parsed unit per file serves all positions through per-query location
    private final Map<String, CachedUnit> unitsByPath = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CachedUnit> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String constantValue(String filePath, int line, int col) {
        DataflowQueries queries = new DataflowQueries();
        MethodDeclaration method = methodFor(filePath, line, col, queries);
        int[] at = javaParserPosition(line, col);
        return queries.constantValue(method, at[0], at[1]);
    }

    @Override
    public long useCount(String filePath, int line, int col) {
        DataflowQueries queries = new DataflowQueries();
        MethodDeclaration method = methodFor(filePath, line, col, queries);
        int[] at = javaParserPosition(line, col);
        return queries.useCount(method, at[0], at[1]);
    }

    @Override
    public boolean isSelfAssigned(String filePath, int line, int col) {
        DataflowQueries queries = new DataflowQueries();
        MethodDeclaration method = methodFor(filePath, line, col, queries);
        int[] at = javaParserPosition(line, col);
        return queries.isSelfAssigned(method, at[0], at[1]);
    }

    /**
     * Engine 0-based line / UTF-16 column → JavaParser 1-based (line,
     * column) — the {@code JavaTypeResolver} convention.
     */
    private static int[] javaParserPosition(int line, int col) {
        if (line < 0 || col < 0) {
            throw new NopLintException("negative position " + line + ":" + col
                    + " (a dataflow query must carry a real position)");
        }
        return new int[]{line + 1, col + 1};
    }

    /**
     * The parsed unit of one file — keyed by PATH (plan 12 audit M7): the
     * former `filePath + "@" + line` key re-read and re-parsed the same file
     * for every queried position.
     */
    private synchronized CachedUnit unitFor(String filePath) {
        return unitsByPath.computeIfAbsent(filePath, path -> {
            try {
                byte[] source = Files.readAllBytes(Path.of(path));
                CompilationUnit unit = parser.parse(new String(source, StandardCharsets.UTF_8))
                        .getResult()
                        .orElseThrow(() -> new NopLintException("the source of '" + path
                                + "' did not parse into a compilation unit (dataflow"
                                + " unavailable)"));
                return new CachedUnit(unit);
            } catch (IOException e) {
                throw new NopLintException("failed to read '" + path
                        + "' for dataflow: " + e.getMessage(), e);
            }
        });
    }

    /**
     * The enclosing method of the queried position: the dataflow chains are
     * method-shaped (item 30's v1 surface), so the adapter resolves the
     * method per query (cheap parent walk over the cached unit) and lets
     * {@link DataflowQueries} fail closed when the position is not a
     * local/parameter declaration name.
     */
    private MethodDeclaration methodFor(String filePath, int line, int col,
                                        DataflowQueries queries) {
        CompilationUnit unit = unitFor(filePath).unit();
        Node node = minimalContaining(unit, line, col);
        Node walker = node;
        while (walker != null) {
            if (walker instanceof MethodDeclaration found) {
                return found;
            }
            walker = walker.getParentNode().orElse(null);
        }
        throw new NopLintException("no method at " + line + ":" + col + " in '"
                + filePath + "' (the dataflow surface is method-local)");
    }

    /**
     * The deepest node whose range contains the 1-based position (the
     * JavaParser convention this resolver converts to before querying).
     */
    private static Node minimalContaining(Node root, int line, int column) {
        Node deepest = root;
        boolean descended = true;
        while (descended) {
            descended = false;
            for (Node child : deepest.getChildNodes()) {
                if (child.getRange().isPresent() && contains1Based(child.getRange().get(), line, column)) {
                    deepest = child;
                    descended = true;
                    break;
                }
            }
        }
        return deepest;
    }

    private static boolean contains1Based(com.github.javaparser.Range range, int line, int column) {
        if (line < range.begin.line || line > range.end.line) {
            return false;
        }
        if (line == range.begin.line && column < range.begin.column) {
            return false;
        }
        return !(line == range.end.line && column > range.end.column);
    }

    private record CachedUnit(CompilationUnit unit) {
    }

}
