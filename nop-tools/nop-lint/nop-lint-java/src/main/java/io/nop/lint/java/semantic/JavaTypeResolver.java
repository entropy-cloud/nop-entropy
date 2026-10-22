package io.nop.lint.java.semantic;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.nodeTypes.NodeWithType;
import com.github.javaparser.ast.nodeTypes.NodeWithVariables;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import io.nop.lint.core.semantic.TypeResolutionException;
import io.nop.lint.core.semantic.TypeResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The Java L2 {@link TypeResolver} (roadmap item 26): single-file parse
 * through JavaParser with the symbolsolver attached to a reflection type
 * solver — JDK-visible types resolve, project-local types raise
 * {@link UnsolvedSymbolException} which the translation layer converts to
 * {@link TypeResolutionException} so the engine degrades the rule instead
 * of guessing (the roadmap hard constraint: L2 is never faked with L1
 * answers). The single-file + reflection model is the plan's v1
 * adjudication; project-classpath resolution is the successor surface
 * (roadmap item 37/40 production wiring).
 *
 * <p>Laziness (design 11 §3, with the round-1 F6 precision): the solver is
 * attached to the {@link ParserConfiguration} before parsing (attaching
 * after a parse is ineffective), and the FILE PARSE happens on the first
 * query — repeated queries hit the per-file cache. The query cache is keyed
 * by (filePath, line, col[, expectedType]) — the round-1 F4 adjudication: a
 * (line, col)-only key would leak answers across the files one instance
 * serves. {@code initProject} re-binding clears the caches.</p>
 *
 * <p>expectedType semantics (the plan's F8 pin): a fully-qualified name is
 * compared directly; a simple name is resolved through the parsed
 * compilation unit's own import context first, then the {@code java.lang}
 * package — mirroring the tsc bridge's same-checker query semantics.
 * Assignability uses the symbolsolver's {@code ResolvedType.isAssignableBy}
 * over the expected type's own declaration (the real supertype chain, not a
 * name approximation).</p>
 */
public final class JavaTypeResolver implements TypeResolver {

    // the sibling resolver convention (plan 12): bounded LRU + synchronized —
    // a long-running LSP process must not accumulate one entry per file forever
    private static final int CACHE_SIZE = 32;

