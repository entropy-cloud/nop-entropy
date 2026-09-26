package io.nop.refactor.java;

import io.nop.api.core.util.SourceLocation;
import io.nop.javaparser.JavaParseTool;
import io.nop.javaparser.parse.JavaParserParseResult;
import io.nop.lint.core.node.SourceRange;
import io.nop.lint.java.semantic.ScopeAnalyzer;
import io.nop.refactor.core.symbol.SymbolDeclaration;
import io.nop.refactor.core.symbol.SymbolKind;
import io.nop.refactor.core.symbol.SymbolReference;
import io.nop.refactor.core.symbol.SymbolResolverAdapter;

import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The first {@link SymbolResolverAdapter} implementation (roadmap WI9, plan
 * 09 adjudication 3/4): Java parsing rides nop-java-parser's
 * {@code JavaParseTool.parseJavaSource} (v1 runs with the symbol solver off
 * — the index and binding filter are structural), the scope semantics ride
 * the nop-lint-java {@code ScopeAnalyzer} public API
 * ({@code definitionOf} resolves the file+offset target form — the exact
 * face WI10/WI11 rename consumes), and the embedded lightweight index is
 * built per run over the single module's files (WI2 adjudications: module
 * scope, no nop-code, no persistent index).
 *
 * <p>Reference binding filter (WI2 adjudication 2): a file binds a target
 * declaration when it shares the declaring file's package, or imports the
 * declaration's FQN, or mentions the FQN in qualified form — occurrences
 * outside these bindings are structurally invisible, so an unbound file can
 * never leak false references. An unresolvable target returns the explicit
 * unresolved form, never a silent empty list.</p>
 */
public final class JavaSymbolResolverAdapter implements SymbolResolverAdapter {

    /**
     * Structural parsing only — the v1 index and binding filter need no
     * reflection-based type solving (plan 09 review: resolveSymbol off).
     */
    private final JavaParseTool parseTool = JavaParseTool.instance().resolveSymbol(false);

    private final ScopeAnalyzer scopeAnalyzer = new ScopeAnalyzer();

    @Override
    public DeclarationIndex buildIndex(List<SourceFile> files) {
        Objects.requireNonNull(files, "files must not be null");
        JavaDeclarationIndex index = new JavaDeclarationIndex();
        for (SourceFile file : files) {
            index.addFile(file);
        }
        return index;
    }

    @Override
    public Resolution resolveReference(DeclarationIndex index, SymbolTarget target) {
        Objects.requireNonNull(index, "index must not be null");
        Objects.requireNonNull(target, "target must not be null");
        if (!(index instanceof JavaDeclarationIndex javaIndex)) {
            throw new io.nop.refactor.core.NopRefactorException(
                    "the index was not built by this adapter (an adapter consumes only "
                            + "its own index; fail-closed)");
        }
        JavaDeclaration javaDeclaration = target.isFqnForm()
                ? javaIndex.byFqn.get(target.fqn())
                : javaIndex.definitionAt(target.path(), target.byteOffset());
        if (javaDeclaration == null) {
            return Resolution.unresolved(target.isFqnForm()
                    ? "no type declaration with FQN '" + target.fqn()
                    + "' in the indexed module"
                    : "no declaration resolves at offset " + target.byteOffset()
                    + " in '" + target.path() + "'");
        }

        SymbolDeclaration declaration = javaDeclaration.declaration;
        List<SymbolReference> references = new ArrayList<>();
        for (ParsedFile file : javaIndex.files.values()) {
            if (!binds(file, javaDeclaration)) {
                continue;
            }
            for (SimpleName name : file.unit.findAll(SimpleName.class)) {
                if (!name.getIdentifier().equals(declaration.name())) {
                    continue;
                }
                SourceRange range = file.rangeOf(name);
                if (range.equals(declaration.range())) {
                    continue;
                }
                references.add(new SymbolReference(name.getIdentifier(), range, file.path));
            }
            // qualified-name mentions (F.Q.N form) in other packages bind too
            if (!file.packageName.equals(javaDeclaration.declaringPackage)
                    && declaration.hasFqn()) {
                for (Name qualified : file.unit.findAll(Name.class)) {
                    if (qualified.asString().equals(declaration.fqn())) {
                        references.add(new SymbolReference(declaration.name(),
                                file.rangeOf(qualified), file.path));
                    }
                }
            }
        }
        return Resolution.of(references);
    }

    /**
     * The binding filter (WI2 adjudication 2): the declaring file always
     * binds; another file binds when it shares the package or imports the
     * declaration's FQN (the qualified-mention branch runs in the caller).
     */
    private boolean binds(ParsedFile file, JavaDeclaration declaration) {
        if (file.path.equals(declaration.declaration.path())) {
            return true;
        }
        if (file.packageName.equals(declaration.declaringPackage)) {
            return true;
        }
        if (!declaration.declaration.hasFqn()) {
            return false;
        }
        return file.unit.getImports().stream()
                .anyMatch(importDecl -> importDecl.getName().asString()
                        .equals(declaration.declaration.fqn()));
    }

