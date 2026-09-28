package io.nop.code.lang.go;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import io.nop.treesitter.compat.TSNode;
import io.nop.treesitter.compat.TSParser;
import io.nop.treesitter.compat.TSTree;
import io.nop.treesitter.compat.TreeSitterGo;

import io.nop.code.core.analyzer.ICodeFileAnalyzer;
import io.nop.code.core.model.CodeAccessModifier;
import io.nop.code.core.model.CodeFileAnalysisResult;
import io.nop.code.core.model.CodeInheritance;
import io.nop.code.core.model.CodeLanguage;
import io.nop.code.core.model.CodeMethodCall;
import io.nop.code.core.model.CodeRelationType;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import io.nop.code.core.model.EdgeProvenance;
import io.nop.code.core.semantic.EdgeConfidence;

/**
 * Go 文件分析器。
 * 使用 nop-treesitter 纯 Java 运行时解析 Go 源代码，提取符号（函数/方法/结构体/接口/
 * 类型别名/常量）、嵌入继承（struct 嵌入 field_declaration、interface 嵌入 type_elem）、
 * 调用与 import 信息。
 *
 * <p>go grammar 形状：method 名 = field {@code name} 的 {@code field_identifier}；
 * struct 嵌入 = 无 name 的 {@code field_declaration}；interface 嵌入 = interface_type
 * 直接子级 {@code type_elem}（generic_type 内的 type_elem 不算嵌入）；接口方法 =
 * {@code method_elem}。
 */
public class GoCodeFileAnalyzer implements ICodeFileAnalyzer {

    private static final TreeSitterGo TS_LANGUAGE = new TreeSitterGo();

    @Override
    public CodeLanguage getLanguage() {
        return CodeLanguage.GO;
    }

    @Override
    public List<String> getFileExtensions() {
        return java.util.Collections.singletonList(".go");
    }

    @Override
    public CodeFileAnalysisResult analyze(String filePath, String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return null;
        }

        TSParser parser = new TSParser();
        parser.setLanguage(TS_LANGUAGE);

        TSTree tree = parser.parseString(null, sourceCode);
        if (tree == null) {
            return null;
        }

        TSNode root = tree.getRootNode();
        CodeFileAnalysisResult result = new CodeFileAnalysisResult();
        result.setFilePath(filePath);
        result.setSourceCode(sourceCode);
        result.setLineCount(countLines(sourceCode));
        result.setLanguage(CodeLanguage.GO);

        String packageName = findPackageName(root, sourceCode);
        result.setPackageName(packageName);

        WalkContext ctx = new WalkContext(sourceCode, packageName, result);
        walkNode(root, null, ctx);

