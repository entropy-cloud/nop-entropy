package io.nop.lint.java.semantic;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import io.nop.lint.core.NopLintException;
import io.nop.lint.core.semantic.MetricsResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The Java {@link MetricsResolver} (roadmap item 32): parses the named file
 * (a small LRU cache — lint runs query the same files repeatedly), locates
 * the smallest node containing the queried byte position via the
 * {@link JavaNodeIndex}, walks up to the enclosing {@link MethodDeclaration},
 * and answers through the {@link MetricsEvaluator}. The same self-parse
 * pattern as {@code JavaTypeResolver}; the symbol solver is not needed for
 * metrics, so a plain parse serves.
 *
 * <p>Fail-closed: a position with no enclosing method (or an unparseable
 * file) throws {@link NopLintException} — the xscript failure path skips
 * the match and counts it; a zero is never faked.</p>
 *
 * <p>Registered through ServiceLoader ({@code META-INF/services}), so CLI
 * runs and {@code RuleTestRunner} defaults pick it up when this module is
 * on the classpath.</p>
 */
public class JavaMetricsResolver implements MetricsResolver {

    private static final int CACHE_SIZE = 32;

    private final MetricsEvaluator evaluator = new MetricsEvaluator();
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
    public int cyclomatic(String filePath, int line, int col) {
        return evaluator.cyclomaticComplexity(enclosingMethod(filePath, line, col));
    }

    @Override
    public int cognitive(String filePath, int line, int col) {
        return evaluator.cognitiveComplexity(enclosingMethod(filePath, line, col));
    }

    @Override
    public long npath(String filePath, int line, int col) {
        return evaluator.nPathComplexity(enclosingMethod(filePath, line, col));
    }

    /**
     * The 0-based line / UTF-16 column contract converts to the file's
     * 1-based byte position (the {@code JavaTypeResolver} convention), the
     * index's minimal-containing node locates the query point, and the
     * parent walk finds the enclosing method.
     */
    private synchronized MethodDeclaration enclosingMethod(String filePath, int line, int col) {
        CachedUnit unit = unitsByPath.computeIfAbsent(filePath, this::parse);
        int bytePos = unit.lineCols().byteOf(line + 1, col + 1);
        Node node = unit.index().minimalContaining(bytePos);
        Node current = node;
        while (current != null && !(current instanceof MethodDeclaration)) {
            current = current.getParentNode().orElse(null);
        }
        if (current == null) {
            throw new NopLintException("no enclosing method for metrics at " + line + ":" + col
                    + " in '" + filePath + "' (a metrics query on a non-method node is a rule "
                    + "defect, not a zero)");
        }
        return (MethodDeclaration) current;
    }

    private CachedUnit parse(String filePath) {
        try {
            byte[] source = Files.readAllBytes(Path.of(filePath));
            CompilationUnit unit = parser.parse(new String(source, StandardCharsets.UTF_8))
                    .getResult()
                    .orElseThrow(() -> new NopLintException("the source of '" + filePath
                            + "' did not parse into a compilation unit (metrics unavailable)"));
            LineColBytes lineCols = new LineColBytes(source);
            JavaNodeIndex index = JavaNodeIndex.build(unit, lineCols);
            return new CachedUnit(lineCols, index);
        } catch (IOException e) {
            throw new NopLintException("failed to read '" + filePath + "' for metrics: "
                    + e.getMessage(), e);
        }
    }

    private record CachedUnit(LineColBytes lineCols, JavaNodeIndex index) {
    }
}
