package io.nop.refactor.java;

import io.nop.api.core.util.SourceLocation;
import io.nop.javaparser.JavaParseTool;
import io.nop.javaparser.parse.JavaParserParseResult;
import io.nop.lint.core.node.SourceRange;
import io.nop.lint.java.semantic.ScopeAnalyzer;
import io.nop.refactor.core.symbol.RenameResolution;
import io.nop.refactor.core.symbol.FileRewrite;
import io.nop.refactor.core.symbol.RenameSpan;
import io.nop.refactor.core.symbol.SymbolDeclaration;
import io.nop.refactor.core.symbol.SymbolKind;
import io.nop.refactor.core.symbol.SymbolReference;
import io.nop.refactor.core.symbol.SymbolResolverAdapter;

import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
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
     * The WI10 first-rung rename resolution (plan 10 adjudications 2/3/4):
     * the index locates and kinds the target (definitionOf cannot see
     * method/type name positions), the conflict domain is the enclosing
     * method's full local/parameter set plus the file's fields plus the
     * self-rename case, occurrences are bound NameExpr identifiers only (a
     * bare simple-name scan would rewrite method calls, field accesses and
     * type names — Java's separated namespaces), and the symbol-intact
     * assertion is pre-computed on the planned post-rename content: the
     * renamed content is re-parsed and every bound new-name NameExpr is
     * counted against the declaration position, whose line/column the
     * rename never moves.
     */
    @Override
    public RenameResolution renameResolution(DeclarationIndex index, SymbolTarget target,
                                             String newName) {
        if (!(index instanceof JavaDeclarationIndex javaIndex)) {
            throw new io.nop.refactor.core.NopRefactorException(
                    "the index was not built by this adapter (an adapter consumes only "
                            + "its own index; fail-closed)");
        }
        Objects.requireNonNull(newName, "newName must not be null");
        // locate: FQN resolves types through the index table (plan 11
        // adjudication 3); offset resolves by byte-range containment
        SymbolDeclaration declaration = target.isFqnForm()
                ? javaIndex.declarationByFqn(target.fqn())
                : javaIndex.declarationContaining(target.path(), target.byteOffset());
        if (declaration == null) {
            return RenameResolution.unresolved(target.isFqnForm()
                    ? "no declaration with FQN '" + target.fqn()
                    + "' in the indexed module"
                    : "no indexed declaration contains offset " + target.byteOffset()
                    + " in '" + target.path() + "'");
        }
        // self-rename is kind-agnostic and stays ahead of the routing
        if (declaration.name().equals(newName)) {
            return RenameResolution.conflict("self-rename '" + declaration.name()
                    + "' to itself is a no-op (rejected to keep the edit list "
                    + "non-degenerate; fail-closed)");
        }
        // kind routing (plan 11 adjudication 8): the first rung keeps its
        // path; constructors refuse (a constructor rename IS a class rename);
        // the second rung takes fields/methods/types
        return switch (declaration.kind()) {
            case LOCAL_VARIABLE, PARAMETER -> firstRungRename(javaIndex, declaration,
                    newName);
            case CONSTRUCTOR -> RenameResolution.outOfScope("a constructor rename is "
                    + "the class rename: target the TYPE (plan 11 adjudication 8)");
            case TYPE -> typeRename(javaIndex, declaration, newName);
            case FIELD -> memberRename(javaIndex, declaration, newName, true);
            case METHOD -> memberRename(javaIndex, declaration, newName, false);
        };
    }

    /**
     * The WI10 first rung, unchanged in semantics (plan 11 adjudication 8:
     * locals/parameters stay single-file).
     */
    private RenameResolution firstRungRename(JavaDeclarationIndex javaIndex,
                                             SymbolDeclaration declaration, String newName) {
        ParsedFile file = javaIndex.file(declaration.path());
        int[] declarationPos = file.lineColumnOf((int) declaration.range().startByte());
        String clash = methodBoundaryClash(file, declaration, declarationPos, newName);
        if (clash != null) {
            return RenameResolution.conflict(clash);
        }
        if (javaIndex.fieldNames(declaration.path()).contains(newName)) {
            return RenameResolution.conflict("the file already declares a field '"
                    + newName + "': an unqualified field reference after the rename "
                    + "point would be captured by the renamed local (silent semantics "
                    + "break; rejected fail-closed)");
        }

        List<SourceRange> occurrences = boundOccurrences(file, declaration.name(),
                declarationPos);
        boolean symbolIntact = symbolIntactOnPreview(file, declaration, declarationPos,
                occurrences, newName);
        List<RenameSpan> spans = new ArrayList<>(occurrences.size() + 1);
        spans.add(new RenameSpan(declaration.range(), newName));
        for (SourceRange range : occurrences) {
            spans.add(new RenameSpan(range, newName));
        }
        return RenameResolution.resolved(declaration, spans, symbolIntact);
    }

    /**
     * The WI11 TYPE face (plan 11 adjudications 1/6/7): the rewrite set is
     * the declaration identifier, the type's constructors, every bound
     * file's simple-name type uses (ClassOrInterfaceType) and expression
     * scopes (NameExpr), the exact and dot-boundary-prefix imports, and the
     * qualified-name mentions — aggregated into the cross-file span
     * carrier. A bound file that declares a same-named variable makes its
     * type uses structurally ambiguous (whole-file CONFLICT); a stale
     * residue on the planned content refuses the rename outright.
     */
    private RenameResolution typeRename(JavaDeclarationIndex javaIndex,
                                        SymbolDeclaration declaration, String newName) {
        String oldFqn = declaration.fqn();
        if (oldFqn.isEmpty()) {
            return RenameResolution.unresolved("the indexed type '"
                    + declaration.name() + "' carries no FQN (a TYPE rename is "
                    + "FQN-driven; fail-closed)");
        }
        String newFqn = oldFqn.substring(0,
                oldFqn.length() - declaration.name().length()) + newName;

        // wildcard-import ambiguity (adjudication 6): a file importing the
        // declaring package via `a.*` cannot bind the simple name when the
        // module holds another type of the same simple name
        java.util.Set<String> otherTypeNames = new java.util.HashSet<>();
        for (ParsedFile f : javaIndex.allFiles()) {
            for (JavaDeclaration d : javaIndex.declarationsOf(f.path)) {
                if (d.declaration().kind() == SymbolKind.TYPE
                        && !d.declaration().range().equals(declaration.range())) {
                    otherTypeNames.add(d.declaration().name());
                }
            }
        }
        boolean wildcardImportExists = false;
        String declaringPackage = oldFqn.substring(0,
                oldFqn.length() - declaration.name().length() - 1);
        for (ParsedFile f : javaIndex.allFiles()) {
            for (var importDecl : f.unit.getImports()) {
                if (!importDecl.isStatic() && importDecl.isAsterisk()
                        && importDecl.getNameAsString().equals(declaringPackage)) {
                    wildcardImportExists = true;
                }
            }
        }
        if (wildcardImportExists && otherTypeNames.contains(newName)) {
            return RenameResolution.conflict("the module declares another type '"
                    + newName + "' and a wildcard import of package '"
                    + declaringPackage + "' exists: the wildcard files' simple uses "
                    + "cannot be bound after the rename (rejected fail-closed)");
        }

        // the old face first pass: collect spans per bound file
        List<FileRewrite> rewrites = new ArrayList<>();
        int total = 0;
        for (ParsedFile file : javaIndex.allFiles()) {
            final List<RenameSpan> spans;
            if (bindsType(file, declaration)) {
                String clash = typeUseShadowClash(file, declaration);
                if (clash != null) {
                    return RenameResolution.conflict(clash);
                }
                // bound files carry the full face: simple uses, expression
                // scopes, imports, qualified mentions
                spans = typeUseSpans(file, declaration, oldFqn, newFqn, newName);
            } else {
                // unbound files still rewrite their FULLY-QUALIFIED mentions —
                // a qualified mention needs no import, so skipping it would
                // leave exactly the stale residue the roadmap forbids
                spans = qualifiedMentionSpans(file, declaration, oldFqn, newFqn, newName);
            }
            if (spans.isEmpty()) {
                continue;
            }
            spans.sort((a, b) -> Integer.compare(a.range().startByte(),
                    b.range().startByte()));
            rewrites.add(new FileRewrite(file.path, spans));
            total += spans.size();
        }
        if (rewrites.isEmpty()) {
            return RenameResolution.unresolved("no bound file carries a use of '"
                    + declaration.name() + "' (the declaration's own file always "
                    + "binds; an empty rewrite set is a wiring bug)");
        }

        // the preview pass: splice the spans (replacement text rides each
        // span), re-count by the same enumeration against the new face, and
        // scan for stale old-face residue — any residue means the collection
        // was incomplete, which refuses the rename before anything lands
        boolean symmetric = true;
        for (FileRewrite rewrite : rewrites) {
            ParsedFile original = javaIndex.file(rewrite.path());
            StringBuilder content = new StringBuilder(original.content);
            for (int i = rewrite.spans().size() - 1; i >= 0; i--) {
                RenameSpan span = rewrite.spans().get(i);
                int startChar = original.charIndexAt(span.range().startByte());
                int endChar = original.charIndexAt(span.range().endByte());
                content.replace(startChar, endChar, span.replacement());
            }
            ParsedFile preview = parse(new SymbolResolverAdapter.SourceFile(
                    original.path, content.toString()));
            if (typeResidueScan(preview, declaration.name(), oldFqn)) {
                return RenameResolution.conflict("the rename preview of '"
                        + rewrite.path() + "' still carries old-face residue (import "
                        + "or qualified mention of '" + oldFqn + "'): the collection "
                        + "was incomplete, and landing it would corrupt the file "
                        + "(stale-import check; rejected fail-closed)");
            }
            // recount: the same enumeration against the new face; the
            // declaration re-locates by its unchanged line/column
            int[] declarationPos = original.lineColumnOf(
                    (int) declaration.range().startByte());
            int previewStart = preview.offsetOf(declarationPos[0], declarationPos[1]);
            // the preview declaration keeps the DECLARING file's path so the
            // enumeration's declaration branch fires only on that file
            SymbolDeclaration previewDeclaration = new SymbolDeclaration(newName,
                    new io.nop.lint.core.node.SourceRange(previewStart,
                            previewStart + byteLength(newName)),
                    SymbolKind.TYPE, declaration.path(), newFqn);
            int recounted = typeUseSpans(preview, previewDeclaration, newFqn, null, null)
                    .size();
            if (recounted != rewrite.spans().size()) {
                symmetric = false;
            }
        }
        if (!symmetric) {
            return RenameResolution.conflict("the rename preview is not symmetric "
                    + "(planned span count != re-parsed new-face count) — a "
                    + "collection asymmetry would corrupt code silently, so the "
                    + "rename refuses (fail-closed)");
        }
        String detail = "resolved: " + total + " rewrites across " + rewrites.size()
                + " files";
        return RenameResolution.resolvedMultiFile(declaration, rewrites, true, detail);
    }

    /**
     * The WI11 FIELD/METHOD face (plan 11 adjudications 4/5): the declaring
     * file's bound references plus exactly-static-import files' unqualified
     * uses; the impact surface refuses whenever a receiver-shaped same-name
     * token in a bound file could silently escape the rewrite.
     */
    private RenameResolution memberRename(JavaDeclarationIndex javaIndex,
                                          SymbolDeclaration declaration, String newName,
                                          boolean isField) {
        String memberFqn = declaration.fqn();
        String ownerFqn = declaringTypeName(declaration);
        ParsedFile declaringFile = javaIndex.file(declaration.path());
        int[] declarationPos = declaringFile.lineColumnOf(
                (int) declaration.range().startByte());

        // impact clause (a): a same-kind same-name declaration in the same
        // file (method overload / duplicate field) makes the target ambiguous
        for (JavaDeclaration sibling : javaIndex.declarationsOf(declaringFile.path)) {
            if (sibling.declaration().kind() == declaration.kind()
                    && sibling.declaration().name().equals(declaration.name())
                    && !sibling.declaration().range().equals(declaration.range())) {
                return RenameResolution.conflict("the declaring class already carries "
                        + "another " + declaration.kind() + " '" + declaration.name()
                        + "' (an overload or duplicate makes the target ambiguous; "
                        + "rejected fail-closed)");
            }
        }

        List<String> staticImportFiles = new ArrayList<>();
        List<String> typeImportFiles = new ArrayList<>();
        List<ParsedFile> boundFiles = new ArrayList<>();
        for (ParsedFile f : javaIndex.allFiles()) {
            if (f.path.equals(declaringFile.path)) {
                continue;
            }
            boolean bound = false;
            if (f.packageName.equals(declaringFile.packageName)) {
                bound = true;
                boundFiles.add(f);
            }
            for (var importDecl : f.unit.getImports()) {
                String imported = importDecl.getNameAsString();
                if (importDecl.isStatic()) {
                    if (imported.equals(memberFqn)) {
                        staticImportFiles.add(f.path);
                        if (!bound) {
                            boundFiles.add(f);
                            bound = true;
                        }
                    } else if (imported.endsWith("." + declaration.name())) {
                        // another type's same-named static member imported in
                        // this file: its unqualified uses cannot be bound
                        return RenameResolution.conflict("file '" + f.path
                                + "' imports another type's same-named static member "
                                + "('" + imported + "'): the unqualified uses cannot "
                                + "be bound structurally (rejected fail-closed)");
                    } else if (importDecl.isAsterisk()
                            && memberFqn.startsWith(imported + ".")) {
                        // a wildcard static import of the declaring type: its
                        // unqualified same-name uses are ambiguous
                        String clash = receiverTokenClash(f, declaration.name(),
                                false, true);
                        if (clash != null) {
                            return RenameResolution.conflict(clash);
                        }
                    }
                } else if (imported.equals(ownerFqn)) {
                    typeImportFiles.add(f.path);
                    if (!bound) {
                        boundFiles.add(f);
                        bound = true;
                    }
                }
            }
        }

        // impact clause (b): bound files' receiver-shaped same-name tokens
        for (ParsedFile f : boundFiles) {
            String clash = receiverTokenClash(f, declaration.name(), true, false);
            if (clash != null) {
                return RenameResolution.conflict(clash);
            }
        }

        // rewrite set: declaring file + static-import files' unqualified uses
        List<RenameSpan> declaringSpans = new ArrayList<>();
        declaringSpans.add(new RenameSpan(declaration.range(), newName));
        int[] excludedCounter = {0};
        declaringSpans.addAll(declaringFileBoundRanges(declaringFile, declaration,
                declarationPos, isField, newName, excludedCounter));
        List<FileRewrite> rewrites = new ArrayList<>();
        int total = declaringSpans.size();
        rewrites.add(new FileRewrite(declaringFile.path, declaringSpans));
        for (String path : staticImportFiles) {
            ParsedFile f = javaIndex.file(path);
            List<RenameSpan> spans = unqualifiedUses(f, declaration.name(), newName);
            // the exact static import declaration itself renames its tail
            // segment (plan 11 adjudication 5: import statements sync)
            for (var importDecl : f.unit.getImports()) {
                if (!importDecl.isStatic()
                        || !importDecl.getNameAsString().equals(memberFqn)) {
                    continue;
                }
                Range range = importDecl.getName().getRange().orElse(null);
                if (range != null) {
                    io.nop.lint.core.node.SourceRange full = f.rangeOf(range);
                    int tailBytes = byteLength(declaration.name());
                    spans.add(new RenameSpan(new io.nop.lint.core.node.SourceRange(
                            full.endByte() - tailBytes, full.endByte()), newName));
                }
            }
            if (spans.isEmpty()) {
                continue;
            }
            spans.sort((a, b) -> Integer.compare(a.range().startByte(),
                    b.range().startByte()));
            rewrites.add(new FileRewrite(path, spans));
            total += spans.size();
        }

        boolean symbolIntact = memberPreviewSymmetry(javaIndex, declaration, rewrites,
                newName);
        if (!symbolIntact) {
            return RenameResolution.conflict("the rename preview is not symmetric "
                    + "(planned span count != re-parsed new-name count) — a "
                    + "collection asymmetry would corrupt code silently, so the "
                    + "rename refuses (fail-closed)");
        }
        String detail = "resolved: " + total + " rewrites across " + rewrites.size()
                + " files" + (excludedCounter[0] > 0 ? "; " + excludedCounter[0]
                + " occurrences excluded by unresolvable positions" : "");
        return RenameResolution.resolvedMultiFile(declaration, rewrites, symbolIntact,
                detail);
    }

    private String declaringTypeName(SymbolDeclaration declaration) {
        String memberFqn = declaration.fqn();
        int dot = memberFqn.lastIndexOf('.');
        return dot < 0 ? memberFqn : memberFqn.substring(0, dot);
    }

    private List<RenameSpan> sortedSpans(List<RenameSpan> spans) {
        List<RenameSpan> sorted = new ArrayList<>(spans);
        sorted.sort((a, b) -> Integer.compare(a.range().startByte(),
                b.range().startByte()));
        return sorted;
    }

    /**
     * The declaring file's own bound references (plan 11 adjudication 4):
     * fields ride definitionOf-bound NameExpr uses plus {@code this.f} tails;
     * methods ride unqualified/this-scoped call names inside the declaring
     * class (definitionOf has no model for method-name positions).
     */
    private List<RenameSpan> declaringFileBoundRanges(ParsedFile file,
                                                      SymbolDeclaration declaration,
                                                      int[] declarationPos,
                                                      boolean isField, String newName,
                                                      int[] excludedCounter) {
        List<RenameSpan> spans = new ArrayList<>();
        if (isField) {
            for (NameExpr nameExpr : file.unit.findAll(NameExpr.class)) {
                if (!nameExpr.getNameAsString().equals(declaration.name())) {
                    continue;
                }
                Range range = nameExpr.getRange().orElse(null);
                if (range == null) {
                    continue;
                }
                int[] pos = file.lineColumnOf(file.offsetOf(range.begin.line,
                        range.begin.column));
                ScopeAnalyzer.Definition def = definitionQuietly(file, pos);
                if (def != null && def.line() == declarationPos[0]
                        && def.column() == declarationPos[1]) {
                    spans.add(new RenameSpan(file.rangeOf(range), newName));
                } else if (def == null) {
                    excludedCounter[0]++;
                }
            }
        }
        for (com.github.javaparser.ast.expr.MethodCallExpr call : file.unit
                .findAll(com.github.javaparser.ast.expr.MethodCallExpr.class)) {
            if (!call.getNameAsString().equals(declaration.name())) {
                continue;
            }
            boolean unqualifiedOrThis = call.getScope().isEmpty()
                    || call.getScope().get().isThisExpr();
            if (unqualifiedOrThis && inDeclaringClass(call, declaringTypeName(declaration))) {
                Range range = call.getName().getRange().orElse(null);
                if (range != null) {
                    spans.add(new RenameSpan(file.rangeOf(range), newName));
                }
            }
        }
        for (com.github.javaparser.ast.expr.FieldAccessExpr access : file.unit
                .findAll(com.github.javaparser.ast.expr.FieldAccessExpr.class)) {
            if (!access.getNameAsString().equals(declaration.name())) {
                continue;
            }
            if (access.getScope().isThisExpr()
                    && inDeclaringClass(access, declaringTypeName(declaration))) {
                Range range = access.getName().getRange().orElse(null);
                if (range != null) {
                    spans.add(new RenameSpan(file.rangeOf(range), newName));
                }
            }
        }
        return spans;
    }

    private boolean inDeclaringClass(com.github.javaparser.ast.Node node,
                                     String ownerFqn) {
        com.github.javaparser.ast.body.TypeDeclaration<?> owner = node
                .findAncestor(com.github.javaparser.ast.body.TypeDeclaration.class)
                .orElse(null);
        return owner != null && owner.getFullyQualifiedName()
                .map(fqn -> fqn.equals(ownerFqn)).orElse(false);
    }

    private ScopeAnalyzer.Definition definitionQuietly(ParsedFile file, int[] pos) {
        try {
            return scopeAnalyzer.definitionOf(file.unit, pos[0], pos[1]);
        } catch (io.nop.lint.core.NopLintException e) {
            return null;
        }
    }

    /**
     * Impact clauses (b)/(c): receiver-shaped same-name tokens (scoped method
     * calls, non-this field accesses) in a bound file are structurally
     * unbindable; a wildcard-static file's unqualified same-name token is
     * ambiguous. Unbound files' tokens are structurally unreachable.
     */
    private String receiverTokenClash(ParsedFile file, String name, boolean bound,
                                      boolean wildcardStatic) {
        boolean wildcardHit = false;
        for (com.github.javaparser.ast.expr.MethodCallExpr call : file.unit
                .findAll(com.github.javaparser.ast.expr.MethodCallExpr.class)) {
            if (!call.getNameAsString().equals(name)) {
                continue;
            }
            if (call.getScope().isEmpty()) {
                if (wildcardStatic) {
                    wildcardHit = true;
                }
                continue;
            }
            if (wildcardStatic && bound) {
                wildcardHit = true;
            }
            if (bound && !call.getScope().get().isThisExpr()) {
                return "bound file '" + file.path + "' carries a receiver-shaped "
                        + "call '" + name + "(...)' the structural index cannot bind "
                        + "(a silent miss is worse than a refusal; rejected fail-closed)";
            }
        }
        for (com.github.javaparser.ast.expr.FieldAccessExpr access : file.unit
                .findAll(com.github.javaparser.ast.expr.FieldAccessExpr.class)) {
            if (!access.getNameAsString().equals(name)) {
                continue;
            }
            if (access.getScope().isThisExpr()) {
                continue;
            }
            if (bound) {
                return "bound file '" + file.path + "' carries a receiver-shaped "
                        + "field access '" + name + "' the structural index cannot "
                        + "bind (rejected fail-closed)";
            }
        }
        for (NameExpr nameExpr : file.unit.findAll(NameExpr.class)) {
            if (nameExpr.getNameAsString().equals(name) && wildcardStatic) {
                wildcardHit = true;
            }
        }
        if (wildcardHit) {
            return "file '" + file.path + "' wildcard-imports the declaring type's "
                    + "static members and carries an unqualified '" + name
                    + "' use (ambiguous binding; rejected fail-closed)";
        }
        return null;
    }

    /**
     * The unqualified same-name token face of an exactly-static-import file
     * (its import binds those uses to this member).
     */
    private List<RenameSpan> unqualifiedUses(ParsedFile file, String name,
                                             String newName) {
        List<RenameSpan> uses = new ArrayList<>();
        for (NameExpr nameExpr : file.unit.findAll(NameExpr.class)) {
            if (nameExpr.getNameAsString().equals(name)) {
                Range range = nameExpr.getRange().orElse(null);
                if (range != null) {
                    uses.add(new RenameSpan(file.rangeOf(range), newName));
                }
            }
        }
        for (com.github.javaparser.ast.expr.MethodCallExpr call : file.unit
                .findAll(com.github.javaparser.ast.expr.MethodCallExpr.class)) {
            if (call.getScope().isEmpty() && call.getNameAsString().equals(name)) {
                Range range = call.getName().getRange().orElse(null);
                if (range != null) {
                    uses.add(new RenameSpan(file.rangeOf(range), newName));
                }
            }
        }
        return uses;
    }

    /**
     * Whether a file binds a TYPE declaration (plan 11 adjudications 1/6):
     * the declaring file, same package, exact type import, dot-boundary
     * prefix import (nested types), or the declaring package's wildcard.
     */
    private boolean bindsType(ParsedFile file, SymbolDeclaration declaration) {
        if (file.path.equals(declaration.path())) {
            return true;
        }
        String oldFqn = declaration.fqn();
        String declaringPackage = oldFqn.substring(0,
                oldFqn.length() - declaration.name().length() - 1);
        if (file.packageName.equals(declaringPackage)) {
            return true;
        }
        for (var importDecl : file.unit.getImports()) {
            if (importDecl.isStatic()) {
                continue;
            }
            String imported = importDecl.getNameAsString();
            if (imported.equals(oldFqn)
                    || imported.startsWith(oldFqn + ".")
                    || (importDecl.isAsterisk() && imported.equals(declaringPackage))) {
                return true;
            }
        }
        return false;
    }

    /**
     * A bound file that declares a same-named variable (local, parameter or
     * field) makes its type uses structurally ambiguous (plan 11
     * adjudication 1): the whole file refuses.
     */
    private String typeUseShadowClash(ParsedFile file, SymbolDeclaration declaration) {
        for (com.github.javaparser.ast.body.VariableDeclarator declarator : file.unit
                .findAll(com.github.javaparser.ast.body.VariableDeclarator.class)) {
            if (declarator.getNameAsString().equals(declaration.name())) {
                return "bound file '" + file.path + "' declares a variable '"
                        + declaration.name() + "': its type uses cannot be told "
                        + "from the variable's uses structurally (rejected "
                        + "fail-closed)";
            }
        }
        for (com.github.javaparser.ast.body.Parameter parameter : file.unit
                .findAll(com.github.javaparser.ast.body.Parameter.class)) {
            if (parameter.getNameAsString().equals(declaration.name())) {
                return "bound file '" + file.path + "' declares a parameter '"
                        + declaration.name() + "': its type uses cannot be told "
                        + "from the parameter's uses structurally (rejected "
                        + "fail-closed)";
            }
        }
        return null;
    }

    /**
     * The TYPE rewrite spans of one bound file (plan 11 adjudication 1):
     * the declaration identifier and constructors, simple-name type uses,
     * expression scopes, exact/prefix imports, and dot-boundary qualified
     * mentions (import/package subtrees excluded so the import face stays
     * single-owned). When {@code matchFqn} is null the enumeration runs
     * against the new face (the preview recount).
     */
    private List<RenameSpan> typeUseSpans(ParsedFile file,
                                          SymbolDeclaration declaration, String fqn,
                                          String replacementFqn, String newName) {
        String matchName = declaration.name();
        boolean newFace = replacementFqn == null;
        String replacement = newFace ? matchName : newName;
        List<RenameSpan> spans = new ArrayList<>();
        // declaration identifier + the type's constructors in this file
        if (file.path.equals(declaration.path())) {
            spans.add(new RenameSpan(declaration.range(), replacement));
            for (com.github.javaparser.ast.body.ConstructorDeclaration constructor : file.unit
                    .findAll(com.github.javaparser.ast.body.ConstructorDeclaration.class)) {
                if (!constructor.getNameAsString().equals(matchName)) {
                    continue;
                }
                if (!inDeclaringClass(constructor, fqn.substring(0,
                        fqn.length() - matchName.length()))) {
                    continue;
                }
                Range range = constructor.getName().getRange().orElse(null);
                if (range != null) {
                    spans.add(new RenameSpan(file.rangeOf(range), replacement));
                }
            }
        }
        for (com.github.javaparser.ast.type.ClassOrInterfaceType type : file.unit
                .findAll(com.github.javaparser.ast.type.ClassOrInterfaceType.class)) {
            if (!type.getNameAsString().equals(matchName)) {
                continue;
            }
            Range range = type.getName().getRange().orElse(null);
            if (range != null) {
                spans.add(new RenameSpan(file.rangeOf(range), replacement));
            }
        }
        for (NameExpr nameExpr : file.unit.findAll(NameExpr.class)) {
            if (nameExpr.getNameAsString().equals(matchName)) {
                Range range = nameExpr.getRange().orElse(null);
                if (range != null) {
                    spans.add(new RenameSpan(file.rangeOf(range), replacement));
                }
            }
        }
        String matchFqn = fqn;
        for (var importDecl : file.unit.getImports()) {
            if (importDecl.isStatic()) {
                continue;
            }
            String imported = importDecl.getNameAsString();
            String replacementText = null;
            if (imported.equals(matchFqn)) {
                replacementText = replacementFqn == null ? matchName : replacementFqn;
            } else if (replacementFqn != null && imported.startsWith(matchFqn + ".")) {
                replacementText = replacementFqn + imported.substring(matchFqn.length());
            }
            // (the new-face recount rewrites the import's last segment with
            // the simple name, symmetric with the old face's full-prefix span
            // only in count — the count symmetry is what the assertion reads)
            if (replacementText != null) {
                Range range = importDecl.getName().getRange().orElse(null);
                if (range != null) {
                    io.nop.lint.core.node.SourceRange full = file.rangeOf(range);
                    // the span covers only the matched prefix (dot boundary)
                    int prefixBytes = byteLength(matchFqn);
                    spans.add(new RenameSpan(new io.nop.lint.core.node.SourceRange(
                            full.startByte(), full.startByte() + prefixBytes),
                            replacementText));
                }
            }
        }
        for (Name name : file.unit.findAll(Name.class)) {
            if (insideImportOrPackage(name)) {
                continue;
            }
            String qualified = name.asString();
            if (qualified.equals(matchFqn)) {
                spans.add(new RenameSpan(nameRange(file, name, matchFqn),
                        newFace ? replacement : replacementFqn));
            } else if (qualified.startsWith(matchFqn + ".")) {
                spans.add(new RenameSpan(nameRange(file, name, matchFqn),
                        newFace ? replacement
                                : replacementFqn + qualified.substring(matchFqn.length())));
            }
        }
        for (com.github.javaparser.ast.expr.FieldAccessExpr access : file.unit
                .findAll(com.github.javaparser.ast.expr.FieldAccessExpr.class)) {
            String flattened = flatten(access);
            if (flattened == null) {
                continue;
            }
            if (flattened.equals(matchFqn)) {
                Range scopeRange = access.getScope().getRange().orElse(null);
                if (scopeRange != null) {
                    spans.add(new RenameSpan(file.rangeOf(scopeRange),
                            newFace ? replacement : replacementFqn));
                }
            } else if (flattened.startsWith(matchFqn + ".")) {
                // the chain's old-name segment sits mid-chain (a.Service.Foo):
                // rewrite that segment's own name node with the simple new name
                com.github.javaparser.ast.expr.FieldAccessExpr segment = access;
                while (segment != null
                        && !segment.getNameAsString().equals(matchName)) {
                    segment = segment.getScope() instanceof com.github.javaparser.ast.expr.FieldAccessExpr inner
                            ? inner : null;
                }
                if (segment != null) {
                    Range segmentRange = segment.getName().getRange().orElse(null);
                    if (segmentRange != null) {
                        spans.add(new RenameSpan(file.rangeOf(segmentRange), replacement));
                    }
                }
            }
        }
        spans.sort((a, b) -> Integer.compare(a.range().startByte(),
                b.range().startByte()));
        return spans;
    }

    /**
     * The stale-face scan of one preview file (plan 11 adjudication 7): any
     * surviving old-FQN import or qualified mention means the collection was
     * incomplete.
     */
    private boolean typeResidueScan(ParsedFile preview, String oldName, String oldFqn) {
        for (var importDecl : preview.unit.getImports()) {
            if (importDecl.isStatic()) {
                continue;
            }
            String imported = importDecl.getNameAsString();
            if (imported.equals(oldFqn) || imported.startsWith(oldFqn + ".")) {
                return true;
            }
        }
        for (Name name : preview.unit.findAll(Name.class)) {
            if (insideImportOrPackage(name)) {
                continue;
            }
            String qualified = name.asString();
            if (qualified.equals(oldFqn) || qualified.startsWith(oldFqn + ".")) {
                return true;
            }
        }
        return false;
    }

    /**
     * The fully-qualified mention face of an UNBOUND file (plan 11
     * adjudication 1's unbound branch): qualified type uses and Name/
     * FieldAccess mention chains referencing the old FQN need no import, so
     * they rewrite even where the simple name would not bind.
     */
    private List<RenameSpan> qualifiedMentionSpans(ParsedFile file,
                                                   SymbolDeclaration declaration,
                                                   String oldFqn, String newFqn,
                                                   String newName) {
        List<RenameSpan> spans = new ArrayList<>();
        for (com.github.javaparser.ast.type.ClassOrInterfaceType type : file.unit
                .findAll(com.github.javaparser.ast.type.ClassOrInterfaceType.class)) {
            if (!type.getNameAsString().equals(declaration.name())
                    || type.getScope().isEmpty()) {
                continue;
            }
            String qualified = type.getScope().get().asString() + "."
                    + type.getNameAsString();
            if (qualified.equals(oldFqn)) {
                Range range = type.getName().getRange().orElse(null);
                if (range != null) {
                    spans.add(new RenameSpan(file.rangeOf(range), newName));
                }
            }
        }
        for (Name name : file.unit.findAll(Name.class)) {
            if (insideImportOrPackage(name)) {
                continue;
            }
            String qualified = name.asString();
            if (qualified.equals(oldFqn)) {
                spans.add(new RenameSpan(nameRange(file, name, oldFqn), newFqn));
            } else if (qualified.startsWith(oldFqn + ".")) {
                spans.add(new RenameSpan(nameRange(file, name, oldFqn),
                        newFqn + qualified.substring(oldFqn.length())));
            }
        }
        for (com.github.javaparser.ast.expr.FieldAccessExpr access : file.unit
                .findAll(com.github.javaparser.ast.expr.FieldAccessExpr.class)) {
            String flattened = flatten(access);
            if (flattened != null && flattened.equals(oldFqn)) {
                Range scopeRange = access.getScope().getRange().orElse(null);
                if (scopeRange != null) {
                    spans.add(new RenameSpan(file.rangeOf(scopeRange), newFqn));
                }
            }
        }
        spans.sort((a, b) -> Integer.compare(a.range().startByte(),
                b.range().startByte()));
        return spans;
    }

    private int byteLength(String text) {
        return text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    private io.nop.lint.core.node.SourceRange nameRange(ParsedFile file, Name name,
                                                        String matchFqn) {
        Range range = name.getRange().orElse(null);
        int start = file.offsetOf(range.begin.line, range.begin.column);
        return new io.nop.lint.core.node.SourceRange(start,
                start + byteLength(matchFqn));
    }

    private boolean insideImportOrPackage(com.github.javaparser.ast.Node node) {
        com.github.javaparser.ast.Node current = node;
        while (current != null) {
            if (current instanceof com.github.javaparser.ast.ImportDeclaration
                    || current instanceof com.github.javaparser.ast.PackageDeclaration) {
                return true;
            }
            current = current.getParentNode().orElse(null);
        }
        return false;
    }

    private String flatten(com.github.javaparser.ast.expr.FieldAccessExpr access) {
        StringBuilder chain = new StringBuilder();
        com.github.javaparser.ast.Node current = access;
        while (current instanceof com.github.javaparser.ast.expr.FieldAccessExpr fa) {
            chain.insert(0, "." + fa.getNameAsString());
            current = fa.getScope();
        }
        if (current instanceof NameExpr nameExpr) {
            chain.insert(0, nameExpr.getNameAsString());
            return chain.toString();
        }
        if (current instanceof Name name) {
            chain.insert(0, name.asString());
            return chain.toString();
        }
        return null;
    }

    /**
     * The member-face preview symmetry: per rewritten file, the planned span
     * count must equal the new-name declaration + use count on the preview
     * (declarations counted on the declaring file, uses on every file —
     * kind-shaped like the collection so the two faces stay symmetric).
     */
    private boolean memberPreviewSymmetry(JavaDeclarationIndex javaIndex,
                                          SymbolDeclaration declaration,
                                          List<FileRewrite> rewrites, String newName) {
        String ownerFqn = declaringTypeName(declaration);
        boolean isField = declaration.kind() == SymbolKind.FIELD;
        for (FileRewrite rewrite : rewrites) {
            ParsedFile original = javaIndex.file(rewrite.path());
            StringBuilder content = new StringBuilder(original.content);
            for (int i = rewrite.spans().size() - 1; i >= 0; i--) {
                RenameSpan span = rewrite.spans().get(i);
                int startChar = original.charIndexAt(span.range().startByte());
                int endChar = original.charIndexAt(span.range().endByte());
                content.replace(startChar, endChar, span.replacement());
            }
            ParsedFile preview = parse(new SymbolResolverAdapter.SourceFile(
                    original.path, content.toString()));
            boolean declaring = rewrite.path().equals(declaration.path());
            int recounted = 0;
            if (isField) {
                if (declaring) {
                    for (com.github.javaparser.ast.body.VariableDeclarator declarator : preview.unit
                            .findAll(com.github.javaparser.ast.body.VariableDeclarator.class)) {
                        if (declarator.getNameAsString().equals(newName)
                                && inDeclaringClass(declarator, ownerFqn)) {
                            recounted++;
                        }
                    }
                    for (com.github.javaparser.ast.expr.FieldAccessExpr access : preview.unit
                            .findAll(com.github.javaparser.ast.expr.FieldAccessExpr.class)) {
                        if (access.getNameAsString().equals(newName)
                                && access.getScope().isThisExpr()) {
                            recounted++;
                        }
                    }
                }
                for (NameExpr nameExpr : preview.unit.findAll(NameExpr.class)) {
                    if (nameExpr.getNameAsString().equals(newName)) {
                        recounted++;
                    }
                }
                if (!declaring) {
                    // the static import declaration's own tail segment
                    for (var importDecl : preview.unit.getImports()) {
                        if (importDecl.isStatic() && importDecl.getNameAsString()
                                .endsWith("." + newName)) {
                            recounted++;
                        }
                    }
                }
            } else {
                if (declaring) {
                    for (com.github.javaparser.ast.body.MethodDeclaration method : preview.unit
                            .findAll(com.github.javaparser.ast.body.MethodDeclaration.class)) {
                        if (method.getNameAsString().equals(newName)
                                && inDeclaringClass(method, ownerFqn)) {
                            recounted++;
                        }
                    }
                    for (com.github.javaparser.ast.expr.MethodCallExpr call : preview.unit
                            .findAll(com.github.javaparser.ast.expr.MethodCallExpr.class)) {
                        if (call.getNameAsString().equals(newName)
                                && call.getScope().isPresent()
                                && call.getScope().get().isThisExpr()) {
                            recounted++;
                        }
                    }
                }
                for (com.github.javaparser.ast.expr.MethodCallExpr call : preview.unit
                        .findAll(com.github.javaparser.ast.expr.MethodCallExpr.class)) {
                    if (call.getNameAsString().equals(newName)
                            && call.getScope().isEmpty()) {
                        recounted++;
                    }
                }
                if (!declaring) {
                    for (var importDecl : preview.unit.getImports()) {
                        if (importDecl.isStatic() && importDecl.getNameAsString()
                                .endsWith("." + newName)) {
                            recounted++;
                        }
                    }
                }
            }
            if (rewrite.spans().size() != recounted) {
                return false;
            }
        }
        return true;
    }

    private String methodBoundaryClash(ParsedFile file, SymbolDeclaration declaration,
                                       int[] declarationPos, String newName) {
        io.nop.lint.core.node.SourceRange declarationRange = declaration.range();
        // the innermost containing method wins (plan 11: anonymous/local-class
        // nesting must not attribute the clash to the outer method)
        com.github.javaparser.ast.body.MethodDeclaration innermostMethod = null;
        io.nop.lint.core.node.SourceRange innermostRange = null;
        for (com.github.javaparser.ast.body.MethodDeclaration method : file.unit
                .findAll(com.github.javaparser.ast.body.MethodDeclaration.class)) {
            Range methodRange = method.getRange().orElse(null);
            if (methodRange == null) {
                continue;
            }
            io.nop.lint.core.node.SourceRange methodSourceRange = file.rangeOf(methodRange);
            if (!methodSourceRange.contains(declarationRange.startByte())) {
                continue;
            }
            if (innermostRange == null
                    || methodSourceRange.endByte() - methodSourceRange.startByte()
                    < innermostRange.endByte() - innermostRange.startByte()) {
                innermostMethod = method;
                innermostRange = methodSourceRange;
            }
        }
        if (innermostMethod != null) {
            com.github.javaparser.ast.body.MethodDeclaration method = innermostMethod;
            for (com.github.javaparser.ast.body.Parameter parameter : method
                    .getParameters()) {
                if (parameter.getNameAsString().equals(newName)
                        && !isTheDeclarationItself(parameter.getRange().orElse(null),
                        declarationPos)) {
                    return "the enclosing method already declares a parameter '"
                            + newName + "' (a same-scope clash is a compile error; "
                            + "rejected fail-closed)";
                }
            }
            for (com.github.javaparser.ast.body.VariableDeclarator declarator : method
                    .findAll(com.github.javaparser.ast.body.VariableDeclarator.class)) {
                if (declarator.getNameAsString().equals(newName)
                        && !isTheDeclarationItself(declarator.getRange().orElse(null),
                        declarationPos)) {
                    return "the enclosing method already declares a local '" + newName
                            + "' in a sibling or nested block (an inner block would "
                            + "re-capture the renamed references; rejected fail-closed)";
                }
            }
            return null;
        }
        for (com.github.javaparser.ast.body.ConstructorDeclaration constructor : file.unit
                .findAll(com.github.javaparser.ast.body.ConstructorDeclaration.class)) {
            Range constructorRange = constructor.getRange().orElse(null);
            if (constructorRange == null
                    || !file.rangeOf(constructorRange).contains(declarationRange.startByte())) {
                continue;
            }
            for (com.github.javaparser.ast.body.Parameter parameter : constructor
                    .getParameters()) {
                if (parameter.getNameAsString().equals(newName)
                        && !isTheDeclarationItself(parameter.getRange().orElse(null),
                        declarationPos)) {
                    return "the enclosing constructor already declares a parameter '"
                            + newName + "' (a same-scope clash is a compile error; "
                            + "rejected fail-closed)";
                }
            }
            for (com.github.javaparser.ast.body.VariableDeclarator declarator : constructor
                    .findAll(com.github.javaparser.ast.body.VariableDeclarator.class)) {
                if (declarator.getNameAsString().equals(newName)
                        && !isTheDeclarationItself(declarator.getRange().orElse(null),
                        declarationPos)) {
                    return "the enclosing constructor already declares a local '"
                            + newName + "' in a sibling or nested block (rejected "
                            + "fail-closed)";
                }
            }
            return null;
        }
        throw new io.nop.refactor.core.NopRefactorException("the variable declaration '"
                + declaration.name() + "' in '" + file.path + "' has no enclosing method "
                + "or constructor (a first-rung rename target is always method-scoped; "
                + "fail-closed)");
    }

    private boolean isTheDeclarationItself(Range nodeRange, int[] declarationPos) {
        // identity by start position: the declaration's own identifier is the
        // only same-name node whose range starts exactly at the declaration
        return nodeRange != null
                && nodeRange.begin.line == declarationPos[0]
                && nodeRange.begin.column == declarationPos[1];
    }

    /**
     * The bound occurrence set (plan 10 adjudication 4): NameExpr identifiers
     * matching the old name whose {@code definitionOf} resolves to exactly
     * the declaration's position — method calls, field accesses and type
     * names never enter the set, so the rewrite cannot corrupt them.
     */
    private List<io.nop.lint.core.node.SourceRange> boundOccurrences(
            ParsedFile file, String name, int[] declarationPos) {
        List<io.nop.lint.core.node.SourceRange> bound = new ArrayList<>();
        for (NameExpr nameExpr : file.unit.findAll(NameExpr.class)) {
            if (!nameExpr.getNameAsString().equals(name)) {
                continue;
            }
            Range range = nameExpr.getRange().orElse(null);
            if (range == null) {
                continue;
            }
            int[] pos = file.lineColumnOf(file.offsetOf(range.begin.line, range.begin.column));
            ScopeAnalyzer.Definition definition;
            try {
                definition = scopeAnalyzer.definitionOf(file.unit, pos[0], pos[1]);
            } catch (io.nop.lint.core.NopLintException e) {
                continue;
            }
            if (definition != null && definition.line() == declarationPos[0]
                    && definition.column() == declarationPos[1]) {
                bound.add(file.rangeOf(range));
            }
        }
        bound.sort((a, b) -> Integer.compare(a.startByte(), b.startByte()));
        return bound;
    }

    /**
     * The symbol-intact preview (plan 10 adjudication 5): splices the new
     * name over the collected ranges (end-first so earlier offsets survive),
     * re-parses the planned content, and counts the bound new-name NameExpr
     * occurrences against the unchanged declaration position.
     */
    private boolean symbolIntactOnPreview(ParsedFile file, SymbolDeclaration declaration,
                                          int[] declarationPos,
                                          List<io.nop.lint.core.node.SourceRange> occurrences,
                                          String newName) {
        // the preview renames the declaration identifier too — an unrenamed
        // declaration would leave the new-name occurrences unresolvable and
        // fake a broken assertion (the plan's N==M symmetry needs the whole
        // rewrite set on both sides)
        List<io.nop.lint.core.node.SourceRange> rewrites = new ArrayList<>(
                occurrences.size() + 1);
        rewrites.add(declaration.range());
        rewrites.addAll(occurrences);
        rewrites.sort((a, b) -> Integer.compare(a.startByte(), b.startByte()));
        String content = file.content;
        for (int i = rewrites.size() - 1; i >= 0; i--) {
            io.nop.lint.core.node.SourceRange range = rewrites.get(i);
            content = content.substring(0, file.charIndexAt(range.startByte()))
                    + newName
                    + content.substring(file.charIndexAt(range.endByte()));
        }
        ParsedFile preview = parse(new SymbolResolverAdapter.SourceFile(file.path, content));
        int rebound = 0;
        for (NameExpr nameExpr : preview.unit.findAll(NameExpr.class)) {
            if (!nameExpr.getNameAsString().equals(newName)) {
                continue;
            }
            Range range = nameExpr.getRange().orElse(null);
            if (range == null) {
                continue;
            }
            int[] pos = preview.lineColumnOf(
                    preview.offsetOf(range.begin.line, range.begin.column));
            ScopeAnalyzer.Definition definition;
            try {
                definition = scopeAnalyzer.definitionOf(preview.unit, pos[0], pos[1]);
            } catch (io.nop.lint.core.NopLintException e) {
                continue;
            }
            if (definition != null && definition.line() == declarationPos[0]
                    && definition.column() == declarationPos[1]) {
                rebound++;
            }
        }
        return occurrences.size() == rebound;
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
        private final Map<String, List<JavaDeclaration>> byFile = new HashMap<>();
        private final Map<String, JavaDeclaration> byFqn = new HashMap<>();

        @Override
        public int fileCount() {
            return files.size();
        }

        void addFile(SourceFile file) {
            ParsedFile parsed = parse(file);
            files.put(file.path(), parsed);

            List<JavaDeclaration> declarations = collectDeclarations(parsed);
            byFile.put(file.path(), declarations);
            for (JavaDeclaration declaration : declarations) {
                byName.computeIfAbsent(declaration.declaration().name(),
                                k -> new ArrayList<>())
                        .add(declaration);
                // only TYPE declarations resolve through the FQN locator
                // (plan 11 adjudication 3); member FQNs exist for the static
                // import match and must not enter the type lookup
                if (declaration.declaration().hasFqn()
                        && declaration.declaration().kind() == SymbolKind.TYPE) {
                    byFqn.put(declaration.declaration().fqn(), declaration);
                }
            }
        }

        ParsedFile file(String path) {
            ParsedFile parsed = files.get(path);
            if (parsed == null) {
                throw new io.nop.refactor.core.NopRefactorException("path '" + path
                        + "' is not part of the indexed module (the caller hands the "
                        + "files the index was built from; fail-closed)");
            }
            return parsed;
        }

        /**
         * The rename locator (plan 10 adjudication 2): the index's byte-range
         * containment — not definitionOf, which cannot see method/type name
         * positions — decides both existence and kind.
         */
        SymbolDeclaration declarationContaining(String path, long byteOffset) {
            for (JavaDeclaration candidate : byFile.getOrDefault(path, List.of())) {
                if (candidate.declaration().range().contains((int) byteOffset)) {
                    return candidate.declaration();
                }
            }
            return null;
        }

        /**
         * The FQN locator (plan 11 adjudication 3 for types; the WI11 member
         * face extends it: FIELD/METHOD index entries carry
         * declaringType.member FQNs, so both faces locate by FQN — the kind
         * dispatch, not the table, decides the rung).
         */
        SymbolDeclaration declarationByFqn(String fqn) {
            JavaDeclaration hit = byFqn.get(fqn);
            if (hit != null) {
                return hit.declaration();
            }
            for (List<JavaDeclaration> declarations : byFile.values()) {
                for (JavaDeclaration candidate : declarations) {
                    if (candidate.declaration().fqn().equals(fqn)) {
                        return candidate.declaration();
                    }
                }
            }
            return null;
        }

        /** All indexed parsed files (the cross-file enumeration face). */
        java.util.Collection<ParsedFile> allFiles() {
            return files.values();
        }

        /** The declarations of one indexed file (the cross-file scan face). */
        List<JavaDeclaration> declarationsOf(String path) {
            return byFile.getOrDefault(path, List.of());
        }

        java.util.Set<String> fieldNames(String path) {
            java.util.Set<String> names = new java.util.HashSet<>();
            for (JavaDeclaration candidate : byFile.getOrDefault(path, List.of())) {
                if (candidate.declaration().kind() == SymbolKind.FIELD) {
                    names.add(candidate.declaration().name());
                }
            }
            return names;
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

            parsed.unit.findAll(FieldDeclaration.class).forEach(field -> {
                String ownerFqn = enclosingTypeFqn(field, parsed);
                field.getVariables().forEach(declarator ->
                        addDeclaration(result, parsed, packageName,
                                declarator.getName(), SymbolKind.FIELD,
                                ownerFqn.isEmpty() ? "" : ownerFqn + "." + declarator.getNameAsString()));
            });
            parsed.unit.findAll(MethodDeclaration.class).forEach(method -> {
                String ownerFqn = enclosingTypeFqn(method, parsed);
                addDeclaration(result, parsed, packageName, method.getName(),
                        SymbolKind.METHOD,
                        ownerFqn.isEmpty() ? "" : ownerFqn + "." + method.getNameAsString());
            });
            parsed.unit.findAll(ConstructorDeclaration.class).forEach(constructor ->
                    addDeclaration(result, parsed, packageName, constructor.getName(),
                            SymbolKind.CONSTRUCTOR, enclosingTypeFqn(constructor, parsed)));

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

        /**
         * The enclosing type's FQN for a member declaration (plan 11
         * adjudication 5): the static-import match needs the member's
         * declaring type, so FIELD/METHOD index entries carry
         * {@code declaringTypeFqn.memberName}.
         */
        private String enclosingTypeFqn(com.github.javaparser.ast.Node node, ParsedFile parsed) {
            com.github.javaparser.ast.body.TypeDeclaration<?> owner = node
                    .findAncestor(com.github.javaparser.ast.body.TypeDeclaration.class)
                    .orElse(null);
            return owner == null ? "" : owner.getFullyQualifiedName().orElse("");
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

        int charIndexAt(int byteOffset) {
            return charIndexOf(byteOffset);
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