        tree = null;
        return result;
    }

    // ---- walking ----

    private static final class WalkContext {
        final String source;
        final String packageName;
        final CodeFileAnalysisResult result;
        CodeSymbol currentType;

        WalkContext(String source, String packageName, CodeFileAnalysisResult result) {
            this.source = source;
            this.packageName = packageName;
            this.result = result;
        }

        String qn(String name) {
            if (packageName == null || packageName.isEmpty()) {
                return name;
            }
            return packageName + "." + name;
        }

        String typeQn(String typeText) {
            String text = typeText.trim();
            if (text.indexOf('.') > 0 || packageName == null || packageName.isEmpty()) {
                return text;
            }
            return packageName + "." + text;
        }
    }

    private void walkNode(TSNode node, CodeSymbol parentSymbol, WalkContext ctx) {
        String type = node.getType();

        if ("package_clause".equals(type)) {
            // package name is handled by findPackageName; nothing per-symbol
        } else if ("import_declaration".equals(type)) {
            visitImportDeclaration(node, ctx);
            return;
        } else if ("function_declaration".equals(type)) {
            visitFunctionDeclaration(node, ctx);
            return;
        } else if ("method_declaration".equals(type)) {
            visitMethodDeclaration(node, ctx);
            return;
        } else if ("type_declaration".equals(type)) {
            visitTypeDeclaration(node, ctx);
            return;
        } else if ("const_declaration".equals(type)) {
            visitConstDeclaration(node, ctx);
            return;
        } else if ("call_expression".equals(type)) {
            visitCallExpression(node, ctx);
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            if (child != null) {
                walkNode(child, parentSymbol, ctx);
            }
        }
    }

    private void visitImportDeclaration(TSNode node, WalkContext ctx) {
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            if (child == null) {
                continue;
            }
            String childType = child.getType();
            if ("import_spec".equals(childType)) {
                visitImportSpec(child, ctx);
            } else if ("import_spec_list".equals(childType)) {
                int specCount = child.getChildCount();
                for (int j = 0; j < specCount; j++) {
                    TSNode spec = child.getChild(j);
                    if (spec != null && "import_spec".equals(spec.getType())) {
                        visitImportSpec(spec, ctx);
                    }
                }
            }
        }
    }

    private void visitImportSpec(TSNode node, WalkContext ctx) {
        TSNode pathNode = firstChildOfType(node, "interpreted_string_literal");
        if (pathNode == null) {
            return;
        }
        String path = stripQuotes(nodeText(pathNode, ctx.source));
        if (path.isEmpty()) {
            return;
        }

        TSNode nameNode = firstChildOfType(node, "package_identifier");
        String alias = null;
        if (nameNode != null) {
            String nameText = nodeText(nameNode, ctx.source);
            if (!"_".equals(nameText) && !".".equals(nameText)) {
                alias = nameText;
            }
        }

        CodeSymbol symbol = new CodeSymbol();
        symbol.setId(newId());
        symbol.setKind(CodeSymbolKind.IMPORT);
        symbol.setName(path);
        symbol.setQualifiedName(ctx.qn(path));
        symbol.setLine(node.getStartPoint().getRow() + 1);
        symbol.setColumn((int) node.getStartPoint().getColumn());
        if (alias != null) {
            symbol.setDocumentation("import alias: " + alias);
        }
        ctx.result.getSymbols().add(symbol);
        if (!ctx.result.getImports().contains(path)) {
            ctx.result.getImports().add(path);
        }
    }

    private void visitFunctionDeclaration(TSNode node, WalkContext ctx) {
        TSNode nameNode = firstChildOfType(node, "identifier");
        if (nameNode == null) {
            return;
        }
        String name = nodeText(nameNode, ctx.source);

        CodeSymbol symbol = new CodeSymbol();
        symbol.setId(newId());
        symbol.setKind(CodeSymbolKind.FUNCTION);
        symbol.setName(name);
        symbol.setQualifiedName(ctx.qn(name));
        symbol.setAccessModifier(CodeAccessModifier.PACKAGE_PRIVATE);
        symbol.setLine(node.getStartPoint().getRow() + 1);
        symbol.setColumn((int) node.getStartPoint().getColumn());
        symbol.setEndLine(node.getEndPoint().getRow() + 1);
        symbol.setSignature(nodeText(node, ctx.source));
        ctx.result.getSymbols().add(symbol);

        walkChildren(node, symbol, ctx);
    }

    private void visitMethodDeclaration(TSNode node, WalkContext ctx) {
        TSNode nameNode = firstChildOfType(node, "field_identifier");
        if (nameNode == null) {
            return;
        }
        String name = nodeText(nameNode, ctx.source);
        TSNode receiverNode = node.getChildByFieldName("receiver");
        String receiverType = extractReceiverType(receiverNode, ctx.source);

        CodeSymbol symbol = new CodeSymbol();
        symbol.setId(newId());
        symbol.setKind(CodeSymbolKind.METHOD);
        symbol.setName(name);
        String ownerQn = receiverType != null ? ctx.typeQn(receiverType) : ctx.packageName;
        symbol.setQualifiedName((ownerQn == null || ownerQn.isEmpty() ? name : ownerQn + "." + name));
        symbol.setAccessModifier(CodeAccessModifier.PACKAGE_PRIVATE);
        symbol.setLine(node.getStartPoint().getRow() + 1);
        symbol.setColumn((int) node.getStartPoint().getColumn());
        symbol.setEndLine(node.getEndPoint().getRow() + 1);
        symbol.setSignature(nodeText(node, ctx.source));
        if (receiverType != null) {
            CodeSymbol owner = findSymbolByQualifiedName(ctx, ctx.typeQn(receiverType));
            if (owner != null) {
                symbol.setParentId(owner.getId());
            }
        }
        ctx.result.getSymbols().add(symbol);

        walkChildren(node, symbol, ctx);
    }

    private void visitTypeDeclaration(TSNode node, WalkContext ctx) {
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            if (child == null) {
                continue;
            }
            if ("type_spec".equals(child.getType())) {
                visitTypeSpec(child, ctx);
            } else if ("type_alias".equals(child.getType())) {
                visitTypeAlias(child, ctx);
            }
        }
    }

    private void visitTypeAlias(TSNode node, WalkContext ctx) {
        TSNode nameNode = firstChildOfType(node, "type_identifier");
        if (nameNode == null) {
            return;
        }
        String name = nodeText(nameNode, ctx.source);
        CodeSymbol symbol = new CodeSymbol();
        symbol.setId(newId());
        symbol.setKind(CodeSymbolKind.TYPE_ALIAS);
        symbol.setName(name);
        symbol.setQualifiedName(ctx.qn(name));
        symbol.setAccessModifier(CodeAccessModifier.PACKAGE_PRIVATE);
        symbol.setLine(node.getStartPoint().getRow() + 1);
        symbol.setColumn((int) node.getStartPoint().getColumn());
        symbol.setEndLine(node.getEndPoint().getRow() + 1);
        symbol.setSignature(nodeText(node, ctx.source));
        ctx.result.getSymbols().add(symbol);
    }

    private void visitTypeSpec(TSNode node, WalkContext ctx) {
        TSNode nameNode = firstChildOfType(node, "type_identifier");
        if (nameNode == null) {
            return;
        }
        String name = nodeText(nameNode, ctx.source);

        TSNode typeNode = null;
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            if (child != null && !child.isNull() && child.isNamed()
                    && !"type_identifier".equals(child.getType())) {
                typeNode = child;
                break;
            }
        }
        String typeShape = typeNode != null ? typeNode.getType() : "";

        CodeSymbolKind kind;
        if ("struct_type".equals(typeShape)) {
            kind = CodeSymbolKind.CLASS;
        } else if ("interface_type".equals(typeShape)) {
            kind = CodeSymbolKind.INTERFACE;
        } else {
            kind = CodeSymbolKind.TYPE_ALIAS;
        }

        CodeSymbol symbol = new CodeSymbol();
        symbol.setId(newId());
        symbol.setKind(kind);
        symbol.setName(name);
        symbol.setQualifiedName(ctx.qn(name));
        symbol.setAccessModifier(CodeAccessModifier.PACKAGE_PRIVATE);
        symbol.setLine(node.getStartPoint().getRow() + 1);
        symbol.setColumn((int) node.getStartPoint().getColumn());
        symbol.setEndLine(node.getEndPoint().getRow() + 1);
        symbol.setSignature(nodeText(node, ctx.source));
        ctx.result.getSymbols().add(symbol);

        CodeSymbol prevType = ctx.currentType;
        ctx.currentType = symbol;
        if ("struct_type".equals(typeShape) && typeNode != null) {
            visitStructType(typeNode, symbol, ctx);
        } else if ("interface_type".equals(typeShape) && typeNode != null) {
            visitInterfaceType(typeNode, symbol, ctx);
        }
        ctx.currentType = prevType;
    }

    private void visitStructType(TSNode structNode, CodeSymbol owner, WalkContext ctx) {
        int childCount = structNode.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = structNode.getChild(i);
            if (child != null && "field_declaration_list".equals(child.getType())) {
                visitFieldDeclarationList(child, owner, ctx);
            }
        }
    }

    private void visitFieldDeclarationList(TSNode listNode, CodeSymbol owner, WalkContext ctx) {
        int childCount = listNode.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = listNode.getChild(i);
            if (child != null && "field_declaration".equals(child.getType())) {
                visitFieldDeclaration(child, owner, ctx);
            }
        }
    }

    private void visitFieldDeclaration(TSNode node, CodeSymbol owner, WalkContext ctx) {
        TSNode nameNode = firstChildOfType(node, "field_identifier");
        if (nameNode != null) {
            // named field: record as FIELD symbol
            String name = nodeText(nameNode, ctx.source);
            CodeSymbol symbol = new CodeSymbol();
            symbol.setId(newId());
            symbol.setKind(CodeSymbolKind.FIELD);
            symbol.setName(name);
            symbol.setQualifiedName(owner.getQualifiedName() + "." + name);
            symbol.setParentId(owner.getId());
            symbol.setAccessModifier(CodeAccessModifier.PACKAGE_PRIVATE);
            symbol.setLine(node.getStartPoint().getRow() + 1);
            symbol.setColumn((int) node.getStartPoint().getColumn());
            ctx.result.getSymbols().add(symbol);
            return;
        }

        // no name: embedded field → inheritance edge
        TSNode typeNode = null;
        int fieldChildCount = node.getChildCount();
        for (int i = 0; i < fieldChildCount; i++) {
            TSNode child = node.getChild(i);
            if (child != null && !child.isNull() && child.isNamed()
                    && !"field_identifier".equals(child.getType())) {
                typeNode = child;
                break;
            }
        }
        if (typeNode == null) {
            return;
        }
        String typeText = nodeText(typeNode, ctx.source).trim();
        addInheritance(ctx, owner, typeText);
    }

    private void visitInterfaceType(TSNode interfaceNode, CodeSymbol owner, WalkContext ctx) {
        int childCount = interfaceNode.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = interfaceNode.getChild(i);
            if (child == null) {
                continue;
            }
            if ("type_elem".equals(child.getType())) {
                // embedded interface: interface_type > type_elem > type_identifier|qualified_type
                TSNode embedded = firstNamedChild(child);
                if (embedded != null) {
                    String typeText = nodeText(embedded, ctx.source).trim();
                    if ("interface_type".equals(embedded.getType())) {
                        continue; // inline interface literal, not an embed
                    }
                    addInheritance(ctx, owner, typeText);
                }
            } else if ("method_elem".equals(child.getType())) {
                TSNode nameNode = firstChildOfType(child, "field_identifier");
                if (nameNode != null) {
                    String name = nodeText(nameNode, ctx.source);
                    CodeSymbol symbol = new CodeSymbol();
                    symbol.setId(newId());
                    symbol.setKind(CodeSymbolKind.METHOD);
                    symbol.setName(name);
                    symbol.setQualifiedName(owner.getQualifiedName() + "." + name);
                    symbol.setParentId(owner.getId());
                    symbol.setLine(child.getStartPoint().getRow() + 1);
                    symbol.setColumn((int) child.getStartPoint().getColumn());
                    ctx.result.getSymbols().add(symbol);
                }
            }
        }
    }

    private void visitConstDeclaration(TSNode node, WalkContext ctx) {
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            if (child != null && "const_spec".equals(child.getType())) {
                TSNode nameNode = firstChildOfType(child, "identifier");
                if (nameNode != null) {
                    String name = nodeText(nameNode, ctx.source);
                    CodeSymbol symbol = new CodeSymbol();
                    symbol.setId(newId());
                    symbol.setKind(CodeSymbolKind.CONSTANT);
                    symbol.setName(name);
                    symbol.setQualifiedName(ctx.qn(name));
                    symbol.setLine(child.getStartPoint().getRow() + 1);
                    symbol.setColumn((int) child.getStartPoint().getColumn());
                    ctx.result.getSymbols().add(symbol);
                }
            }
        }
    }

    private void visitCallExpression(TSNode node, WalkContext ctx) {
        TSNode fnNode = node.getChildByFieldName("function");
        if (fnNode == null || fnNode.isNull()) {
            return;
        }
        String fnText = nodeText(fnNode, ctx.source).trim();
        if (fnText.isEmpty()) {
            return;
        }

        CodeMethodCall call = new CodeMethodCall();
        call.setId(newId());
        call.setMethodName(fnText.substring(fnText.lastIndexOf('.') + 1));
        call.setCalleeQualifiedName(resolveCallQualifiedName(fnText, ctx));
        call.setCallType("FUNCTION_CALL");
        call.setLine(node.getStartPoint().getRow() + 1);
        call.setColumn((int) node.getStartPoint().getColumn());
        call.setConfidence(EdgeConfidence.EXTRACTED);
        call.setProvenance(EdgeProvenance.AST_EXTRACTION);
        if (ctx.currentType != null) {
            call.setCallerId(ctx.currentType.getId());
        }
        ctx.result.getCalls().add(call);
    }

    private String resolveCallQualifiedName(String fnText, WalkContext ctx) {
        // same-file candidate: bare identifier matching a known symbol qn
        if (fnText.indexOf('.') < 0) {
            for (CodeSymbol symbol : ctx.result.getSymbols()) {
                if (symbol.getName().equals(fnText)
                        && (symbol.getKind() == CodeSymbolKind.FUNCTION
                                || symbol.getKind() == CodeSymbolKind.METHOD)) {
                    return symbol.getQualifiedName();
                }
            }
            return fnText;
        }
        // selector: pkg.Func or receiver.Method — keep the text as the qn candidate
        int dot = fnText.lastIndexOf('.');
        String head = fnText.substring(0, dot);
        for (CodeSymbol symbol : ctx.result.getSymbols()) {
            if (symbol.getKind() == CodeSymbolKind.CLASS
                    || symbol.getKind() == CodeSymbolKind.INTERFACE
                    || symbol.getKind() == CodeSymbolKind.TYPE_ALIAS) {
                if (symbol.getName().equals(head)
                        || symbol.getQualifiedName().equals(head)
                        || symbol.getQualifiedName().endsWith("." + head)) {
                    return symbol.getQualifiedName() + "." + fnText.substring(dot + 1);
                }
            }
        }
        return fnText;
    }

    private void addInheritance(WalkContext ctx, CodeSymbol owner, String typeText) {
        if (typeText.isEmpty() || typeText.startsWith("*") || typeText.startsWith("...")
                || typeText.contains(" ") || typeText.contains("(")) {
            return;
        }
        CodeInheritance inheritance = new CodeInheritance();
        inheritance.setId(newId());
        inheritance.setSubTypeId(owner.getId());
        inheritance.setSuperTypeQualifiedName(ctx.typeQn(typeText));
        inheritance.setRelationType(CodeRelationType.EXTENDS);
        inheritance.setProvenance(EdgeProvenance.AST_EXTRACTION);
        ctx.result.getInheritances().add(inheritance);
    }

    // ---- helpers ----

    private CodeSymbol findSymbolByQualifiedName(WalkContext ctx, String qn) {
        for (CodeSymbol symbol : ctx.result.getSymbols()) {
            if (symbol.getQualifiedName().equals(qn)) {
                return symbol;
            }
        }
        return null;
    }

    private TSNode firstNamedChild(TSNode node) {
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            if (child != null && !child.isNull() && child.isNamed()) {
                return child;
            }
        }
        return null;
    }

    private String extractReceiverType(TSNode receiverNode, String source) {
        if (receiverNode == null || receiverNode.isNull()) {
            return null;
        }
        String text = nodeText(receiverNode, source);
        // receiver forms: (x *T) / (x T) / (*T) / (T)
        String t = text.trim();
        if (t.startsWith("(")) {
            t = t.substring(1, t.lastIndexOf(')') > 0 ? t.lastIndexOf(')') : t.length()).trim();
        }
        if (t.startsWith("*")) {
            t = t.substring(1).trim();
        }
        int space = t.lastIndexOf(' ');
        if (space >= 0) {
            t = t.substring(space + 1).trim();
        }
        if (t.startsWith("*")) {
            t = t.substring(1).trim();
        }
        return t.isEmpty() ? null : t;
    }

    private String findPackageName(TSNode root, String source) {
        int childCount = root.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = root.getChild(i);
            if (child != null && "package_clause".equals(child.getType())) {
                TSNode nameNode = firstChildOfType(child, "package_identifier");
                if (nameNode != null) {
                    return nodeText(nameNode, source);
                }
            }
        }
        return null;
    }

    private static TSNode firstChildOfType(TSNode node, String type) {
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            if (child != null && !child.isNull() && type.equals(child.getType())) {
                return child;
            }
        }
        return null;
    }

    private void walkChildren(TSNode node, CodeSymbol parentSymbol, WalkContext ctx) {
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            if (child != null) {
                walkNode(child, parentSymbol, ctx);
            }
        }
    }

    private static String stripQuotes(String s) {
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            return s.substring(1, s.length() - 1);
        }
        if (s.length() >= 3 && s.charAt(0) == '`' && s.charAt(s.length() - 1) == '`') {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static String nodeText(TSNode node, String source) {
        int startByte = node.getStartByte();
        int endByte = node.getEndByte();
        if (startByte >= endByte) {
            return "";
        }
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        if (endByte > bytes.length) {
            endByte = bytes.length;
        }
        return new String(bytes, startByte, endByte - startByte, StandardCharsets.UTF_8);
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }
}