    /**
     * One indexed declaration plus the package context its binding filter
     * needs (a member's binding package is its declaring file's package).
     */
    private record JavaDeclaration(SymbolDeclaration declaration, String declaringPackage) {
    }

    /**
     * The per-run module index: parsed units by path, declarations by simple
     * name and by FQN. Nothing persists beyond the run (WI2: no external
     * index service, no nop-code).
     */
    final class JavaDeclarationIndex implements DeclarationIndex {

        private final Map<String, ParsedFile> files = new LinkedHashMap<>();
        private final Map<String, List<JavaDeclaration>> byName = new HashMap<>();
        private final Map<String, JavaDeclaration> byFqn = new HashMap<>();

        @Override
        public int fileCount() {
            return files.size();
        }

        void addFile(SourceFile file) {
            ParsedFile parsed = parse(file);
            files.put(file.path(), parsed);

            List<JavaDeclaration> declarations = collectDeclarations(parsed);
            for (JavaDeclaration declaration : declarations) {
                byName.computeIfAbsent(declaration.declaration().name(),
                                k -> new ArrayList<>())
                        .add(declaration);
                if (declaration.declaration().hasFqn()) {
                    byFqn.put(declaration.declaration().fqn(), declaration);
                }
            }
        }

        /**
         * The file+offset target form rides the nop-lint-java ScopeAnalyzer
         * public API: the byte offset maps to a line/column, and
         * {@code definitionOf} names the definition enclosing that position.
         */
        private JavaDeclaration definitionAt(String path, long byteOffset) {
            ParsedFile file = files.get(path);
            if (file == null || byteOffset < 0 || byteOffset >= file.content.length()) {
                return null;
            }
            int[] lineCol = file.lineColumnOf((int) byteOffset);
            ScopeAnalyzer.Definition definition;
            try {
                definition = scopeAnalyzer.definitionOf(file.unit, lineCol[0], lineCol[1]);
            } catch (io.nop.lint.core.NopLintException e) {
                // a position outside any node (blank line, past the last
                // token) names no definition — the explicit unresolved face,
                // not a parse failure
                return null;
            }
            if (definition == null) {
                return null;
            }
            List<JavaDeclaration> candidates = byName.getOrDefault(
                    definition.name(), List.of());
            for (JavaDeclaration candidate : candidates) {
                if (candidate.declaration().path().equals(path)
                        && candidate.declaration().range().startByte() <= file.offsetOf(
                        definition.line(), definition.column())) {
                    return candidate;
                }
            }
            return null;
        }

        private List<JavaDeclaration> collectDeclarations(ParsedFile parsed) {
            List<JavaDeclaration> result = new ArrayList<>();
            String packageName = parsed.packageName;

            parsed.unit.findAll(ClassOrInterfaceDeclaration.class).forEach(type ->
                    addDeclaration(result, parsed, packageName, type.getName(),
                            SymbolKind.TYPE, fqnOf(parsed, type)));
            parsed.unit.findAll(EnumDeclaration.class).forEach(type ->
                    addDeclaration(result, parsed, packageName, type.getName(),
                            SymbolKind.TYPE, fqnOf(parsed, type)));
            parsed.unit.findAll(RecordDeclaration.class).forEach(type ->
                    addDeclaration(result, parsed, packageName, type.getName(),
                            SymbolKind.TYPE, fqnOf(parsed, type)));

            parsed.unit.findAll(FieldDeclaration.class).forEach(field ->
                    field.getVariables().forEach(declarator ->
                            addDeclaration(result, parsed, packageName,
                                    declarator.getName(), SymbolKind.FIELD, "")));
            parsed.unit.findAll(MethodDeclaration.class).forEach(method ->
                    addDeclaration(result, parsed, packageName, method.getName(),
                            SymbolKind.METHOD, ""));
            parsed.unit.findAll(ConstructorDeclaration.class).forEach(constructor ->
                    addDeclaration(result, parsed, packageName, constructor.getName(),
                            SymbolKind.METHOD, ""));

            parsed.unit.findAll(Parameter.class).forEach(parameter ->
                    addDeclaration(result, parsed, packageName, parameter.getName(),
                            SymbolKind.PARAMETER, ""));
            parsed.unit.findAll(VariableDeclarationExpr.class).forEach(varDecl ->
                    varDecl.getVariables().forEach(declarator ->
                            addDeclaration(result, parsed, packageName,
                                    declarator.getName(), SymbolKind.LOCAL_VARIABLE, "")));
            return result;
        }

        /**
         * The v1 FQN face: package + outer type chain for types (nested
         * types qualify through their enclosing type's FQN).
         */
        private String fqnOf(ParsedFile parsed, com.github.javaparser.ast.body.TypeDeclaration<?> type) {
            String fqn = type.getFullyQualifiedName().orElse("");
            return fqn;
        }

