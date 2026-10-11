package io.nop.lint.java.semantic;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hand-computed anchors for the L4 semantic kernel (roadmap item 34 Phase
 * 1, plan Decision 2): the v1 answerable surface is JDK type hierarchies +
 * same-CU hierarchies (transitive, superinterfaces, erased generic names) +
 * the pure-AST overridability matrix; a user type outside the linter
 * classpath surfaces as UnsolvedSymbolException (translated by the resolver
 * layer) — never a fabricated false.
 */
public class TestSemanticAnalyzer {

    private final SemanticAnalyzer analyzer = new SemanticAnalyzer();
    private final JavaParser parser = solverParser();

    private static JavaParser solverParser() {
        ParserConfiguration config = new ParserConfiguration();
        config.setSymbolResolver(new com.github.javaparser.symbolsolver.JavaSymbolSolver(
                new com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver(
                        new com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver(),
                        new com.github.javaparser.symbolsolver.resolution.typesolvers.ClassLoaderTypeSolver(
                                Thread.currentThread().getContextClassLoader()))));
        return new JavaParser(config);
    }

    private ClassOrInterfaceDeclaration type(String source, String name) {
        CompilationUnit unit = parser.parse(source).getResult().orElseThrow();
        return unit.findAll(ClassOrInterfaceDeclaration.class).stream()
                .filter(t -> t.getNameAsString().equals(name))
                .findFirst().orElseThrow();
    }

    private MethodDeclaration method(String source, String name) {
        CompilationUnit unit = parser.parse(source).getResult().orElseThrow();
        return unit.findAll(MethodDeclaration.class).stream()
                .filter(m -> m.getNameAsString().equals(name))
                .findFirst().orElseThrow();
    }

    private MethodCallExpr firstCall(String source) {
        CompilationUnit unit = parser.parse(source).getResult().orElseThrow();
        return unit.findAll(MethodCallExpr.class).get(0);
    }

    @Test
    public void directJdkInterfaceImplementationAnswers() {
        ClassOrInterfaceDeclaration type = type("""
                class FileTask implements java.lang.Runnable {
                    public void run() { }
                }
                """, "FileTask");

        assertTrue(analyzer.implementsInterface(type, "java.lang.Runnable"));
        assertTrue(analyzer.implementsInterface(type, "Runnable"),
                "the simple name falls back to java.lang (resolveExpectedType vocabulary)");
        assertFalse(analyzer.implementsInterface(type, "java.lang.Comparable"));
    }

    @Test
    public void transitiveChainAndSuperinterfaceAnswer() {
        ClassOrInterfaceDeclaration type = type("""
                interface Named extends java.lang.Comparable<Named> {
                    int compareTo(Named other);
                }

                class Base implements Named {
                    public int compareTo(Named other) { return 0; }
                }

                class Child extends Base { }
                """, "Child");

        assertTrue(analyzer.implementsInterface(type, "Named"),
                "the superclass chain resolves to the same-CU interface");
        assertTrue(analyzer.implementsInterface(type, "Comparable"),
                "the superinterface chain reaches the JDK interface");
        assertTrue(analyzer.implementsInterface(type, "java.lang.Comparable"),
                "the erased qualified name matches despite the generic parameter");
    }

    @Test
    public void genericInterfaceMatchesThroughErasure() {
        ClassOrInterfaceDeclaration type = type("""
                class Box implements java.lang.Comparable<Box> {
                    public int compareTo(Box other) { return 0; }
                }
                """, "Box");

        assertTrue(analyzer.implementsInterface(type, "java.lang.Comparable"),
                "erased qualified-name comparison (describe() would carry <Box> and fail)");
    }

    @Test
    public void unresolvedUserTypeThrowsInsteadOfFaking() {
        ClassOrInterfaceDeclaration type = type("""
                class UsesExternal implements some.external.UnknownType {
                    public void doIt() { }
                }
                """, "UsesExternal");

        assertThrows(UnsolvedSymbolException.class,
                () -> analyzer.implementsInterface(type, "java.lang.Runnable"),
                "a user type outside the linter classpath is unanswerable (never false)");
    }

    @Test
    public void overridabilityMatrixMatchesModifiersAndClassFinality() {
        MethodDeclaration plain = method("""
                class Repo {
                    void query() { }
                }
                """, "query");

        MethodDeclaration finalMethod = method("""
                class Repo {
                    final void query() { }
                }
                """, "query");

        MethodDeclaration inFinalClass = method("""
                final class Repo {
                    void query() { }
                }
                """, "query");

        MethodDeclaration staticMethod = method("""
                class Repo {
                    static void query() { }
                }
                """, "query");

        assertFalse(analyzer.isOverridable(plain));
        assertTrue(analyzer.isOverridable(finalMethod));
        assertTrue(analyzer.isOverridable(inFinalClass), "a final class seals its methods");
        assertTrue(analyzer.isOverridable(staticMethod));
    }

    @Test
    public void loggerCallMatrixAnswersByReceiverAndName() {
        assertTrue(analyzer.isLoggerCall(firstCall("""
                class Demo {
                    void m(org.slf4j.Logger log) {
                        log.info("hello");
                    }
                }
                """)), "slf4j + log family");

        assertTrue(analyzer.isLoggerCall(firstCall("""
                class Demo {
                    void m(org.slf4j.Logger log) {
                        if (log.isDebugEnabled()) { }
                    }
                }
                """)), "the isXxxEnabled guard form");

        assertTrue(analyzer.isLoggerCall(firstCall("""
                class Demo {
                    void m(java.util.logging.Logger log) {
                        log.info("hello");
                    }
                }
                """)), "JUL receiver + whitelisted family");

        MethodCallExpr nonLogger = firstCall("""
                class Demo {
                    void m(java.util.List<String> list) {
                        list.info();
                    }
                }
                """);
        assertFalse(analyzer.isLoggerCall(nonLogger), "a non-logger receiver is a real false");

        MethodCallExpr unknownReceiver = firstCall("""
                class Demo {
                    void m(UserLogger log) {
                        log.info("x");
                    }
                }
                """);
        assertThrows(UnsolvedSymbolException.class, () -> analyzer.isLoggerCall(unknownReceiver),
                "an unresolvable receiver is unanswerable (never false)");
    }
}