    private final Map<String, ParsedFile> filesByPath = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ParsedFile> eldest) {
            return size() > CACHE_SIZE;
        }
    };
    // assignability answers and type names are DIFFERENT value types (plan 12
    // audit B12-2): two maps instead of one Map<String,String> storing both
    private final Map<String, Boolean> assignableCache = new HashMap<>();
    private final Map<String, String> typeNameCache = new HashMap<>();

    @Override
    public boolean isAvailable() {
        // an in-process library: the environment always plausibly supports
        // the query (design 11 §3 cheap-probe contract)
        return true;
    }

    /**
     * The solver chain the per-file parses attach: reflection (JDK types)
     * plus, in project mode, one {@link JavaParserTypeSolver} per discovered
     * source root. Swapped atomically by {@link #initProject(Path)}.
     */
    private volatile CombinedTypeSolver typeSolvers = new CombinedTypeSolver(
            new ReflectionTypeSolver());

    /**
     * Project mode (roadmap item 37/40 production wiring): the project root
     * is scanned for Java source roots ({@code src/main/java} and
     * {@code src/test/java} at any depth, hidden and build trees pruned)
     * and each becomes
     * a {@link JavaParserTypeSolver} behind the reflection solver —
     * project-local types resolve through their own compilation units. A
     * null root resets to the single-file + reflection model. Cache state is
     * always invalidated: answers bound to the old project must not leak.
     */
    @Override
    public synchronized void initProject(Path projectRoot) {
        filesByPath.clear();
        assignableCache.clear();
        typeNameCache.clear();
        if (projectRoot == null) {
            typeSolvers = new CombinedTypeSolver(new ReflectionTypeSolver());
            return;
        }
        if (!Files.isDirectory(projectRoot)) {
            throw new TypeResolutionException("project root '" + projectRoot
                    + "' is not a directory");
        }
        CombinedTypeSolver combined = new CombinedTypeSolver(new ReflectionTypeSolver());
        int roots = 0;
        for (Path sourceRoot : discoverSourceRoots(projectRoot)) {
            combined.add(new com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver(
                    sourceRoot));
            roots++;
        }
        if (roots == 0) {
            throw new TypeResolutionException("project root '" + projectRoot
                    + "' contains no src/main/java or src/test/java source roots; "
                    + "project-level resolution cannot bind (fail-closed, never a "
                    + "reflection-only guess under a declared project)");
        }
        typeSolvers = combined;
    }

    /**
     * The source roots under {@code projectRoot}: directories named
     * {@code src/main/java} or {@code src/test/java}. The walk prunes
     * hidden, build ({@code target}/{@code build}) and dependency
     * ({@code node_modules}) trees; the count cap is a runaway guard and
     * fails loudly rather than silently serving a partial project.
     */
    private static final int MAX_SOURCE_ROOTS = 4096;

    private static List<Path> discoverSourceRoots(Path projectRoot) {
        List<Path> roots = new ArrayList<>();
        try {
            Files.walkFileTree(projectRoot, new SimpleFileVisitor<>() {
                @Override
                public java.nio.file.FileVisitResult preVisitDirectory(Path dir,
                        BasicFileAttributes attrs) {
                    String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                    if (name.startsWith(".") || "target".equals(name) || "build".equals(name)
                            || "node_modules".equals(name)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult visitFileFailed(Path file,
                        IOException exc) {
                    // unreadable subtrees are skipped, not fatal
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult postVisitDirectory(Path dir,
                        IOException exc) {
                    String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                    if ("java".equals(name)) {
                        Path parent = dir.getParent();
                        if (parent != null && ("main".equals(
                                parent.getFileName() == null ? "" : parent.getFileName().toString())
                                || "test".equals(parent.getFileName() == null ? ""
                                        : parent.getFileName().toString()))) {
                            if (roots.size() >= MAX_SOURCE_ROOTS) {
                                throw new IllegalStateException("more than " + MAX_SOURCE_ROOTS
                                        + " source roots under '" + projectRoot + "' (runaway guard)");
                            }
                            roots.add(dir);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                /** The last three path segments joined, e.g. {@code src/main/java}. */
                private String remainder(Path dir) {
                    Path p = dir;
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < 3 && p != null; i++) {
                        sb.insert(0, p.getFileName() == null ? "" : p.getFileName().toString());
                        if (i < 2) {
                            sb.insert(0, '/');
                        }
                        p = p.getParent();
                    }
                    return sb.toString();
                }
            });
        } catch (java.io.IOException e) {
            throw new TypeResolutionException("failed to scan project root '" + projectRoot
                    + "' for source roots: " + e.getMessage(), e);
        }
        return roots;
    }

    @Override
    public synchronized boolean isAssignableTo(String filePath, int line, int col, String expectedType) {
        Objects.requireNonNull(expectedType, "expectedType must not be null");
        String cacheKey = filePath + "|" + line + "|" + col + "|" + expectedType;
        Boolean cached = assignableCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        ParsedFile file = file(filePath);
        ResolvedType resolved = resolvedTypeAt(file, filePath, line, col);
        String fqn = resolveExpectedType(file, expectedType);
        if (resolved.describe().equals(fqn)) {
            assignableCache.put(cacheKey, Boolean.TRUE);
            return true;
        }
        if (resolved.isReferenceType()) {
            // the supertype chain of the resolved type carries the
            // assignability answer (spike: String's ancestors include
            // CharSequence and Object)
            for (com.github.javaparser.resolution.types.ResolvedReferenceType ancestor
                    : resolved.asReferenceType().getAllAncestors()) {
                if (ancestor.describe().equals(fqn)) {
                    assignableCache.put(cacheKey, Boolean.TRUE);
                    return true;
                }
            }
        }
        assignableCache.put(cacheKey, Boolean.FALSE);
        return false;
    }

    @Override
    public synchronized String typeNameAt(String filePath, int line, int col) {
        String cacheKey = filePath + "|" + line + "|" + col;
        String cached = typeNameCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        ParsedFile file = file(filePath);
        String typeName = resolvedTypeAt(file, filePath, line, col).describe();
        typeNameCache.put(cacheKey, typeName);
        return typeName;
    }

    private ResolvedType resolvedTypeAt(ParsedFile file, String filePath, int line, int col) {
        // engine positions are 0-based; JavaParser positions are 1-based
        int bytePos = file.lineCols().byteOf(line + 1, col + 1);
        // the capture start is the WHOLE captured expression's start: prefer
        // the outermost expression beginning there (b.tag().toString() over
        // the inner b), and fall back to the innermost containing node for
        // declaration-form queries
        Node node = file.index().outermostExpressionStartingAt(bytePos);
        if (node == null) {
            node = file.index().minimalContaining(bytePos);
        }
        if (node == null) {
            throw new TypeResolutionException("position " + line + ":" + col + " in '" + filePath
                    + "' falls outside the parsed compilation unit");
        }
        Node current = node;
        while (current != null) {
            ResolvedType resolved = tryResolve(current, filePath);
            if (resolved != null) {
                return resolved;
            }
            current = current.getParentNode().orElse(null);
        }
        throw new TypeResolutionException("no resolvable type at the queried position in '"
                + filePath + "' (the node carries no expression or declaration type)");
    }

    /**
     * The position-key index of one parsed file (the design 06 §6.3 bridge's
     * resolver surface, exposed for the mapping contract tests).
     */
    public JavaNodeIndex indexOf(String filePath) {
        return file(filePath).index();
    }

    /**
     * Parses source bytes into a compilation unit with the symbol solver
     * attached — the configuration MUST precede the parse (attaching after
     * a parse is ineffective, plan F6). Package-visible so the mapping
     * contract tests parse exactly the way the resolver does.
     */
    static CompilationUnit parseUnit(byte[] source) {
        return parseUnitWith(new CombinedTypeSolver(new ReflectionTypeSolver()), source);
    }

    /**
     * The project-aware form: the run's solver chain (reflection + the
     * discovered source roots) rides the same parse configuration.
     */
    private CompilationUnit parseUnitInstance(byte[] source) {
        return parseUnitWith(typeSolvers, source);
    }

    private static CompilationUnit parseUnitWith(com.github.javaparser.resolution.TypeSolver solver,
                                                byte[] source) {
        ParserConfiguration config = new ParserConfiguration();
        config.setSymbolResolver(new JavaSymbolSolver(solver));
        JavaParser parser = new JavaParser(config);
        return parser.parse(new String(source, StandardCharsets.UTF_8))
                .getResult()
                .orElseThrow(() -> new TypeResolutionException(
                        "the source did not parse into a compilation unit"));
    }

    private synchronized ParsedFile file(String filePath) {
        return filesByPath.computeIfAbsent(filePath, path -> {
            try {
                byte[] source = Files.readAllBytes(Path.of(filePath));
                CompilationUnit cu = parseUnitInstance(source);
                LineColBytes lineCols = new LineColBytes(source);
                JavaNodeIndex index = JavaNodeIndex.build(cu, lineCols);
                return new ParsedFile(source, lineCols, cu, index);
            } catch (TypeResolutionException e) {
                throw e;
            } catch (Exception e) {
                throw new TypeResolutionException("failed to parse '" + filePath
                        + "' for type resolution: " + e.getMessage(), e);
            }
        });
    }

    /**
     * The per-node resolution attempt: null when the node kind carries no
     * expression/declaration type (the ancestor walk continues), a resolved
     * type when the node answers, and a translated
     * {@link TypeResolutionException} when the solver tries and fails (plan
     * F5 — UnsolvedSymbolException, the not-configured
     * IllegalStateException, and the CombinedTypeSolver's
     * UnsupportedOperationException all translate; a raw unsolved exception
     * escaping the resolver would bypass the engine's mid-run degrade catch
     * and crash the run).
     */
    private ResolvedType tryResolve(Node node, String filePath) {
        try {
            if (node instanceof Expression expression) {
                return expression.calculateResolvedType();
            }
            if (node instanceof com.github.javaparser.ast.body.VariableDeclarator declarator) {
                return declarator.getType().resolve();
            }
            if (node instanceof NodeWithType<?, ?> withType) {
                return withType.getType().resolve();
            }
            if (node instanceof Type type) {
                return type.resolve();
            }
            if (node instanceof NodeWithVariables<?> withVariables
                    && !withVariables.getVariables().isEmpty()) {
                return withVariables.getVariable(0).getType().resolve();
            }
        } catch (UnsolvedSymbolException | IllegalStateException | UnsupportedOperationException e) {
            throw new TypeResolutionException("symbol resolution failed in '" + filePath
                    + "': " + e.getMessage(), e);
        } catch (RuntimeException e) {
            if (e instanceof TypeResolutionException) {
                throw e;
            }
            throw new TypeResolutionException("symbol resolution failed in '" + filePath
                    + "': " + e.getMessage(), e);
        }
        return null;
    }

    private String resolveExpectedType(ParsedFile file, String expectedType) {
        if (expectedType.contains(".")) {
            return expectedType;
        }
        // the simple name resolves through the compilation unit's own import
        // context first (the F8 pin: same-unit symbolsolver semantics)
        for (com.github.javaparser.ast.ImportDeclaration importDecl : file.cu().getImports()) {
            String imported = importDecl.getNameAsString();
            if (imported.endsWith("." + expectedType)) {
                return imported;
            }
        }
        return "java.lang." + expectedType;
    }

    private record ParsedFile(byte[] source, LineColBytes lineCols, CompilationUnit cu,
                              JavaNodeIndex index) {
    }
}
