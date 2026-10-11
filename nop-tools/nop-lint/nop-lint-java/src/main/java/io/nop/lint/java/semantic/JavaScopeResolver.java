package io.nop.lint.java.semantic;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import io.nop.lint.core.NopLintException;
import io.nop.lint.core.semantic.ScopeResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Java {@link ScopeResolver} (roadmap item 33): parses the named file
 * (LRU-cached, the {@code JavaMetricsResolver} pattern) and answers through
 * the {@link ScopeAnalyzer}. Positions convert from the engine's 0-based
 * line / UTF-16 column contract to JavaParser's 1-based columns at the
 * boundary; the definition answer converts back to the engine's byte
 * offset via {@link LineColBytes}.
 *
 * <p>Fail-closed: malformed positions throw {@link NopLintException}; a
 * name nothing in the unit declares is the legitimate -1 answer, never a
 * fabricated node. Registered through ServiceLoader ({@code
 * META-INF/services}).</p>
 */
public class JavaScopeResolver implements ScopeResolver {

    private static final int CACHE_SIZE = 32;

    private final ScopeAnalyzer analyzer = new ScopeAnalyzer();
    private final JavaParser parser = new JavaParser(new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17));
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
    public long definitionOf(String filePath, int line, int col) {
        CachedUnit unit = unitFor(filePath);
        int[] at = javaParserPosition(unit, filePath, line, col);
        ScopeAnalyzer.Definition definition = analyzer.definitionOf(unit.unit(), at[0], at[1]);
        if (definition == null) {
            return -1;
        }
        return unit.lineCols().byteOf(definition.line(), definition.column());
    }

    @Override
    public List<String> declaredNames(String filePath, int line, int col) {
        CachedUnit unit = unitFor(filePath);
        int[] at = javaParserPosition(unit, filePath, line, col);
        return analyzer.declaredNames(unit.unit(), at[0], at[1]);
    }

    @Override
    public String scopeKind(String filePath, int line, int col) {
        CachedUnit unit = unitFor(filePath);
        int[] at = javaParserPosition(unit, filePath, line, col);
        return analyzer.scopeKind(unit.unit(), at[0], at[1]);
    }

    @Override
    public boolean shadows(String filePath, int line, int col) {
        CachedUnit unit = unitFor(filePath);
        int[] at = javaParserPosition(unit, filePath, line, col);
        return analyzer.shadows(unit.unit(), at[0], at[1]);
    }

    /**
     * Engine 0-based line / UTF-16 column → JavaParser 1-based (line,
     * column) — the {@code JavaTypeResolver} convention.
     */
    private int[] javaParserPosition(CachedUnit unit, String filePath, int line, int col) {
        if (line < 0 || col < 0) {
            throw new NopLintException("negative position " + line + ":" + col
                    + " in '" + filePath + "' (a scope query must carry a real position)");
        }
        return new int[]{line + 1, col + 1};
    }

    private synchronized CachedUnit unitFor(String filePath) {
        return unitsByPath.computeIfAbsent(filePath, path -> {
            try {
                byte[] source = Files.readAllBytes(Path.of(path));
                CompilationUnit unit = parser.parse(new String(source, StandardCharsets.UTF_8))
                        .getResult()
                        .orElseThrow(() -> new NopLintException("the source of '" + path
                                + "' did not parse into a compilation unit (scope analysis"
                                + " unavailable)"));
                LineColBytes lineCols = new LineColBytes(source);
                return new CachedUnit(unit, lineCols);
            } catch (IOException e) {
                throw new NopLintException("failed to read '" + path + "' for scope analysis: "
                        + e.getMessage(), e);
            }
        });
    }

    private record CachedUnit(CompilationUnit unit, LineColBytes lineCols) {
    }
}
