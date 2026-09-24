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

    private final DataflowQueries queries = new DataflowQueries();
    private final JavaParser parser = new JavaParser(new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17));
    private final Map<String, CachedMethod> methodsByPath = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CachedMethod> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String constantValue(String filePath, int line, int col) {
        CachedMethod cached = methodFor(filePath, line, col);
        int[] at = javaParserPosition(line, col);
        return cached.queries().constantValue(cached.method(), at[0], at[1]);
    }

    @Override
    public long useCount(String filePath, int line, int col) {
        CachedMethod cached = methodFor(filePath, line, col);
        int[] at = javaParserPosition(line, col);
        return cached.queries().useCount(cached.method(), at[0], at[1]);
    }

    @Override
    public boolean isSelfAssigned(String filePath, int line, int col) {
        CachedMethod cached = methodFor(filePath, line, col);
        int[] at = javaParserPosition(line, col);
        return cached.queries().isSelfAssigned(cached.method(), at[0], at[1]);
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
     * The enclosing method of the queried position: the dataflow chains are
     * method-shaped (item 30's v1 surface), so the adapter resolves the
     * method first and lets {@link DataflowQueries} fail closed when the
     * position is not a local/parameter declaration name.
     */
    private synchronized CachedMethod methodFor(String filePath, int line, int col) {
        return methodsByPath.computeIfAbsent(filePath + "@" + line, key -> {
            try {
                byte[] source = Files.readAllBytes(Path.of(filePath));
                CompilationUnit unit = parser.parse(new String(source, StandardCharsets.UTF_8))
                        .getResult()
                        .orElseThrow(() -> new NopLintException("the source of '" + filePath
                                + "' did not parse into a compilation unit (dataflow"
                                + " unavailable)"));
                LineColBytes lineCols = new LineColBytes(source);
                int bytePos = lineCols.byteOf(line + 1, col + 1);
                // greedy descent to the deepest node covering the position
                // (a plain findFirst would stop at the outermost node)
                Node deepest = unit;
                boolean descended = true;
                while (descended) {
                    descended = false;
                    for (Node child : deepest.getChildNodes()) {
                        if (child.getRange().isPresent()
                                && covers(child.getRange().get(), line, col)) {
                            deepest = child;
                            descended = true;
                            break;
                        }
                    }
                }
                Node walker = deepest;
                MethodDeclaration method = null;
                while (walker != null) {
                    if (walker instanceof MethodDeclaration found) {
                        method = found;
                        break;
                    }
                    walker = walker.getParentNode().orElse(null);
                }
                if (method == null) {
                    throw new NopLintException("no method at " + line + ":" + col + " in '"
                            + filePath + "' (the dataflow surface is method-local)");
                }
                return new CachedMethod(new DataflowQueries(), method);
            } catch (IOException e) {
                throw new NopLintException("failed to read '" + filePath
                        + "' for dataflow: " + e.getMessage(), e);
            }
        });
    }

    private static boolean covers(com.github.javaparser.Range range, int line, int column) {
        if (line < range.begin.line || line > range.end.line) {
            return false;
        }
        if (line == range.begin.line && column < range.begin.column) {
            return false;
        }
        return !(line == range.end.line && column > range.end.column);
    }

    private record CachedMethod(DataflowQueries queries, MethodDeclaration method) {
    }
}
