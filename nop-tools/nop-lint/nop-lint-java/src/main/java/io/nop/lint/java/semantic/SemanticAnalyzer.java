package io.nop.lint.java.semantic;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import io.nop.lint.core.NopLintException;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;

import java.util.Set;

/**
 * The L4 semantic-analysis kernel (roadmap item 34, design 06 §4.5 contract
 * names): interface implementation across the type hierarchy, method
 * overridability, and logger-call recognition — over a symbol-solver-backed
 * parse (the {@code JavaTypeResolver} assembly; the solver must be attached
 * to the configuration before the parse).
 *
 * <p>v1 可答面 (plan Decision 2): JDK type hierarchies plus same-compilation-
 * unit hierarchies (transitive chains and superinterfaces included); a type
 * the solver cannot resolve (a user class outside the linter classpath)
 * surfaces as {@link UnsolvedSymbolException} — the resolver layer
 * translates it, and a fabricated {@code false} is never returned.
 * isOverridable is a pure-AST judgment (modifiers plus enclosing-class
 * finality) and needs no solver.</p>
 */
public final class SemanticAnalyzer {

    /**
     * The logger interfaces the v1 whitelist recognizes (plan Decision 5);
     * the log4j branch is classpath-conditional (log4j-api is not on the
     * lint modules' dependency chain) — fixtures pin slf4j/JUL forms.
     */
    private static final Set<String> LOGGER_TYPES = Set.of(
            "org.slf4j.Logger",
            "java.util.logging.Logger",
            "org.apache.logging.log4j.Logger");

    /**
     * The logger method names: the level-log family, the generic {@code
     * log}, and the {@code isXxxEnabled} guards.
     */
    private static final Set<String> LOGGER_METHODS = Set.of(
            "trace", "debug", "info", "warn", "error", "log");

    /**
     * True when the type declaration at the node implements (directly, via
     * a superclass chain, or via a superinterface) the interface named
     * {@code interfaceName}. The comparison target normalizes through the
     * {@code JavaTypeResolver.resolveExpectedType} vocabulary (FQN → CU
     * imports → java.lang. fallback) and the hierarchy match uses the
     * ERASED qualified name — a {@code Comparable<Foo>} ancestor matches
     * {@code java.lang.Comparable}.
     */
    /**
     * The comparison-target normalizer exposed for the position-keyed
     * resolver (roadmap item 34): same vocabulary as the kernel's internal
     * use — FQN passthrough, CU imports, java.lang fallback, same-package.
     */
    public String normalizeName(String interfaceName, ClassOrInterfaceDeclaration type) {
        return normalize(interfaceName, type);
    }

    public boolean implementsInterface(ClassOrInterfaceDeclaration type, String interfaceName) {
        return implementsInterface(type.resolve(), interfaceName,
                normalize(interfaceName, type));
    }

    /**
     * The resolved-type overload for the position-keyed resolver's
     * new-expression path (roadmap item 34): no compilation unit is at
     * hand, so the comparison target normalizes through FQN passthrough
     * and the java.lang fallback only.
     */
    public boolean implementsInterface(ResolvedReferenceTypeDeclaration resolved,
                                       String interfaceName) {
        String trimmed = interfaceName.trim();
        String target = trimmed.contains(".") ? trimmed
                : JAVA_LANG_SIMPLES.contains(trimmed) ? "java.lang." + trimmed
                : trimmed;
        for (ResolvedReferenceType ancestor : resolved.getAllAncestors()) {
            if (erasedQualifiedName(ancestor).equals(target)) {
                return true;
            }
        }
        return false;
    }

    private boolean implementsInterface(ResolvedReferenceTypeDeclaration resolved,
                                        String interfaceName, String target) {
        for (ResolvedReferenceType ancestor : resolved.getAllAncestors()) {
            if (erasedQualifiedName(ancestor).equals(target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when the method cannot be overridden: final/static/private
     * modifiers, or a final enclosing class. An interface method is
     * always overridable in the implementing type.
     */
    public boolean isOverridable(MethodDeclaration method) {
        if (method.isFinal() || method.isStatic() || method.isPrivate()) {
            return true;
        }
        return method.findAncestor(ClassOrInterfaceDeclaration.class)
                .map(ClassOrInterfaceDeclaration::isFinal)
                .orElse(false);
    }

    /**
     * True when the call's receiver resolves to a whitelisted logger type
     * and the method name is in the log family or an {@code isXxxEnabled}
     * guard. A receiver whose type cannot resolve throws (the caller's
     * failure path — never a fabricated false).
     */
    public boolean isLoggerCall(MethodCallExpr call) {
        String name = call.getName().getIdentifier();
        if (LOGGER_METHODS.contains(name) || name.startsWith("is") && name.endsWith("Enabled")) {
            com.github.javaparser.ast.expr.Expression scope = call.getScope().orElse(null);
            if (scope == null) {
                return false;
            }
            ResolvedType receiverType = scope.calculateResolvedType();
            if (receiverType instanceof ResolvedReferenceType referenceType) {
                return LOGGER_TYPES.contains(referenceType.getQualifiedName());
            }
        }
        return false;
    }

    /**
     * The comparison target normalizer: FQN passes through; a simple name
     * resolves through the unit's imports and falls back to {@code java.}.
     * lang.*} (the {@code JavaTypeResolver.resolveExpectedType} vocabulary).
     */
    private String normalize(String interfaceName, ClassOrInterfaceDeclaration type) {
        String trimmed = interfaceName.trim();
        if (trimmed.contains(".")) {
            return trimmed;
        }
        var importMatch = type.findCompilationUnit().flatMap(unit -> unit.getImports().stream()
                .filter(importDecl -> importDecl.getName().asString().endsWith("." + trimmed)
                        || importDecl.getName().asString().equals(trimmed))
                .findFirst());
        return importMatch
                .map(importDecl -> importDecl.getName().asString())
                .orElseGet(() -> {
                    // the java.lang fallback plus the same-package form
                    if (JAVA_LANG_SIMPLES.contains(trimmed)) {
                        return "java.lang." + trimmed;
                    }
                    String packagePrefix = type.findCompilationUnit()
                            .flatMap(unit -> unit.getPackageDeclaration())
                            .map(pkg -> pkg.getNameAsString() + ".")
                            .orElse("");
                    return packagePrefix + trimmed;
                });
    }

    private static final Set<String> JAVA_LANG_SIMPLES = Set.of(
            "Runnable", "Comparable", "Iterable", "AutoCloseable", "Cloneable",
            "CharSequence", "Readable", "Thread.UncaughtExceptionHandler");

    private static String erasedQualifiedName(ResolvedReferenceType referenceType) {
        ResolvedReferenceTypeDeclaration typeDeclaration = referenceType.getTypeDeclaration()
                .orElseThrow(() -> new NopLintException(
                        referenceType.describe() + " has no type declaration"));
        return typeDeclaration.getQualifiedName();
    }
}
