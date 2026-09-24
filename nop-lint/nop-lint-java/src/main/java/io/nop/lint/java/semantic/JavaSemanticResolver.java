package io.nop.lint.java.semantic;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.Node;
import io.nop.lint.core.NopLintException;
import io.nop.lint.core.semantic.SemanticResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The Java {@link SemanticResolver} (roadmap item 34): parses the named file
 * with the symbol solver attached (the {@code JavaTypeResolver} assembly;
 * the solver must precede the parse) and answers through the {@link
 * SemanticAnalyzer}. The position locates the type declaration (class/
 * interface or {@code new} expression), the method, or the call.
 *
 * <p>Fail-closed: a type the solver cannot resolve (a user class outside
 * the linter classpath) surfaces as {@link NopLintException} — the xscript
 * failure path skips and counts, a fabricated false is never produced.
 * Registered through ServiceLoader.</p>
 */
public class JavaSemanticResolver implements SemanticResolver {

    private static final int CACHE_SIZE = 32;

    private final SemanticAnalyzer analyzer = new SemanticAnalyzer();
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
    public boolean implementsInterface(String filePath, int line, int col, String interfaceName) {
        CachedUnit unit = unitFor(filePath);
        Node node = unit.index().minimalContaining(unit.lineCols().byteOf(line + 1, col + 1));
        ClassOrInterfaceDeclaration type = findAncestor(node, ClassOrInterfaceDeclaration.class);
        if (type == null) {
            ObjectCreationExpr creation = findAncestor(node, ObjectCreationExpr.class);
            if (creation == null) {
                throw new NopLintException("no type declaration at " + line + ":" + col
                        + " in '" + filePath + "' (implementsInterface queries a type"
                        + " declaration or a new expression)");
            }
            return unit.analyzer().implementsInterface(
                    creation.getType().resolve().asReferenceType()
                            .getTypeDeclaration().orElseThrow(), interfaceName);
        }
        return unit.analyzer().implementsInterface(type, interfaceName);
    }

    @Override
    public boolean isOverridable(String filePath, int line, int col) {
        CachedUnit unit = unitFor(filePath);
        Node node = unit.index().minimalContaining(unit.lineCols().byteOf(line + 1, col + 1));
        MethodDeclaration method = findAncestor(node, MethodDeclaration.class);
        if (method == null) {
            throw new NopLintException("no method at " + line + ":" + col + " in '"
                    + filePath + "' (isOverridable queries a method declaration)");
        }
        return unit.analyzer().isOverridable(method);
    }

    @Override
    public boolean isLoggerCall(String filePath, int line, int col) {
        CachedUnit unit = unitFor(filePath);
        Node node = unit.index().minimalContaining(unit.lineCols().byteOf(line + 1, col + 1));
        MethodCallExpr call = findAncestor(node, MethodCallExpr.class);
        if (call == null) {
            throw new NopLintException("no method call at " + line + ":" + col + " in '"
                    + filePath + "' (isLoggerCall queries a call)");
        }
        return unit.analyzer().isLoggerCall(call);
    }

    private <T extends Node> T findAncestor(Node node, Class<T> type) {
        Node current = node;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getParentNode().orElse(null);
        }
        return null;
    }

    private synchronized CachedUnit unitFor(String filePath) {
        return unitsByPath.computeIfAbsent(filePath, path -> {
            try {
                byte[] source = Files.readAllBytes(Path.of(path));
                ParserConfiguration config = new ParserConfiguration();
                config.setSymbolResolver(new com.github.javaparser.symbolsolver.JavaSymbolSolver(
                        new com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver(
                                new com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver(),
                                new com.github.javaparser.symbolsolver.resolution.typesolvers.ClassLoaderTypeSolver(
                                        Thread.currentThread().getContextClassLoader()))));
                CompilationUnit unit = new JavaParser(config)
                        .parse(new String(source, StandardCharsets.UTF_8))
                        .getResult()
                        .orElseThrow(() -> new NopLintException("the source of '" + path
                                + "' did not parse into a compilation unit (semantic analysis"
                                + " unavailable)"));
                LineColBytes lineCols = new LineColBytes(source);
                JavaNodeIndex index = JavaNodeIndex.build(unit, lineCols);
                return new CachedUnit(unit, lineCols, index, new SemanticAnalyzer());
            } catch (IOException e) {
                throw new NopLintException("failed to read '" + path
                        + "' for semantic analysis: " + e.getMessage(), e);
            }
        });
    }

    private record CachedUnit(CompilationUnit unit, LineColBytes lineCols,
                              JavaNodeIndex index, SemanticAnalyzer analyzer) {
    }
}