        private void addDeclaration(List<JavaDeclaration> result, ParsedFile parsed,
                                    String packageName, SimpleName name,
                                    SymbolKind kind, String fqn) {
            name.getRange().ifPresentOrElse(range ->
                            result.add(new JavaDeclaration(new SymbolDeclaration(
                                    name.getIdentifier(), parsed.rangeOf(range),
                                    kind, parsed.path, fqn), packageName)),
                    () -> {
                        throw new io.nop.refactor.core.NopRefactorException(
                                "declaration '" + name.getIdentifier() + "' in '"
                                        + parsed.path + "' carries no source range "
                                        + "(the index is byte-range based; fail-closed)");
                    });
        }
    }

    /**
     * One parsed source file: the unit plus the byte-offset mapping its
     * ranges need (JavaParser reports line/column; the SPI contract is byte
     * ranges, so the adapter owns the conversion).
     */
    private ParsedFile parse(SourceFile file) {
        JavaParserParseResult parsed;
        try {
            parsed = parseTool.parseJavaSource(SourceLocation.fromPath(file.path()),
                    file.content());
        } catch (RuntimeException e) {
            throw new io.nop.refactor.core.NopRefactorException(
                    "indexing failed for '" + file.path() + "' (the module index parses "
                            + "every file; a parse failure is fail-closed): " + e.getMessage(), e);
        }
        return new ParsedFile(file.path(), file.content(), parsed.getCompilationUnit());
    }

    private static final class ParsedFile {
        final String path;
        final String content;
        final CompilationUnit unit;
        final String packageName;
        private final int[] lineStartBytes;

        ParsedFile(String path, String content, CompilationUnit unit) {
            this.path = path;
            this.content = content;
            this.unit = unit;
            this.packageName = unit.getPackageDeclaration()
                    .map(pkg -> pkg.getNameAsString()).orElse("");
            this.lineStartBytes = lineStartBytes(content);
        }

        SourceRange rangeOf(Range range) {
            int start = offsetOf(range.begin.line, range.begin.column);
            int end = offsetOf(range.end.line, range.end.column)
                    + utf8Bytes(content, range.end);
            return new SourceRange(start, end);
        }

        SourceRange rangeOf(com.github.javaparser.ast.Node node) {
            return node.getRange()
                    .map(this::rangeOf)
                    .orElseThrow(() -> new io.nop.refactor.core.NopRefactorException(
                            "node in '" + path + "' carries no source range (fail-closed)"));
        }

        int[] lineColumnOf(int byteOffset) {
            int line = 1;
            while (line < lineStartBytes.length
                    && lineStartBytes[line] <= byteOffset) {
                line++;
            }
            int lineStart = lineStartBytes[line - 1];
            int column = 1;
            int byteCursor = lineStart;
            int charCursor = charIndexOf(lineStart);
            while (byteCursor < byteOffset && charCursor < content.length()) {
                byteCursor += utf8Bytes(content.charAt(charCursor));
                charCursor++;
                column++;
            }
            return new int[]{line, column};
        }

        int offsetOf(int line, int column) {
            int lineStart = lineStartBytes[Math.min(line - 1, lineStartBytes.length - 1)];
            int charCursor = charIndexOf(lineStart);
            int byteCursor = lineStart;
            for (int i = 1; i < column && charCursor < content.length(); i++) {
                byteCursor += utf8Bytes(content.charAt(charCursor));
                charCursor++;
            }
            return byteCursor;
        }

        private int charIndexOf(int byteOffset) {
            int bytes = 0;
            int chars = 0;
            while (bytes < byteOffset && chars < content.length()) {
                bytes += utf8Bytes(content.charAt(chars));
                chars++;
            }
            return chars;
        }

        private static int[] lineStartBytes(String content) {
            List<Integer> starts = new ArrayList<>();
            starts.add(0);
            int offset = 0;
            for (int i = 0; i < content.length(); i++) {
                offset += utf8Bytes(content.charAt(i));
                if (content.charAt(i) == '\n') {
                    starts.add(offset);
                }
            }
            int[] result = new int[starts.size()];
            for (int i = 0; i < starts.size(); i++) {
                result[i] = starts.get(i);
            }
            return result;
        }

        private static int utf8Bytes(String content, com.github.javaparser.Position position) {
            // the end position is inclusive: measure the character at it
            int charIndex = 0;
            int line = 1;
            int column = 1;
            while (charIndex < content.length()) {
                if (line == position.line && column == position.column) {
                    return utf8Bytes(content.charAt(charIndex));
                }
                if (content.charAt(charIndex) == '\n') {
                    line++;
                    column = 1;
                } else {
                    column++;
                }
                charIndex++;
            }
            return 0;
        }

        private static int utf8Bytes(char c) {
            if (c < 0x80) {
                return 1;
            }
            if (c < 0x800) {
                return 2;
            }
            if (c >= 0xD800 && c <= 0xDFFF) {
                return 2; // surrogate pair: two chars, four bytes, two per char
            }
            return 3;
        }
    }
}
